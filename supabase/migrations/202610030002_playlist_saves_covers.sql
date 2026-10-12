-- Stable saves, authoritative counts, and private playlist covers.
BEGIN;
CREATE OR REPLACE FUNCTION public.gratify_replace_cloud_playlist_v2(
    p_sync_id text, p_title text, p_thumbnail_url text, p_is_public boolean, p_tracks jsonb
) RETURNS TABLE(playlist_id text)
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_owner public.cloud_playlists.user_id%TYPE:=auth.uid(); v_id public.cloud_playlists.id%TYPE; v_public boolean; v_tracks jsonb;
BEGIN
    IF v_owner IS NULL THEN RAISE EXCEPTION 'Authentication required' USING ERRCODE='42501'; END IF;
    IF p_sync_id IS NULL OR length(p_sync_id)>128 OR length(p_sync_id)<16
       OR p_title IS NULL OR length(trim(p_title))=0 OR length(p_title)>512
       OR p_tracks IS NULL OR jsonb_typeof(p_tracks)<>'array' OR jsonb_array_length(p_tracks)>10000
    THEN RAISE EXCEPTION 'Invalid playlist'; END IF;
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_owner::text||':playlist:'||p_sync_id,0));
    IF EXISTS(SELECT 1 FROM public.playlist_sync_tombstones WHERE user_id=v_owner::text AND client_sync_id=p_sync_id)
    THEN RAISE EXCEPTION 'Playlist was deleted'; END IF;
    SELECT cp.id,cp.is_public INTO v_id,v_public FROM public.cloud_playlists cp
    WHERE cp.user_id=v_owner AND cp.client_sync_id=p_sync_id FOR UPDATE;
    v_public:=coalesce(p_is_public,v_public,false);
    IF v_id IS NULL THEN
        INSERT INTO public.cloud_playlists(user_id,client_sync_id,title,thumbnail_url,is_public)
        VALUES(v_owner,p_sync_id,p_title,p_thumbnail_url,v_public) RETURNING id INTO v_id;
    ELSE
        UPDATE public.cloud_playlists SET title=p_title,thumbnail_url=p_thumbnail_url,is_public=v_public
        WHERE id=v_id AND user_id=v_owner;
    END IF;
    DELETE FROM public.cloud_playlist_items WHERE cloud_playlist_items.playlist_id=v_id;
    INSERT INTO public.cloud_playlist_items(playlist_id,video_id,title,artist,duration,thumbnail_url,position)
    SELECT v_id,item->>'video_id',item->>'title',item->>'artist',(item->>'duration')::integer,
        item->>'thumbnail_url',(ordinality-1)::integer
    FROM jsonb_array_elements(p_tracks) WITH ORDINALITY AS tracks(item,ordinality);
    IF v_public THEN
        SELECT coalesce(jsonb_agg(jsonb_build_object('video_id',item->>'video_id','title',item->>'title',
            'artists',item->>'artist','duration_seconds',(item->>'duration')::integer) ORDER BY ordinality),'[]'::jsonb)
        INTO v_tracks FROM jsonb_array_elements(p_tracks) WITH ORDINALITY AS tracks(item,ordinality);
        PERFORM public.gratify_replace_shared_playlist_v2(p_sync_id,p_title,p_thumbnail_url,
            (SELECT display_name FROM public.profiles WHERE id::text=v_owner::text),v_tracks);
    END IF;
    -- Private publication retains the stable shared identity and saves behind RLS.
    RETURN QUERY SELECT v_id::text;
END $$;

CREATE OR REPLACE FUNCTION public.sync_shared_playlist_add_count() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$ BEGIN
    IF TG_OP='INSERT' THEN
        UPDATE public.shared_playlists SET add_count=add_count+1
        WHERE id=NEW.playlist_id AND user_id::text<>NEW.user_id::text;
    ELSE
        UPDATE public.shared_playlists SET add_count=greatest(add_count-1,0)
        WHERE id=OLD.playlist_id AND user_id::text<>OLD.user_id::text;
    END IF;
    RETURN NULL;
END $$;
REVOKE ALL ON FUNCTION public.sync_shared_playlist_add_count() FROM PUBLIC,anon,authenticated;
UPDATE public.shared_playlists sp SET add_count=(SELECT count(*) FROM public.shared_playlist_saves s
    WHERE s.playlist_id=sp.id AND s.user_id::text<>sp.user_id::text);

