-- Public discovery must exclude private playlists even when the caller owns them.
BEGIN;
CREATE OR REPLACE FUNCTION public.gratify_get_public_playlists()
RETURNS jsonb LANGUAGE sql STABLE SECURITY INVOKER SET search_path='' AS $$
    SELECT coalesce(jsonb_agg(to_jsonb(sp) || jsonb_build_object('is_public',true)
        ORDER BY sp.created_at DESC,sp.id),'[]'::jsonb)
    FROM public.shared_playlists sp
    JOIN public.cloud_playlists cp ON cp.user_id::text=sp.user_id::text
        AND cp.client_sync_id=sp.client_sync_id
    WHERE cp.is_public IS TRUE;
$$;
REVOKE ALL ON FUNCTION public.gratify_get_public_playlists() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.gratify_get_public_playlists() TO anon,authenticated;
NOTIFY pgrst,'reload schema';
COMMIT;
