BEGIN;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS now_playing_video_id text;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS now_playing_title text;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS now_playing_artist text;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS last_active_at text;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS top_artists jsonb;
CREATE TABLE IF NOT EXISTS public.profile_privacy (
    user_id text PRIMARY KEY,
    show_followers boolean NOT NULL DEFAULT false,
    show_playlists boolean NOT NULL DEFAULT false,
    show_recent_artists boolean NOT NULL DEFAULT false
);
ALTER TABLE public.profile_privacy ALTER COLUMN show_followers SET DEFAULT false;
ALTER TABLE public.profile_privacy ALTER COLUMN show_playlists SET DEFAULT false;
ALTER TABLE public.profile_privacy ALTER COLUMN show_recent_artists SET DEFAULT false;
ALTER TABLE public.profile_privacy ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS privacy_read ON public.profile_privacy;
DROP POLICY IF EXISTS privacy_write ON public.profile_privacy;
CREATE POLICY privacy_read ON public.profile_privacy FOR SELECT USING (true);
CREATE POLICY privacy_write ON public.profile_privacy FOR ALL TO authenticated
    USING (user_id=auth.uid()::text) WITH CHECK (user_id=auth.uid()::text);
REVOKE ALL ON public.profile_privacy FROM PUBLIC,anon,authenticated;
GRANT SELECT ON public.profile_privacy TO anon,authenticated;
GRANT INSERT,UPDATE ON public.profile_privacy TO authenticated;