-- Only the authenticated identity can save a published playlist. Lock one row
-- so duplicate/repeated requests and other users' saves cannot corrupt counts.
CREATE OR REPLACE FUNCTION public.gratify_set_playlist_saved(p_playlist_id text,p_saved boolean,p_expected_owner text) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_owner text:=auth.uid()::text; v_playlist public.shared_playlists%ROWTYPE;
BEGIN
    IF v_owner IS NULL THEN RAISE EXCEPTION 'Authentication required' USING ERRCODE='42501'; END IF;
    IF p_expected_owner IS DISTINCT FROM v_owner THEN RAISE EXCEPTION 'Account ownership changed' USING ERRCODE='42501'; END IF;
    IF p_saved IS NULL THEN RAISE EXCEPTION 'Save state required'; END IF;
    SELECT * INTO v_playlist FROM public.shared_playlists WHERE id::text=p_playlist_id FOR UPDATE;
    IF p_saved THEN
        IF v_playlist.id IS NULL OR v_playlist.user_id::text=v_owner OR NOT EXISTS(
            SELECT 1 FROM public.cloud_playlists cp WHERE cp.user_id=v_playlist.user_id
            AND cp.client_sync_id=v_playlist.client_sync_id AND cp.is_public IS TRUE)
        THEN RAISE EXCEPTION 'Public playlist unavailable' USING ERRCODE='42501'; END IF;
        INSERT INTO public.shared_playlist_saves(playlist_id,user_id) VALUES(v_playlist.id,v_owner)
        ON CONFLICT(playlist_id,user_id) DO NOTHING;
    ELSE
        -- A user can remove their save even after the source becomes private.
        DELETE FROM public.shared_playlist_saves WHERE playlist_id::text=p_playlist_id AND user_id::text=v_owner;
    END IF;
    RETURN (SELECT jsonb_build_object('playlist_id',p_playlist_id,'saved',p_saved,'add_count',coalesce(
        (SELECT add_count FROM public.shared_playlists WHERE id::text=p_playlist_id),0)));
