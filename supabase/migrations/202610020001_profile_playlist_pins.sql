-- Separate playlist publication from profile curation, for both TEXT and UUID schemas.
BEGIN;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public'
                   AND table_name='cloud_playlists' AND column_name='is_profile_pinned') THEN
        ALTER TABLE public.cloud_playlists ADD COLUMN is_profile_pinned boolean NOT NULL DEFAULT false;
        -- Keep the user's previously displayed selections on the first upgrade only.
        UPDATE public.cloud_playlists SET is_profile_pinned=true WHERE is_public IS TRUE;
    END IF;
END $$;

-- A playlist that becomes private can never remain displayed, including writes by older clients.
CREATE OR REPLACE FUNCTION public.gratify_clear_private_profile_pin() RETURNS trigger
LANGUAGE plpgsql SET search_path='' AS $$ BEGIN
    IF NEW.is_public IS NOT TRUE THEN NEW.is_profile_pinned:=false; END IF;
    RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS gratify_clear_private_profile_pin ON public.cloud_playlists;
CREATE TRIGGER gratify_clear_private_profile_pin BEFORE INSERT OR UPDATE ON public.cloud_playlists
FOR EACH ROW EXECUTE FUNCTION public.gratify_clear_private_profile_pin();

CREATE OR REPLACE FUNCTION public.gratify_set_playlist_profile_pin(p_sync_id text,p_pinned boolean)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_owner text:=auth.uid()::text; v_public boolean;
BEGIN
    IF v_owner IS NULL THEN RAISE EXCEPTION 'Authentication required' USING ERRCODE='42501'; END IF;
    IF p_sync_id IS NULL OR length(p_sync_id)<16 OR length(p_sync_id)>128 OR p_pinned IS NULL
    THEN RAISE EXCEPTION 'Invalid playlist pin'; END IF;
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_owner||':playlist:'||p_sync_id,0));
    SELECT cp.is_public INTO v_public FROM public.cloud_playlists cp
    WHERE cp.user_id::text=v_owner AND cp.client_sync_id=p_sync_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Owned playlist not found' USING ERRCODE='42501'; END IF;
    IF p_pinned AND v_public IS NOT TRUE THEN RAISE EXCEPTION 'Publish playlist before displaying it'; END IF;
    UPDATE public.cloud_playlists SET is_profile_pinned=p_pinned
    WHERE user_id::text=v_owner AND client_sync_id=p_sync_id;
END $$;

-- Publishing and pinning succeed or roll back together, including the shared tracks.
CREATE OR REPLACE FUNCTION public.gratify_replace_cloud_playlist_v3(
    p_sync_id text,p_title text,p_thumbnail_url text,p_is_public boolean,p_tracks jsonb,p_profile_pinned boolean
) RETURNS TABLE(playlist_id text) LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_id text;
BEGIN
    SELECT saved.playlist_id INTO v_id FROM public.gratify_replace_cloud_playlist_v2(
        p_sync_id,p_title,p_thumbnail_url,p_is_public,p_tracks) saved;
    PERFORM public.gratify_set_playlist_profile_pin(p_sync_id,p_profile_pinned);
    RETURN QUERY SELECT v_id;
END $$;

-- Public links and discovery are independent of the profile's visibility setting.
-- Preserve ownership checks and require a matching published cloud identity for shared copies.
DROP POLICY IF EXISTS cloud_visible ON public.cloud_playlists;
CREATE POLICY cloud_visible ON public.cloud_playlists FOR SELECT USING (
    user_id::text=auth.uid()::text OR is_public IS TRUE
);
DROP POLICY IF EXISTS shared_visible ON public.shared_playlists;
CREATE POLICY shared_visible ON public.shared_playlists FOR SELECT USING (
    user_id::text=auth.uid()::text OR EXISTS (
        SELECT 1 FROM public.cloud_playlists cp WHERE cp.user_id::text=shared_playlists.user_id::text
        AND cp.client_sync_id=shared_playlists.client_sync_id AND cp.is_public IS TRUE
    )
);

CREATE OR REPLACE FUNCTION public.gratify_get_profile_playlists(p_user_id text) RETURNS jsonb
LANGUAGE sql STABLE SECURITY INVOKER SET search_path='' AS $$
    SELECT coalesce(jsonb_agg(to_jsonb(cp)||jsonb_build_object('shared_playlist_id',sp.id)
        ORDER BY cp.title,cp.id),'[]'::jsonb)
    FROM public.cloud_playlists cp LEFT JOIN public.shared_playlists sp
        ON sp.user_id::text=cp.user_id::text AND sp.client_sync_id=cp.client_sync_id
    WHERE cp.user_id::text=p_user_id AND cp.is_profile_pinned IS TRUE AND cp.is_public IS TRUE
      AND (p_user_id=auth.uid()::text OR coalesce((SELECT show_playlists FROM public.profile_privacy
           WHERE user_id::text=p_user_id),false));
$$;

REVOKE ALL ON FUNCTION public.gratify_clear_private_profile_pin() FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.gratify_set_playlist_profile_pin(text,boolean) FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.gratify_replace_cloud_playlist_v3(text,text,text,boolean,jsonb,boolean) FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.gratify_get_profile_playlists(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.gratify_set_playlist_profile_pin(text,boolean) TO authenticated;
GRANT EXECUTE ON FUNCTION public.gratify_replace_cloud_playlist_v3(text,text,text,boolean,jsonb,boolean) TO authenticated;
GRANT EXECUTE ON FUNCTION public.gratify_get_profile_playlists(text) TO anon,authenticated;
NOTIFY pgrst,'reload schema';
COMMIT;
