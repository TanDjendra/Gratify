-- Device-local numeric IDs and titles are not safe cross-device identities.
-- Keep every historical record. Ambiguous legacy copies require explicit review.
BEGIN;
ALTER TABLE public.cloud_playlists ADD COLUMN IF NOT EXISTS client_sync_id text;
ALTER TABLE public.shared_playlists ADD COLUMN IF NOT EXISTS client_sync_id text;
UPDATE public.cloud_playlists SET client_sync_id=id WHERE client_sync_id IS NULL;
UPDATE public.shared_playlists SET client_sync_id=id WHERE client_sync_id IS NULL;
ALTER TABLE public.cloud_playlists ALTER COLUMN client_sync_id SET DEFAULT gen_random_uuid()::text;
ALTER TABLE public.shared_playlists ALTER COLUMN client_sync_id SET DEFAULT gen_random_uuid()::text;
ALTER TABLE public.cloud_playlists ALTER COLUMN client_sync_id SET NOT NULL;
ALTER TABLE public.shared_playlists ALTER COLUMN client_sync_id SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS cloud_playlist_owner_sync ON public.cloud_playlists(user_id,client_sync_id);
CREATE UNIQUE INDEX IF NOT EXISTS shared_playlist_owner_sync ON public.shared_playlists(user_id,client_sync_id);
GRANT INSERT (client_sync_id) ON public.shared_playlists TO authenticated;

CREATE TABLE IF NOT EXISTS public.playlist_sync_tombstones (
    user_id text NOT NULL, client_sync_id text NOT NULL,
    PRIMARY KEY(user_id,client_sync_id)
);
ALTER TABLE public.playlist_sync_tombstones ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS playlist_tombstone_read ON public.playlist_sync_tombstones;
DROP POLICY IF EXISTS playlist_tombstone_insert ON public.playlist_sync_tombstones;
CREATE POLICY playlist_tombstone_read ON public.playlist_sync_tombstones FOR SELECT TO authenticated USING(user_id=auth.uid()::text);
CREATE POLICY playlist_tombstone_insert ON public.playlist_sync_tombstones FOR INSERT TO authenticated WITH CHECK(user_id=auth.uid()::text);
REVOKE ALL ON public.playlist_sync_tombstones FROM PUBLIC,anon,authenticated;
GRANT SELECT,INSERT ON public.playlist_sync_tombstones TO authenticated;

CREATE OR REPLACE FUNCTION public.gratify_replace_shared_playlist_v2(
    p_sync_id text, p_title text, p_thumbnail_url text, p_creator_name text, p_tracks jsonb
) RETURNS TABLE(playlist_id text)
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_owner text:=auth.uid()::text; v_id text;
BEGIN
    IF v_owner IS NULL THEN RAISE EXCEPTION 'Authentication required' USING ERRCODE='42501'; END IF;
    IF p_sync_id IS NULL OR length(p_sync_id)>128 OR length(p_sync_id)<16
       OR p_title IS NULL OR length(trim(p_title))=0 OR length(p_title)>512
       OR p_tracks IS NULL OR jsonb_typeof(p_tracks)<>'array' OR jsonb_array_length(p_tracks)>10000
    THEN RAISE EXCEPTION 'Invalid playlist'; END IF;
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_owner||':playlist:'||p_sync_id,0));
    IF EXISTS(SELECT 1 FROM public.playlist_sync_tombstones WHERE user_id=v_owner AND client_sync_id=p_sync_id)
    THEN RAISE EXCEPTION 'Playlist was deleted'; END IF;
    SELECT sp.id INTO v_id FROM public.shared_playlists sp WHERE sp.user_id=v_owner AND sp.client_sync_id=p_sync_id FOR UPDATE;
    IF v_id IS NULL THEN
        INSERT INTO public.shared_playlists(user_id,client_sync_id,title,thumbnail_url,creator_name)
        VALUES(v_owner,p_sync_id,p_title,p_thumbnail_url,p_creator_name) RETURNING id INTO v_id;
    ELSE
        UPDATE public.shared_playlists SET title=p_title,thumbnail_url=p_thumbnail_url,creator_name=p_creator_name
        WHERE id=v_id AND user_id=v_owner;
    END IF;
    DELETE FROM public.shared_playlist_tracks WHERE shared_playlist_tracks.playlist_id=v_id;
    INSERT INTO public.shared_playlist_tracks(playlist_id,video_id,title,artists,duration_seconds,position)
    SELECT v_id,item->>'video_id',item->>'title',item->>'artists',(item->>'duration_seconds')::integer,(ordinality-1)::integer
    FROM jsonb_array_elements(p_tracks) WITH ORDINALITY AS tracks(item,ordinality);
    RETURN QUERY SELECT v_id;
END $$;

CREATE OR REPLACE FUNCTION public.gratify_replace_cloud_playlist_v2(
    p_sync_id text, p_title text, p_thumbnail_url text, p_is_public boolean, p_tracks jsonb
) RETURNS TABLE(playlist_id text)
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_owner text:=auth.uid()::text; v_id text; v_public boolean; v_tracks jsonb;
BEGIN
    IF v_owner IS NULL THEN RAISE EXCEPTION 'Authentication required' USING ERRCODE='42501'; END IF;
    IF p_sync_id IS NULL OR length(p_sync_id)>128 OR length(p_sync_id)<16
       OR p_title IS NULL OR length(trim(p_title))=0 OR length(p_title)>512
       OR p_tracks IS NULL OR jsonb_typeof(p_tracks)<>'array' OR jsonb_array_length(p_tracks)>10000
    THEN RAISE EXCEPTION 'Invalid playlist'; END IF;
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_owner||':playlist:'||p_sync_id,0));
    IF EXISTS(SELECT 1 FROM public.playlist_sync_tombstones WHERE user_id=v_owner AND client_sync_id=p_sync_id)
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
            (SELECT display_name FROM public.profiles WHERE id::text=v_owner),v_tracks);
    ELSE
        DELETE FROM public.shared_playlists WHERE user_id=v_owner AND client_sync_id=p_sync_id;
    END IF;
    RETURN QUERY SELECT v_id;