END $$;
REVOKE ALL ON FUNCTION public.gratify_set_playlist_saved(text,boolean,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.gratify_set_playlist_saved(text,boolean,text) TO authenticated;
REVOKE UPDATE ON public.shared_playlist_saves FROM PUBLIC,anon,authenticated;
DO $$ DECLARE v_columns text; BEGIN
    SELECT string_agg(quote_ident(column_name),',') INTO v_columns FROM information_schema.columns
    WHERE table_schema='public' AND table_name='shared_playlist_saves';
    EXECUTE format('REVOKE UPDATE (%s) ON public.shared_playlist_saves FROM PUBLIC,anon,authenticated',v_columns);
END $$;
-- Older APKs still use INSERT ... ON CONFLICT DO NOTHING. Keep this path but
-- enforce the same public/non-owner checks instead of breaking those clients.
DO $$ DECLARE v_policy record; BEGIN
    FOR v_policy IN SELECT policyname FROM pg_policies WHERE schemaname='public'
        AND tablename='shared_playlist_saves' AND cmd IN ('INSERT','ALL') LOOP
        EXECUTE format('DROP POLICY %I ON public.shared_playlist_saves',v_policy.policyname);
    END LOOP;
END $$;
CREATE POLICY shared_playlist_saves_insert_public ON public.shared_playlist_saves FOR INSERT TO authenticated
WITH CHECK(user_id::text=auth.uid()::text AND EXISTS(
    SELECT 1 FROM public.shared_playlists sp JOIN public.cloud_playlists cp
        ON cp.user_id=sp.user_id AND cp.client_sync_id=sp.client_sync_id
    WHERE sp.id=shared_playlist_saves.playlist_id AND sp.user_id::text<>auth.uid()::text AND cp.is_public IS TRUE));
GRANT INSERT ON public.shared_playlist_saves TO authenticated;
CREATE OR REPLACE FUNCTION public.gratify_get_saved_playlists() RETURNS jsonb
LANGUAGE sql STABLE SECURITY INVOKER SET search_path='' AS $$
    SELECT coalesce(jsonb_agg(to_jsonb(sp) ORDER BY saves.created_at DESC),'[]'::jsonb)
    FROM public.shared_playlist_saves saves JOIN public.shared_playlists sp ON sp.id=saves.playlist_id
    WHERE saves.user_id::text=auth.uid()::text;
$$;
REVOKE ALL ON FUNCTION public.gratify_get_saved_playlists() FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.gratify_get_saved_playlists() TO authenticated;
-- Deletion remains allowed with owner RLS, for the durable offline removal queue.

CREATE OR REPLACE FUNCTION public.gratify_get_profile_playlists(p_user_id text) RETURNS jsonb
LANGUAGE sql STABLE SECURITY INVOKER SET search_path='' AS $$
    SELECT coalesce(jsonb_agg(to_jsonb(cp)||jsonb_build_object('shared_playlist_id',sp.id,'add_count',coalesce(sp.add_count,0))
        ORDER BY cp.title,cp.id),'[]'::jsonb)
    FROM public.cloud_playlists cp LEFT JOIN public.shared_playlists sp
        ON sp.user_id=cp.user_id AND sp.client_sync_id=cp.client_sync_id
    WHERE cp.user_id::text=p_user_id AND cp.is_profile_pinned IS TRUE AND cp.is_public IS TRUE
      AND (p_user_id=auth.uid()::text OR coalesce((SELECT show_playlists FROM public.profile_privacy
           WHERE user_id::text=p_user_id),false));
$$;

INSERT INTO storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
VALUES('playlist-covers','playlist-covers',false,5242880,ARRAY['image/jpeg'])
ON CONFLICT(id) DO UPDATE SET public=false,file_size_limit=5242880,allowed_mime_types=ARRAY['image/jpeg'];
CREATE OR REPLACE FUNCTION public.gratify_can_read_playlist_cover(p_name text) RETURNS boolean
LANGUAGE sql STABLE SECURITY INVOKER SET search_path='' AS $$
    SELECT split_part(p_name,'/',1)=auth.uid()::text OR EXISTS(
        SELECT 1 FROM public.cloud_playlists cp WHERE cp.user_id::text=split_part(p_name,'/',1)
        AND cp.client_sync_id=split_part(p_name,'/',2) AND cp.is_public IS TRUE
        AND split_part(cp.thumbnail_url,'/storage/v1/object/public/playlist-covers/',2)=p_name);
$$;
REVOKE ALL ON FUNCTION public.gratify_can_read_playlist_cover(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.gratify_can_read_playlist_cover(text) TO anon,authenticated;
DROP POLICY IF EXISTS gratify_playlist_cover_read ON storage.objects;
CREATE POLICY gratify_playlist_cover_read ON storage.objects FOR SELECT TO anon,authenticated
USING(bucket_id='playlist-covers' AND public.gratify_can_read_playlist_cover(name));
DROP POLICY IF EXISTS gratify_playlist_cover_insert ON storage.objects;
CREATE POLICY gratify_playlist_cover_insert ON storage.objects FOR INSERT TO authenticated
WITH CHECK(bucket_id='playlist-covers' AND split_part(name,'/',1)=auth.uid()::text
    AND length(split_part(name,'/',2)) BETWEEN 16 AND 128 AND split_part(name,'/',3) LIKE '%.jpg'
    AND split_part(name,'/',4)='' AND name NOT LIKE '%..%');
DROP POLICY IF EXISTS gratify_playlist_cover_delete ON storage.objects;
CREATE POLICY gratify_playlist_cover_delete ON storage.objects FOR DELETE TO authenticated
USING(bucket_id='playlist-covers' AND split_part(name,'/',1)=auth.uid()::text);

CREATE OR REPLACE FUNCTION public.gratify_hide_owned_playlist_v2(p_sync_id text) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_owner text:=auth.uid()::text;
BEGIN
    IF v_owner IS NULL THEN RAISE EXCEPTION 'Authentication required' USING ERRCODE='42501'; END IF;
    IF p_sync_id IS NULL OR length(p_sync_id)<16 OR length(p_sync_id)>128 THEN RAISE EXCEPTION 'Invalid identity'; END IF;
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_owner::text||':playlist:'||p_sync_id,0));
    UPDATE public.cloud_playlists SET is_public=false WHERE user_id::text=v_owner::text AND client_sync_id=p_sync_id;
END $$;

NOTIFY pgrst,'reload schema';
COMMIT;
