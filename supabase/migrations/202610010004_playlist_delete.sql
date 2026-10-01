BEGIN;
CREATE OR REPLACE FUNCTION public.gratify_delete_owned_playlist(p_local_playlist_id bigint) RETURNS void
LANGUAGE plpgsql SECURITY INVOKER SET search_path = '' AS $$
DECLARE v_owner text := auth.uid()::text;
BEGIN
    IF v_owner IS NULL THEN RAISE EXCEPTION 'Authentication required' USING ERRCODE='42501'; END IF;
    IF p_local_playlist_id IS NULL OR p_local_playlist_id < 0 THEN RAISE EXCEPTION 'Invalid playlist'; END IF;
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_owner || ':cloud:' || p_local_playlist_id,0));
    DELETE FROM public.cloud_playlists WHERE user_id=v_owner AND local_playlist_id=p_local_playlist_id;
    DELETE FROM public.shared_playlists WHERE user_id=v_owner AND title LIKE '%|||' || p_local_playlist_id;
END $$;
REVOKE ALL ON FUNCTION public.gratify_delete_owned_playlist(bigint) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.gratify_delete_owned_playlist(bigint) TO authenticated;
COMMIT;
