BEGIN;
-- Verify the deletion feature is deployed before the client removes its avatar.
CREATE OR REPLACE FUNCTION public.gratify_prepare_account_deletion() RETURNS void
LANGUAGE plpgsql SECURITY INVOKER SET search_path='' AS $$
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Authentication required' USING ERRCODE='42501'; END IF;
END $$;
REVOKE ALL ON FUNCTION public.gratify_prepare_account_deletion() FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.gratify_prepare_account_deletion() TO authenticated;
-- No caller-supplied ID: the function can remove only the authenticated account.
CREATE OR REPLACE FUNCTION public.gratify_delete_account() RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE v_owner uuid := auth.uid();
BEGIN
    IF v_owner IS NULL THEN RAISE EXCEPTION 'Authentication required' USING ERRCODE='42501'; END IF;
    DELETE FROM public.shared_playlist_saves WHERE user_id=v_owner::text;
    DELETE FROM public.shared_playlists WHERE user_id=v_owner::text;
    DELETE FROM public.cloud_playlists WHERE user_id=v_owner::text;
    DELETE FROM public.user_liked_songs WHERE user_id=v_owner::text;
    DELETE FROM public.user_followed_artists WHERE user_id=v_owner::text;
    DELETE FROM public.user_saved_albums WHERE user_id=v_owner::text;
    DELETE FROM public.user_play_history WHERE user_id=v_owner::text;
    DELETE FROM public.user_queue WHERE user_id=v_owner::text;
    DELETE FROM public.user_settings WHERE user_id=v_owner::text;
    IF to_regclass('public.user_library_state') IS NOT NULL THEN DELETE FROM public.user_library_state WHERE user_id=v_owner::text; END IF;
    IF to_regclass('public.playlist_sync_tombstones') IS NOT NULL THEN DELETE FROM public.playlist_sync_tombstones WHERE user_id=v_owner::text; END IF;
    DELETE FROM public.profile_privacy WHERE user_id=v_owner::text;
    DELETE FROM public.follows WHERE follower_id::text=v_owner::text OR following_id::text=v_owner::text;
    DELETE FROM public.profiles WHERE id::text=v_owner::text;
    DELETE FROM auth.users WHERE id=v_owner;
END $$;
REVOKE ALL ON FUNCTION public.gratify_delete_account() FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.gratify_delete_account() TO authenticated;
COMMIT;
