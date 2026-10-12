-- Profile curation preserves publication state. Only an owner sees a pinned private playlist.
BEGIN;
DROP TRIGGER IF EXISTS gratify_clear_private_profile_pin ON public.cloud_playlists;

CREATE OR REPLACE FUNCTION public.gratify_set_playlist_profile_pin(p_sync_id text,p_pinned boolean)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_owner text:=auth.uid()::text;
BEGIN
    IF v_owner IS NULL THEN RAISE EXCEPTION 'Authentication required' USING ERRCODE='42501'; END IF;
    IF p_sync_id IS NULL OR length(p_sync_id)<16 OR length(p_sync_id)>128 OR p_pinned IS NULL
    THEN RAISE EXCEPTION 'Invalid playlist pin'; END IF;
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_owner||':playlist:'||p_sync_id,0));
    PERFORM 1 FROM public.cloud_playlists cp
    WHERE cp.user_id::text=v_owner AND cp.client_sync_id=p_sync_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Owned playlist not found' USING ERRCODE='42501'; END IF;
    UPDATE public.cloud_playlists SET is_profile_pinned=p_pinned
    WHERE user_id::text=v_owner AND client_sync_id=p_sync_id;
END $$;

CREATE OR REPLACE FUNCTION public.gratify_get_profile_playlists(p_user_id text)
RETURNS jsonb LANGUAGE sql STABLE SECURITY INVOKER SET search_path='' AS $$
    SELECT coalesce(jsonb_agg(to_jsonb(cp)||jsonb_build_object('shared_playlist_id',sp.id,'add_count',coalesce(sp.add_count,0))
        ORDER BY cp.title,cp.id),'[]'::jsonb)
    FROM public.cloud_playlists cp LEFT JOIN public.shared_playlists sp
        ON sp.user_id=cp.user_id AND sp.client_sync_id=cp.client_sync_id
    WHERE cp.user_id::text=p_user_id AND cp.is_profile_pinned IS TRUE
      AND (p_user_id=auth.uid()::text OR (cp.is_public IS TRUE AND coalesce(
          (SELECT show_playlists FROM public.profile_privacy WHERE user_id::text=p_user_id),false)));
$$;
REVOKE ALL ON FUNCTION public.gratify_set_playlist_profile_pin(text,boolean) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.gratify_set_playlist_profile_pin(text,boolean) TO authenticated;
REVOKE ALL ON FUNCTION public.gratify_get_profile_playlists(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.gratify_get_profile_playlists(text) TO anon,authenticated;
NOTIFY pgrst,'reload schema';
COMMIT;