-- Remove every permissive SELECT policy; SQL policies combine with OR.
DO $$ DECLARE p record; BEGIN
    FOR p IN SELECT tablename,policyname FROM pg_policies
      WHERE schemaname='public' AND tablename IN ('profiles','follows','shared_playlists','shared_playlist_tracks','cloud_playlists') AND (cmd IN ('SELECT','ALL') OR policyname LIKE '%\_write\_%' ESCAPE '\')
    LOOP EXECUTE format('DROP POLICY %I ON public.%I',p.policyname,p.tablename); END LOOP;
END $$;
CREATE POLICY profile_write_insert ON public.profiles FOR INSERT TO authenticated WITH CHECK (id::text=auth.uid()::text);
CREATE POLICY profile_write_update ON public.profiles FOR UPDATE TO authenticated USING (id::text=auth.uid()::text) WITH CHECK (id::text=auth.uid()::text);
CREATE POLICY profile_write_delete ON public.profiles FOR DELETE TO authenticated USING (id::text=auth.uid()::text);
CREATE POLICY follows_write_insert ON public.follows FOR INSERT TO authenticated WITH CHECK (follower_id::text=auth.uid()::text);
CREATE POLICY follows_write_delete ON public.follows FOR DELETE TO authenticated USING (follower_id::text=auth.uid()::text);
CREATE POLICY shared_write_insert ON public.shared_playlists FOR INSERT TO authenticated WITH CHECK (user_id=auth.uid()::text);
CREATE POLICY shared_write_update ON public.shared_playlists FOR UPDATE TO authenticated USING (user_id=auth.uid()::text) WITH CHECK (user_id=auth.uid()::text);
CREATE POLICY shared_write_delete ON public.shared_playlists FOR DELETE TO authenticated USING (user_id=auth.uid()::text);
CREATE POLICY shared_tracks_write_insert ON public.shared_playlist_tracks FOR INSERT TO authenticated WITH CHECK (EXISTS(SELECT 1 FROM public.shared_playlists sp WHERE sp.id=playlist_id AND sp.user_id=auth.uid()::text));
CREATE POLICY shared_tracks_write_update ON public.shared_playlist_tracks FOR UPDATE TO authenticated USING (EXISTS(SELECT 1 FROM public.shared_playlists sp WHERE sp.id=playlist_id AND sp.user_id=auth.uid()::text)) WITH CHECK (EXISTS(SELECT 1 FROM public.shared_playlists sp WHERE sp.id=playlist_id AND sp.user_id=auth.uid()::text));
CREATE POLICY shared_tracks_write_delete ON public.shared_playlist_tracks FOR DELETE TO authenticated USING (EXISTS(SELECT 1 FROM public.shared_playlists sp WHERE sp.id=playlist_id AND sp.user_id=auth.uid()::text));
CREATE POLICY cloud_write_insert ON public.cloud_playlists FOR INSERT TO authenticated WITH CHECK (user_id=auth.uid()::text);
CREATE POLICY cloud_write_update ON public.cloud_playlists FOR UPDATE TO authenticated USING (user_id=auth.uid()::text) WITH CHECK (user_id=auth.uid()::text);
CREATE POLICY cloud_write_delete ON public.cloud_playlists FOR DELETE TO authenticated USING (user_id=auth.uid()::text);
CREATE POLICY profile_owner_read ON public.profiles FOR SELECT TO authenticated USING (id::text=auth.uid()::text);
REVOKE SELECT ON public.profiles FROM anon;

-- This controlled projection is the only public profile read surface. Private columns never leave it.
CREATE OR REPLACE VIEW public.public_profiles WITH (security_barrier=true) AS
SELECT p.id,p.display_name,p.avatar_url,p.created_at,
       CASE WHEN p.id::text=auth.uid()::text OR coalesce(v.show_recent_artists,false) THEN p.now_playing_video_id ELSE NULL END AS now_playing_video_id,
       CASE WHEN p.id::text=auth.uid()::text OR coalesce(v.show_recent_artists,false) THEN p.now_playing_title ELSE NULL END AS now_playing_title,
       CASE WHEN p.id::text=auth.uid()::text OR coalesce(v.show_recent_artists,false) THEN p.now_playing_artist ELSE NULL END AS now_playing_artist,
       CASE WHEN p.id::text=auth.uid()::text OR coalesce(v.show_recent_artists,false) THEN p.last_active_at ELSE NULL END AS last_active_at,
       CASE WHEN p.id::text=auth.uid()::text OR coalesce(v.show_recent_artists,false) THEN p.top_artists ELSE NULL END AS top_artists,
       p.note,p.note_updated_at,
       coalesce(v.show_followers,false) AS show_followers,
       coalesce(v.show_playlists,false) AS show_playlists,
       coalesce(v.show_recent_artists,false) AS show_recent_artists
FROM public.profiles p LEFT JOIN public.profile_privacy v ON v.user_id=p.id::text;
REVOKE ALL ON public.public_profiles FROM PUBLIC;
GRANT SELECT ON public.public_profiles TO anon,authenticated;

CREATE POLICY follows_visible ON public.follows FOR SELECT USING (
    auth.uid()::text IN (follower_id::text,following_id::text) OR
    (coalesce((SELECT show_followers FROM public.profile_privacy WHERE user_id=follower_id::text),false)
     AND coalesce((SELECT show_followers FROM public.profile_privacy WHERE user_id=following_id::text),false))
);
CREATE POLICY shared_visible ON public.shared_playlists FOR SELECT USING (
    user_id=auth.uid()::text OR coalesce((SELECT show_playlists FROM public.profile_privacy WHERE user_id=shared_playlists.user_id),false)
);
CREATE POLICY shared_tracks_visible ON public.shared_playlist_tracks FOR SELECT USING (
    EXISTS(SELECT 1 FROM public.shared_playlists sp WHERE sp.id=shared_playlist_tracks.playlist_id)
);
CREATE POLICY cloud_visible ON public.cloud_playlists FOR SELECT USING (
    user_id=auth.uid()::text OR (is_public IS TRUE AND coalesce((SELECT show_playlists FROM public.profile_privacy WHERE user_id=cloud_playlists.user_id),false))
);

CREATE OR REPLACE FUNCTION public.gratify_set_profile_privacy(p_setting text,p_visible boolean)
RETURNS void LANGUAGE plpgsql SECURITY INVOKER SET search_path='' AS $$
DECLARE v_owner text:=auth.uid()::text;
BEGIN
    IF v_owner IS NULL THEN RAISE EXCEPTION 'Authentication required' USING ERRCODE='42501'; END IF;
    IF p_setting IS NULL OR p_setting NOT IN ('show_followers','show_playlists','show_recent_artists') OR p_visible IS NULL THEN RAISE EXCEPTION 'Invalid privacy setting'; END IF;
    INSERT INTO public.profile_privacy(user_id) VALUES(v_owner) ON CONFLICT DO NOTHING;
    UPDATE public.profile_privacy SET
        show_followers=CASE WHEN p_setting='show_followers' THEN p_visible ELSE show_followers END,
        show_playlists=CASE WHEN p_setting='show_playlists' THEN p_visible ELSE show_playlists END,
        show_recent_artists=CASE WHEN p_setting='show_recent_artists' THEN p_visible ELSE show_recent_artists END
    WHERE user_id=v_owner;
END $$;
REVOKE ALL ON FUNCTION public.gratify_set_profile_privacy(text,boolean) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.gratify_set_profile_privacy(text,boolean) TO authenticated;
COMMIT;
