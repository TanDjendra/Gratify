-- Runtime SQL/RLS verification only. Synthetic rows are ALWAYS rolled back.
-- Does not claim Auth login, email, Storage API or device sync verification.
BEGIN;
DO $$
DECLARE
    a uuid := gen_random_uuid(); b uuid := gen_random_uuid();
    a_playlist text; b_playlist text; shared_id text;
    track jsonb := '[{"video_id":"gratify-audit-track","title":"Audit track","artist":"Audit","duration":60}]';
BEGIN
    INSERT INTO auth.users(id) VALUES(a),(b);
    INSERT INTO public.profiles(id,display_name,now_playing_title) VALUES(a,'Gratify audit A','Private audit activity'),(b,'Gratify audit B','B audit activity')
    ON CONFLICT(id) DO UPDATE SET display_name=excluded.display_name,now_playing_title=excluded.now_playing_title;
    PERFORM set_config('request.jwt.claim.sub',a::text,true);
    SET LOCAL ROLE authenticated;
    SELECT playlist_id INTO a_playlist FROM public.gratify_replace_cloud_playlist_v2(a::text,'Audit playlist',NULL,true,track);
    SELECT id::text INTO shared_id FROM public.shared_playlists WHERE client_sync_id=a::text;
    IF jsonb_array_length(public.gratify_get_cloud_playlist_items(a_playlist))<>1 THEN RAISE EXCEPTION 'Owner snapshot failed'; END IF;
    BEGIN
        PERFORM public.gratify_replace_cloud_playlist_v2(a::text,'Bad replacement',NULL,true,'[{"video_id":null}]');
        RAISE EXCEPTION 'Invalid replacement accepted' USING ERRCODE='P0002';
    EXCEPTION WHEN not_null_violation THEN NULL; END;
    IF (SELECT title FROM public.cloud_playlists WHERE id::text=a_playlist)<>'Audit playlist' THEN RAISE EXCEPTION 'Rollback failed'; END IF;
    PERFORM public.gratify_apply_library_changes('[{"kind":"user_liked_songs","item_id":"gratify-audit-liked","enabled":true,"payload":{"title":"Audit liked","duration":60}}]');
    PERFORM public.gratify_replace_queue('[{"video_id":"gratify-audit-queue","title":"Audit queue","duration":60}]');
    INSERT INTO public.follows(follower_id,following_id) VALUES(a::text,b::text);
    INSERT INTO public.user_follows(follower_id,following_id) VALUES(a,b);
    RESET ROLE;
    PERFORM set_config('request.jwt.claim.sub',b::text,true);
    SET LOCAL ROLE authenticated;
    IF EXISTS(SELECT 1 FROM public.cloud_playlists WHERE id::text=a_playlist) THEN RAISE EXCEPTION 'Other account can read private playlist'; END IF;
    IF EXISTS(SELECT 1 FROM public.user_liked_songs WHERE user_id=a) THEN RAISE EXCEPTION 'Other account can read private library'; END IF;
    IF (SELECT now_playing_title FROM public.public_profiles WHERE id=a) IS NOT NULL THEN RAISE EXCEPTION 'Private activity leaked'; END IF;
    SELECT playlist_id INTO b_playlist FROM public.gratify_replace_cloud_playlist_v2(a::text,'B identity isolated',NULL,false,track);
    IF b_playlist=a_playlist THEN RAISE EXCEPTION 'Owner identities collided'; END IF;
    RESET ROLE;
    PERFORM set_config('request.jwt.claim.sub','',true);
    SET LOCAL ROLE anon;
    IF public.gratify_get_cloud_playlist_items(a_playlist)<>'[]'::jsonb OR public.gratify_get_shared_playlist_tracks(shared_id)<>'[]'::jsonb THEN RAISE EXCEPTION 'Anonymous snapshot leaked'; END IF;
    IF EXISTS(SELECT 1 FROM public.follows WHERE follower_id=a::text) OR EXISTS(SELECT 1 FROM public.user_follows WHERE follower_id=a) THEN RAISE EXCEPTION 'Follow privacy leaked'; END IF;
    RESET ROLE;
    PERFORM set_config('request.jwt.claim.sub',a::text,true);
    SET LOCAL ROLE authenticated;
    PERFORM public.gratify_set_profile_privacy('show_playlists',true);
    RESET ROLE;
    PERFORM set_config('request.jwt.claim.sub','',true);
    SET LOCAL ROLE anon;
    IF jsonb_array_length(public.gratify_get_cloud_playlist_items(a_playlist))<>1 THEN RAISE EXCEPTION 'Explicit sharing unavailable'; END IF;
    RESET ROLE;
    PERFORM set_config('request.jwt.claim.sub',a::text,true);
    SET LOCAL ROLE authenticated;
    PERFORM public.gratify_delete_owned_playlist_v2(a::text);
    BEGIN
        PERFORM public.gratify_replace_cloud_playlist_v2(a::text,'Resurrected',NULL,false,track);
        RAISE EXCEPTION 'Deleted identity resurrected' USING ERRCODE='P0002';
    EXCEPTION WHEN raise_exception THEN
        IF SQLERRM<>'Playlist was deleted' THEN RAISE; END IF;
    END;
    PERFORM public.gratify_delete_account();
    RESET ROLE;
    IF EXISTS(SELECT 1 FROM auth.users WHERE id=a) OR NOT EXISTS(SELECT 1 FROM auth.users WHERE id=b) THEN RAISE EXCEPTION 'Account isolation failed'; END IF;
    IF NOT EXISTS(SELECT 1 FROM public.cloud_playlists WHERE id::text=b_playlist AND user_id=b) THEN RAISE EXCEPTION 'Other account playlist removed'; END IF;
END $$;
ROLLBACK;
SELECT 'PASS: live UUID RPCs, rollback, owner/anonymous RLS, both follow tables, sharing, deletion isolation; all synthetic rows rolled back' AS verification;
