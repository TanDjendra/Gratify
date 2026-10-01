-- Apply after the existing Gratify schema and ownership RLS hotfixes.
-- Functions run with the caller's rights; auth.uid and RLS both enforce ownership.
BEGIN;

ALTER TABLE public.shared_playlist_tracks ADD COLUMN IF NOT EXISTS position integer NOT NULL DEFAULT 0;

CREATE OR REPLACE FUNCTION public.gratify_replace_shared_playlist(
    p_playlist_id text, p_local_playlist_id bigint, p_title text,
    p_thumbnail_url text, p_creator_name text, p_tracks jsonb
) RETURNS TABLE(playlist_id text)
LANGUAGE plpgsql SECURITY INVOKER SET search_path = '' AS $$
DECLARE v_id text; v_owner text := auth.uid()::text; v_title text;
BEGIN
    IF v_owner IS NULL THEN RAISE EXCEPTION 'Authentication required' USING ERRCODE='42501'; END IF;
    IF p_local_playlist_id IS NULL OR p_local_playlist_id < 0 OR p_title IS NULL OR p_tracks IS NULL
       OR length(trim(p_title)) = 0 OR length(p_title) > 512 OR jsonb_typeof(p_tracks) <> 'array'
       OR jsonb_array_length(p_tracks) > 10000 THEN RAISE EXCEPTION 'Invalid playlist'; END IF;
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_owner || ':shared:' || p_local_playlist_id, 0));
    v_title := p_title || '|||' || p_local_playlist_id;
    IF p_playlist_id IS NOT NULL THEN
        SELECT sp.id INTO v_id FROM public.shared_playlists sp
        WHERE sp.id=p_playlist_id AND sp.user_id=v_owner FOR UPDATE;
        IF v_id IS NULL THEN RAISE EXCEPTION 'Playlist not owned' USING ERRCODE='42501'; END IF;
    ELSE
        SELECT sp.id INTO v_id FROM public.shared_playlists sp
        WHERE sp.user_id=v_owner AND sp.title=v_title ORDER BY sp.created_at LIMIT 1 FOR UPDATE;
    END IF;
    IF v_id IS NULL THEN
        INSERT INTO public.shared_playlists(user_id,title,thumbnail_url,creator_name)
        VALUES(v_owner,v_title,p_thumbnail_url,p_creator_name) RETURNING id INTO v_id;
    ELSE
        UPDATE public.shared_playlists SET title=v_title,thumbnail_url=p_thumbnail_url,creator_name=p_creator_name WHERE id=v_id;
    END IF;
    DELETE FROM public.shared_playlist_tracks WHERE shared_playlist_tracks.playlist_id=v_id;
    INSERT INTO public.shared_playlist_tracks(playlist_id,video_id,title,artists,duration_seconds,position)
    SELECT v_id, item->>'video_id', item->>'title', item->>'artists',
           (item->>'duration_seconds')::integer, (ordinality-1)::integer
    FROM jsonb_array_elements(p_tracks) WITH ORDINALITY AS tracks(item,ordinality);
    RETURN QUERY SELECT v_id;
END $$;

CREATE OR REPLACE FUNCTION public.gratify_replace_cloud_playlist(
    p_local_playlist_id bigint, p_title text, p_thumbnail_url text, p_is_public boolean, p_tracks jsonb
) RETURNS TABLE(playlist_id text)
LANGUAGE plpgsql SECURITY INVOKER SET search_path = '' AS $$
DECLARE v_id text; v_owner text := auth.uid()::text; v_public boolean; v_shared text; v_shared_tracks jsonb;
BEGIN
    IF v_owner IS NULL THEN RAISE EXCEPTION 'Authentication required' USING ERRCODE='42501'; END IF;
    IF p_local_playlist_id IS NULL OR p_local_playlist_id < 0 OR p_title IS NULL OR p_tracks IS NULL
       OR length(trim(p_title)) = 0 OR length(p_title) > 512 OR jsonb_typeof(p_tracks) <> 'array'
       OR jsonb_array_length(p_tracks) > 10000 THEN RAISE EXCEPTION 'Invalid playlist'; END IF;
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_owner || ':cloud:' || p_local_playlist_id, 0));
    SELECT cp.id,cp.is_public INTO v_id,v_public FROM public.cloud_playlists cp
    WHERE cp.user_id=v_owner AND cp.local_playlist_id=p_local_playlist_id ORDER BY cp.created_at LIMIT 1 FOR UPDATE;
    v_public := coalesce(p_is_public,v_public,false);
    IF v_id IS NULL THEN
        INSERT INTO public.cloud_playlists(user_id,local_playlist_id,title,thumbnail_url,is_public)
        VALUES(v_owner,p_local_playlist_id,p_title,p_thumbnail_url,v_public) RETURNING id INTO v_id;
    ELSE
        UPDATE public.cloud_playlists SET title=p_title,thumbnail_url=p_thumbnail_url,is_public=v_public WHERE id=v_id;
    END IF;
    DELETE FROM public.cloud_playlist_items WHERE cloud_playlist_items.playlist_id=v_id;
    INSERT INTO public.cloud_playlist_items(playlist_id,video_id,title,artist,duration,thumbnail_url,position)
    SELECT v_id,item->>'video_id',item->>'title',item->>'artist',
           (item->>'duration')::integer,item->>'thumbnail_url',(ordinality-1)::integer
    FROM jsonb_array_elements(p_tracks) WITH ORDINALITY AS tracks(item,ordinality);
    SELECT sp.id INTO v_shared FROM public.shared_playlists sp
    WHERE sp.user_id=v_owner AND sp.title LIKE '%|||' || p_local_playlist_id ORDER BY sp.created_at LIMIT 1;
    IF v_public THEN
        SELECT coalesce(jsonb_agg(jsonb_build_object('video_id',item->>'video_id','title',item->>'title',
               'artists',item->>'artist','duration_seconds',(item->>'duration')::integer) ORDER BY ordinality),'[]'::jsonb)
        INTO v_shared_tracks FROM jsonb_array_elements(p_tracks) WITH ORDINALITY AS tracks(item,ordinality);
        PERFORM public.gratify_replace_shared_playlist(v_shared,p_local_playlist_id,p_title,p_thumbnail_url,
                (SELECT display_name FROM public.profiles WHERE id::text=v_owner),v_shared_tracks);
    ELSIF v_shared IS NOT NULL THEN
        DELETE FROM public.shared_playlists WHERE id=v_shared AND user_id=v_owner;
    END IF;
    RETURN QUERY SELECT v_id;
END $$;

REVOKE ALL ON FUNCTION public.gratify_replace_shared_playlist(text,bigint,text,text,text,jsonb) FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.gratify_replace_cloud_playlist(bigint,text,text,boolean,jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.gratify_replace_shared_playlist(text,bigint,text,text,text,jsonb) TO authenticated;
GRANT EXECUTE ON FUNCTION public.gratify_replace_cloud_playlist(bigint,text,text,boolean,jsonb) TO authenticated;

COMMIT;