END $$;

CREATE OR REPLACE FUNCTION public.gratify_hide_owned_playlist_v2(p_sync_id text) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_owner text:=auth.uid()::text;
BEGIN
    IF v_owner IS NULL THEN RAISE EXCEPTION 'Authentication required' USING ERRCODE='42501'; END IF;
    IF p_sync_id IS NULL OR length(p_sync_id)<16 OR length(p_sync_id)>128 THEN RAISE EXCEPTION 'Invalid identity'; END IF;
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_owner||':playlist:'||p_sync_id,0));
    UPDATE public.cloud_playlists SET is_public=false WHERE user_id=v_owner AND client_sync_id=p_sync_id;
    DELETE FROM public.shared_playlists WHERE user_id=v_owner AND client_sync_id=p_sync_id;
END $$;

CREATE OR REPLACE FUNCTION public.gratify_delete_owned_playlist_v2(p_sync_id text) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_owner text:=auth.uid()::text;
BEGIN
    IF v_owner IS NULL THEN RAISE EXCEPTION 'Authentication required' USING ERRCODE='42501'; END IF;
    IF p_sync_id IS NULL OR length(p_sync_id)<16 OR length(p_sync_id)>128 THEN RAISE EXCEPTION 'Invalid identity'; END IF;
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_owner||':playlist:'||p_sync_id,0));
    INSERT INTO public.playlist_sync_tombstones(user_id,client_sync_id) VALUES(v_owner,p_sync_id) ON CONFLICT DO NOTHING;
    DELETE FROM public.cloud_playlists WHERE user_id=v_owner AND client_sync_id=p_sync_id;
    DELETE FROM public.shared_playlists WHERE user_id=v_owner AND client_sync_id=p_sync_id;
END $$;

-- Fail closed for old clients which still use device-local IDs for destructive operations.
-- Return a single JSON value so PostgREST's default row limit cannot truncate
-- playlists of more than 1,000 tracks. One SQL statement also preserves ordering.
CREATE OR REPLACE FUNCTION public.gratify_get_cloud_playlist_items(p_playlist_id text) RETURNS jsonb
LANGUAGE sql STABLE SECURITY INVOKER SET search_path='' AS $$
    SELECT coalesce(jsonb_agg(to_jsonb(item) ORDER BY item.position,item.id),'[]'::jsonb)
    FROM public.cloud_playlist_items item WHERE item.playlist_id=p_playlist_id;
$$;
CREATE OR REPLACE FUNCTION public.gratify_get_shared_playlist_tracks(p_playlist_id text) RETURNS jsonb
LANGUAGE sql STABLE SECURITY INVOKER SET search_path='' AS $$
    SELECT coalesce(jsonb_agg(to_jsonb(track) ORDER BY track.position,track.id),'[]'::jsonb)
    FROM public.shared_playlist_tracks track WHERE track.playlist_id=p_playlist_id;
$$;
REVOKE ALL ON FUNCTION public.gratify_get_cloud_playlist_items(text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.gratify_get_shared_playlist_tracks(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.gratify_get_cloud_playlist_items(text) TO anon,authenticated;
GRANT EXECUTE ON FUNCTION public.gratify_get_shared_playlist_tracks(text) TO anon,authenticated;

REVOKE ALL ON FUNCTION public.gratify_replace_shared_playlist(text,bigint,text,text,text,jsonb) FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.gratify_replace_cloud_playlist(bigint,text,text,boolean,jsonb) FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.gratify_delete_owned_playlist(bigint) FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.gratify_replace_shared_playlist_v2(text,text,text,text,jsonb) FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.gratify_replace_cloud_playlist_v2(text,text,text,boolean,jsonb) FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.gratify_hide_owned_playlist_v2(text) FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.gratify_delete_owned_playlist_v2(text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.gratify_replace_shared_playlist_v2(text,text,text,text,jsonb) TO authenticated;
GRANT EXECUTE ON FUNCTION public.gratify_replace_cloud_playlist_v2(text,text,text,boolean,jsonb) TO authenticated;
GRANT EXECUTE ON FUNCTION public.gratify_hide_owned_playlist_v2(text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.gratify_delete_owned_playlist_v2(text) TO authenticated;

-- Make transaction RPCs the only client write path. Old direct REST writes could
-- otherwise still delete multiple same-title playlists even after the old RPCs are retired.
DO $$ DECLARE v_table text; v_columns text; BEGIN
    FOREACH v_table IN ARRAY ARRAY['cloud_playlists','cloud_playlist_items','shared_playlists','shared_playlist_tracks'] LOOP
        SELECT string_agg(quote_ident(column_name),',') INTO v_columns
        FROM information_schema.columns WHERE table_schema='public' AND table_name=v_table;
        EXECUTE format('REVOKE INSERT (%s), UPDATE (%s), REFERENCES (%s) ON public.%I FROM PUBLIC,anon,authenticated',v_columns,v_columns,v_columns,v_table);
        EXECUTE format('REVOKE ALL ON public.%I FROM PUBLIC,anon,authenticated',v_table);
        EXECUTE format('GRANT SELECT ON public.%I TO anon,authenticated',v_table);
    END LOOP;
END $$;
COMMIT;
