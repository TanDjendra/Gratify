-- Gratify launch fixes. UUID production variant; preserves existing UUID primary and foreign keys.
-- Run on the authorized Gratify project only. All changes commit together.
BEGIN;
DO $$ BEGIN
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='profiles' AND column_name='id') THEN RAISE EXCEPTION 'Schema mismatch: profiles.id missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='profiles' AND column_name='display_name') THEN RAISE EXCEPTION 'Schema mismatch: profiles.display_name missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='profiles' AND column_name='avatar_url') THEN RAISE EXCEPTION 'Schema mismatch: profiles.avatar_url missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='profiles' AND column_name='note') THEN RAISE EXCEPTION 'Schema mismatch: profiles.note missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='profiles' AND column_name='note_updated_at') THEN RAISE EXCEPTION 'Schema mismatch: profiles.note_updated_at missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='follows' AND column_name='follower_id') THEN RAISE EXCEPTION 'Schema mismatch: follows.follower_id missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='follows' AND column_name='following_id') THEN RAISE EXCEPTION 'Schema mismatch: follows.following_id missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='shared_playlists' AND column_name='id') THEN RAISE EXCEPTION 'Schema mismatch: shared_playlists.id missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='shared_playlists' AND column_name='user_id') THEN RAISE EXCEPTION 'Schema mismatch: shared_playlists.user_id missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='shared_playlists' AND column_name='title') THEN RAISE EXCEPTION 'Schema mismatch: shared_playlists.title missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='shared_playlists' AND column_name='created_at') THEN RAISE EXCEPTION 'Schema mismatch: shared_playlists.created_at missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='shared_playlist_tracks' AND column_name='playlist_id') THEN RAISE EXCEPTION 'Schema mismatch: shared_playlist_tracks.playlist_id missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='shared_playlist_tracks' AND column_name='video_id') THEN RAISE EXCEPTION 'Schema mismatch: shared_playlist_tracks.video_id missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='shared_playlist_tracks' AND column_name='title') THEN RAISE EXCEPTION 'Schema mismatch: shared_playlist_tracks.title missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='shared_playlist_tracks' AND column_name='artists') THEN RAISE EXCEPTION 'Schema mismatch: shared_playlist_tracks.artists missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='shared_playlist_tracks' AND column_name='duration_seconds') THEN RAISE EXCEPTION 'Schema mismatch: shared_playlist_tracks.duration_seconds missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='shared_playlist_saves' AND column_name='playlist_id') THEN RAISE EXCEPTION 'Schema mismatch: shared_playlist_saves.playlist_id missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='shared_playlist_saves' AND column_name='user_id') THEN RAISE EXCEPTION 'Schema mismatch: shared_playlist_saves.user_id missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='cloud_playlists' AND column_name='id') THEN RAISE EXCEPTION 'Schema mismatch: cloud_playlists.id missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='cloud_playlists' AND column_name='user_id') THEN RAISE EXCEPTION 'Schema mismatch: cloud_playlists.user_id missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='cloud_playlists' AND column_name='local_playlist_id') THEN RAISE EXCEPTION 'Schema mismatch: cloud_playlists.local_playlist_id missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='cloud_playlists' AND column_name='title') THEN RAISE EXCEPTION 'Schema mismatch: cloud_playlists.title missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='cloud_playlists' AND column_name='is_public') THEN RAISE EXCEPTION 'Schema mismatch: cloud_playlists.is_public missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='cloud_playlists' AND column_name='created_at') THEN RAISE EXCEPTION 'Schema mismatch: cloud_playlists.created_at missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='cloud_playlist_items' AND column_name='playlist_id') THEN RAISE EXCEPTION 'Schema mismatch: cloud_playlist_items.playlist_id missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='cloud_playlist_items' AND column_name='video_id') THEN RAISE EXCEPTION 'Schema mismatch: cloud_playlist_items.video_id missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='cloud_playlist_items' AND column_name='title') THEN RAISE EXCEPTION 'Schema mismatch: cloud_playlist_items.title missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='cloud_playlist_items' AND column_name='artist') THEN RAISE EXCEPTION 'Schema mismatch: cloud_playlist_items.artist missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='cloud_playlist_items' AND column_name='duration') THEN RAISE EXCEPTION 'Schema mismatch: cloud_playlist_items.duration missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='cloud_playlist_items' AND column_name='thumbnail_url') THEN RAISE EXCEPTION 'Schema mismatch: cloud_playlist_items.thumbnail_url missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='cloud_playlist_items' AND column_name='position') THEN RAISE EXCEPTION 'Schema mismatch: cloud_playlist_items.position missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='user_liked_songs' AND column_name='id') THEN RAISE EXCEPTION 'Schema mismatch: user_liked_songs.id missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='user_liked_songs' AND column_name='user_id') THEN RAISE EXCEPTION 'Schema mismatch: user_liked_songs.user_id missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='user_liked_songs' AND column_name='video_id') THEN RAISE EXCEPTION 'Schema mismatch: user_liked_songs.video_id missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='user_liked_songs' AND column_name='favorite_at') THEN RAISE EXCEPTION 'Schema mismatch: user_liked_songs.favorite_at missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='user_followed_artists' AND column_name='id') THEN RAISE EXCEPTION 'Schema mismatch: user_followed_artists.id missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='user_followed_artists' AND column_name='user_id') THEN RAISE EXCEPTION 'Schema mismatch: user_followed_artists.user_id missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='user_followed_artists' AND column_name='channel_id') THEN RAISE EXCEPTION 'Schema mismatch: user_followed_artists.channel_id missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='user_followed_artists' AND column_name='followed_at') THEN RAISE EXCEPTION 'Schema mismatch: user_followed_artists.followed_at missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='user_saved_albums' AND column_name='id') THEN RAISE EXCEPTION 'Schema mismatch: user_saved_albums.id missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='user_saved_albums' AND column_name='user_id') THEN RAISE EXCEPTION 'Schema mismatch: user_saved_albums.user_id missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='user_saved_albums' AND column_name='browse_id') THEN RAISE EXCEPTION 'Schema mismatch: user_saved_albums.browse_id missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='user_saved_albums' AND column_name='favorite_at') THEN RAISE EXCEPTION 'Schema mismatch: user_saved_albums.favorite_at missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='user_play_history' AND column_name='id') THEN RAISE EXCEPTION 'Schema mismatch: user_play_history.id missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='user_play_history' AND column_name='user_id') THEN RAISE EXCEPTION 'Schema mismatch: user_play_history.user_id missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='user_play_history' AND column_name='video_id') THEN RAISE EXCEPTION 'Schema mismatch: user_play_history.video_id missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='user_play_history' AND column_name='played_at') THEN RAISE EXCEPTION 'Schema mismatch: user_play_history.played_at missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='user_queue' AND column_name='id') THEN RAISE EXCEPTION 'Schema mismatch: user_queue.id missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='user_queue' AND column_name='user_id') THEN RAISE EXCEPTION 'Schema mismatch: user_queue.user_id missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='user_queue' AND column_name='video_id') THEN RAISE EXCEPTION 'Schema mismatch: user_queue.video_id missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='user_queue' AND column_name='duration') THEN RAISE EXCEPTION 'Schema mismatch: user_queue.duration missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='user_queue' AND column_name='position') THEN RAISE EXCEPTION 'Schema mismatch: user_queue.position missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='user_settings' AND column_name='user_id') THEN RAISE EXCEPTION 'Schema mismatch: user_settings.user_id missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='user_settings' AND column_name='settings_json') THEN RAISE EXCEPTION 'Schema mismatch: user_settings.settings_json missing'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='shared_playlists' AND column_name='user_id' AND data_type='uuid') THEN RAISE EXCEPTION 'Schema mismatch: shared_playlists.user_id expected uuid'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='shared_playlist_saves' AND column_name='user_id' AND data_type='text') THEN RAISE EXCEPTION 'Schema mismatch: shared_playlist_saves.user_id expected text'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='cloud_playlists' AND column_name='user_id' AND data_type='uuid') THEN RAISE EXCEPTION 'Schema mismatch: cloud_playlists.user_id expected uuid'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='user_liked_songs' AND column_name='user_id' AND data_type='uuid') THEN RAISE EXCEPTION 'Schema mismatch: user_liked_songs.user_id expected uuid'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='user_followed_artists' AND column_name='user_id' AND data_type='uuid') THEN RAISE EXCEPTION 'Schema mismatch: user_followed_artists.user_id expected uuid'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='user_saved_albums' AND column_name='user_id' AND data_type='uuid') THEN RAISE EXCEPTION 'Schema mismatch: user_saved_albums.user_id expected uuid'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='user_play_history' AND column_name='user_id' AND data_type='uuid') THEN RAISE EXCEPTION 'Schema mismatch: user_play_history.user_id expected uuid'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='user_queue' AND column_name='user_id' AND data_type='uuid') THEN RAISE EXCEPTION 'Schema mismatch: user_queue.user_id expected uuid'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='user_settings' AND column_name='user_id' AND data_type='uuid') THEN RAISE EXCEPTION 'Schema mismatch: user_settings.user_id expected uuid'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='cloud_playlists' AND column_name='id' AND data_type='uuid') THEN RAISE EXCEPTION 'Schema mismatch: cloud_playlists.id expected uuid'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='shared_playlists' AND column_name='id' AND data_type='uuid') THEN RAISE EXCEPTION 'Schema mismatch: shared_playlists.id expected uuid'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='cloud_playlist_items' AND column_name='playlist_id' AND data_type='uuid') THEN RAISE EXCEPTION 'Schema mismatch: cloud_playlist_items.playlist_id expected uuid'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='shared_playlist_tracks' AND column_name='playlist_id' AND data_type='uuid') THEN RAISE EXCEPTION 'Schema mismatch: shared_playlist_tracks.playlist_id expected uuid'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='user_queue' AND column_name='duration' AND data_type='integer') THEN RAISE EXCEPTION 'Schema mismatch: user_queue.duration expected integer'; END IF;
IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='user_settings' AND column_name='settings_json' AND data_type='text') THEN RAISE EXCEPTION 'Schema mismatch: user_settings.settings_json expected text'; END IF;
END $$;

-- Source: supabase_user_data_rls_hotfix.sql
-- Jalankan sekali di Supabase SQL Editor untuk database yang sudah memakai
-- supabase_setup.sql versi lama. Seluruh perubahan berada dalam satu transaksi.
-- Jika satu tabel tidak ada atau satu perintah gagal, perubahan dibatalkan.
-- Data yang telanjur diakses atau diubah pihak lain tidak dapat dipulihkan oleh skrip ini.

DO $$
DECLARE
    t TEXT;
    p RECORD;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'user_liked_songs',
        'user_followed_artists',
        'user_saved_albums',
        'user_play_history',
        'user_queue',
        'user_settings'
    ] LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t);

        -- Semua policy lama dihapus: policy permisif PostgreSQL digabung dengan OR.
        FOR p IN
            SELECT policyname FROM pg_policies
            WHERE schemaname = 'public' AND tablename = t
        LOOP
            EXECUTE format('DROP POLICY %I ON public.%I', p.policyname, t);
        END LOOP;

        -- user_id::text mendukung skema lama (TEXT) dan migrasi (UUID).
        EXECUTE format(
            'CREATE POLICY %I ON public.%I FOR ALL TO authenticated '
            || 'USING (auth.uid()::text = user_id::text) '
            || 'WITH CHECK (auth.uid()::text = user_id::text)',
            t || '_owner_only', t
        );

        EXECUTE format('REVOKE ALL ON public.%I FROM anon, authenticated, PUBLIC', t);
        EXECUTE format(
            'GRANT SELECT, INSERT, UPDATE, DELETE ON public.%I TO authenticated', t
        );
    END LOOP;
END $$;

-- Setelah menjalankan, periksa pg_policies: harus ada satu policy _owner_only
-- per tabel di atas. Pastikan role anon tidak punya hak tabel dan uji akses
-- memakai dua akun berbeda sebelum mengaktifkan sinkronisasi lagi.


-- Source: supabase_social_rls_hotfix.sql
-- Jalankan di Supabase SQL Editor pada database yang sudah memakai setup lama.
-- Semua perubahan atomik. Policy lama dihapus karena policy permisif digabung
-- dengan OR; membiarkannya tetap ada akan melewati pembatasan pemilik.
-- Nilai is_public pada playlist yang sudah ada tidak diubah; tinjau data lama
-- jika sebelumnya ada playlist privat yang tersimpan sebagai publik.

DO $$
DECLARE
    t TEXT;
    p RECORD;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'profiles',
        'follows',
        'shared_playlists',
        'shared_playlist_saves',
        'shared_playlist_tracks',
        'cloud_playlists',
        'cloud_playlist_items'
    ] LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t);
        FOR p IN
            SELECT policyname FROM pg_policies
            WHERE schemaname = 'public' AND tablename = t
        LOOP
            EXECUTE format('DROP POLICY %I ON public.%I', p.policyname, t);
        END LOOP;
        EXECUTE format('REVOKE ALL ON public.%I FROM anon, authenticated, PUBLIC', t);
    END LOOP;
END $$;

CREATE POLICY profiles_read_public ON public.profiles FOR SELECT USING (true);
CREATE POLICY profiles_insert_owner ON public.profiles FOR INSERT TO authenticated
    WITH CHECK (auth.uid()::text = id::text);
CREATE POLICY profiles_update_owner ON public.profiles FOR UPDATE TO authenticated
    USING (auth.uid()::text = id::text)
    WITH CHECK (auth.uid()::text = id::text);
GRANT SELECT ON public.profiles TO anon, authenticated;
GRANT INSERT, UPDATE ON public.profiles TO authenticated;

CREATE POLICY follows_read_public ON public.follows FOR SELECT USING (true);
CREATE POLICY follows_insert_follower ON public.follows FOR INSERT TO authenticated
    WITH CHECK (auth.uid()::text = follower_id::text);
CREATE POLICY follows_delete_follower ON public.follows FOR DELETE TO authenticated
    USING (auth.uid()::text = follower_id::text);
GRANT SELECT ON public.follows TO anon, authenticated;
GRANT INSERT, DELETE ON public.follows TO authenticated;

ALTER TABLE public.shared_playlists ALTER COLUMN id SET DEFAULT gen_random_uuid();
CREATE POLICY shared_playlists_read_public ON public.shared_playlists FOR SELECT USING (true);
CREATE POLICY shared_playlists_insert_owner ON public.shared_playlists FOR INSERT TO authenticated
    WITH CHECK (auth.uid()::text = user_id::text);
CREATE POLICY shared_playlists_update_owner ON public.shared_playlists FOR UPDATE TO authenticated
    USING (auth.uid()::text = user_id::text)
    WITH CHECK (auth.uid()::text = user_id::text);
CREATE POLICY shared_playlists_delete_owner ON public.shared_playlists FOR DELETE TO authenticated
    USING (auth.uid()::text = user_id::text);
GRANT SELECT ON public.shared_playlists TO anon, authenticated;
GRANT INSERT (user_id, title, thumbnail_url, creator_name) ON public.shared_playlists TO authenticated;
GRANT UPDATE (title, thumbnail_url, creator_name) ON public.shared_playlists TO authenticated;
GRANT DELETE ON public.shared_playlists TO authenticated;

-- Daftar siapa yang menyimpan playlist hanya dapat dibaca pemilik catatan.
-- Hitungan publik tetap tersedia lewat shared_playlists.add_count.
CREATE POLICY shared_playlist_saves_read_owner ON public.shared_playlist_saves FOR SELECT TO authenticated
    USING (auth.uid()::text = user_id::text);
CREATE POLICY shared_playlist_saves_insert_owner ON public.shared_playlist_saves FOR INSERT TO authenticated
    WITH CHECK (auth.uid()::text = user_id::text);
CREATE POLICY shared_playlist_saves_delete_owner ON public.shared_playlist_saves FOR DELETE TO authenticated
    USING (auth.uid()::text = user_id::text);
GRANT SELECT, INSERT, DELETE ON public.shared_playlist_saves TO authenticated;

-- Pulihkan hitungan yang mungkin pernah diubah langsung sebelum hak kolom dibatasi.
UPDATE public.shared_playlists sp
SET add_count = (
    SELECT COUNT(*) FROM public.shared_playlist_saves s WHERE s.playlist_id = sp.id
);

ALTER TABLE public.shared_playlist_tracks ALTER COLUMN id SET DEFAULT gen_random_uuid();
CREATE POLICY shared_playlist_tracks_read_public ON public.shared_playlist_tracks FOR SELECT USING (true);
CREATE POLICY shared_playlist_tracks_insert_owner ON public.shared_playlist_tracks FOR INSERT TO authenticated
    WITH CHECK (EXISTS (
        SELECT 1 FROM public.shared_playlists sp
        WHERE sp.id = playlist_id AND sp.user_id::text = auth.uid()::text
    ));
CREATE POLICY shared_playlist_tracks_delete_owner ON public.shared_playlist_tracks FOR DELETE TO authenticated
    USING (EXISTS (
        SELECT 1 FROM public.shared_playlists sp
        WHERE sp.id = playlist_id AND sp.user_id::text = auth.uid()::text
    ));
GRANT SELECT ON public.shared_playlist_tracks TO anon, authenticated;
GRANT INSERT, DELETE ON public.shared_playlist_tracks TO authenticated;

-- Playlist privat hanya terlihat oleh pemilik; yang publik tetap bisa dibaca.
ALTER TABLE public.cloud_playlists ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE public.cloud_playlists ALTER COLUMN is_public SET DEFAULT false;
CREATE POLICY cloud_playlists_read_visible ON public.cloud_playlists FOR SELECT
    USING (is_public IS TRUE OR auth.uid()::text = user_id::text);
CREATE POLICY cloud_playlists_manage_owner ON public.cloud_playlists FOR ALL TO authenticated
    USING (auth.uid()::text = user_id::text)
    WITH CHECK (auth.uid()::text = user_id::text);
GRANT SELECT ON public.cloud_playlists TO anon, authenticated;
GRANT INSERT, UPDATE, DELETE ON public.cloud_playlists TO authenticated;

ALTER TABLE public.cloud_playlist_items ALTER COLUMN id SET DEFAULT gen_random_uuid();
CREATE POLICY cloud_playlist_items_read_visible ON public.cloud_playlist_items FOR SELECT
    USING (EXISTS (
        SELECT 1 FROM public.cloud_playlists cp
        WHERE cp.id = playlist_id AND (cp.is_public IS TRUE OR cp.user_id::text = auth.uid()::text)
    ));
CREATE POLICY cloud_playlist_items_manage_owner ON public.cloud_playlist_items FOR ALL TO authenticated
    USING (EXISTS (
        SELECT 1 FROM public.cloud_playlists cp
        WHERE cp.id = playlist_id AND cp.user_id::text = auth.uid()::text
    ))
    WITH CHECK (EXISTS (
        SELECT 1 FROM public.cloud_playlists cp
        WHERE cp.id = playlist_id AND cp.user_id::text = auth.uid()::text
    ));
GRANT SELECT ON public.cloud_playlist_items TO anon, authenticated;
GRANT INSERT, UPDATE, DELETE ON public.cloud_playlist_items TO authenticated;


-- Source: 202610010001_launch_integrity.sql
-- Apply after the existing Gratify schema and ownership RLS hotfixes.
-- Functions run with the caller's rights; auth.uid and RLS both enforce ownership.

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
        WHERE sp.id::text=p_playlist_id AND sp.user_id::text=v_owner FOR UPDATE;
        IF v_id IS NULL THEN RAISE EXCEPTION 'Playlist not owned' USING ERRCODE='42501'; END IF;
    ELSE
        SELECT sp.id INTO v_id FROM public.shared_playlists sp
        WHERE sp.user_id::text=v_owner AND sp.title=v_title ORDER BY sp.created_at LIMIT 1 FOR UPDATE;
    END IF;
    IF v_id IS NULL THEN
        INSERT INTO public.shared_playlists(user_id,title,thumbnail_url,creator_name)
        VALUES(v_owner::uuid,v_title,p_thumbnail_url,p_creator_name) RETURNING id INTO v_id;
    ELSE
        UPDATE public.shared_playlists SET title=v_title,thumbnail_url=p_thumbnail_url,creator_name=p_creator_name WHERE id::text=v_id;
    END IF;
    DELETE FROM public.shared_playlist_tracks WHERE shared_playlist_tracks.playlist_id::text=v_id;
    INSERT INTO public.shared_playlist_tracks(playlist_id,video_id,title,artists,duration_seconds,position)
    SELECT v_id::uuid, item->>'video_id', item->>'title', item->>'artists',
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
    WHERE cp.user_id::text=v_owner AND cp.local_playlist_id=p_local_playlist_id ORDER BY cp.created_at LIMIT 1 FOR UPDATE;
    v_public := coalesce(p_is_public,v_public,false);
    IF v_id IS NULL THEN
        INSERT INTO public.cloud_playlists(user_id,local_playlist_id,title,thumbnail_url,is_public)
        VALUES(v_owner::uuid,p_local_playlist_id,p_title,p_thumbnail_url,v_public) RETURNING id INTO v_id;
    ELSE
        UPDATE public.cloud_playlists SET title=p_title,thumbnail_url=p_thumbnail_url,is_public=v_public WHERE id::text=v_id;
    END IF;
    DELETE FROM public.cloud_playlist_items WHERE cloud_playlist_items.playlist_id::text=v_id;
    INSERT INTO public.cloud_playlist_items(playlist_id,video_id,title,artist,duration,thumbnail_url,position)
    SELECT v_id::uuid,item->>'video_id',item->>'title',item->>'artist',
           (item->>'duration')::integer,item->>'thumbnail_url',(ordinality-1)::integer
    FROM jsonb_array_elements(p_tracks) WITH ORDINALITY AS tracks(item,ordinality);
    SELECT sp.id INTO v_shared FROM public.shared_playlists sp
    WHERE sp.user_id::text=v_owner AND sp.title LIKE '%|||' || p_local_playlist_id ORDER BY sp.created_at LIMIT 1;
    IF v_public THEN
        SELECT coalesce(jsonb_agg(jsonb_build_object('video_id',item->>'video_id','title',item->>'title',
               'artists',item->>'artist','duration_seconds',(item->>'duration')::integer) ORDER BY ordinality),'[]'::jsonb)
        INTO v_shared_tracks FROM jsonb_array_elements(p_tracks) WITH ORDINALITY AS tracks(item,ordinality);
        PERFORM public.gratify_replace_shared_playlist(v_shared,p_local_playlist_id,p_title,p_thumbnail_url,
                (SELECT display_name FROM public.profiles WHERE id::text=v_owner),v_shared_tracks);
    ELSIF v_shared IS NOT NULL THEN
        DELETE FROM public.shared_playlists WHERE id::text=v_shared AND user_id::text=v_owner;
    END IF;
    RETURN QUERY SELECT v_id;
END $$;

REVOKE ALL ON FUNCTION public.gratify_replace_shared_playlist(text,bigint,text,text,text,jsonb) FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.gratify_replace_cloud_playlist(bigint,text,text,boolean,jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.gratify_replace_shared_playlist(text,bigint,text,text,text,jsonb) TO authenticated;
GRANT EXECUTE ON FUNCTION public.gratify_replace_cloud_playlist(bigint,text,text,boolean,jsonb) TO authenticated;


-- Source: 202610010002_profile_privacy.sql

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
    USING (user_id::text=auth.uid()::text) WITH CHECK (user_id::text=auth.uid()::text);
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
CREATE POLICY shared_write_insert ON public.shared_playlists FOR INSERT TO authenticated WITH CHECK (user_id::text=auth.uid()::text);
CREATE POLICY shared_write_update ON public.shared_playlists FOR UPDATE TO authenticated USING (user_id::text=auth.uid()::text) WITH CHECK (user_id::text=auth.uid()::text);
CREATE POLICY shared_write_delete ON public.shared_playlists FOR DELETE TO authenticated USING (user_id::text=auth.uid()::text);
CREATE POLICY shared_tracks_write_insert ON public.shared_playlist_tracks FOR INSERT TO authenticated WITH CHECK (EXISTS(SELECT 1 FROM public.shared_playlists sp WHERE sp.id=playlist_id AND sp.user_id::text=auth.uid()::text));
CREATE POLICY shared_tracks_write_update ON public.shared_playlist_tracks FOR UPDATE TO authenticated USING (EXISTS(SELECT 1 FROM public.shared_playlists sp WHERE sp.id=playlist_id AND sp.user_id::text=auth.uid()::text)) WITH CHECK (EXISTS(SELECT 1 FROM public.shared_playlists sp WHERE sp.id=playlist_id AND sp.user_id::text=auth.uid()::text));
CREATE POLICY shared_tracks_write_delete ON public.shared_playlist_tracks FOR DELETE TO authenticated USING (EXISTS(SELECT 1 FROM public.shared_playlists sp WHERE sp.id=playlist_id AND sp.user_id::text=auth.uid()::text));
CREATE POLICY cloud_write_insert ON public.cloud_playlists FOR INSERT TO authenticated WITH CHECK (user_id::text=auth.uid()::text);
CREATE POLICY cloud_write_update ON public.cloud_playlists FOR UPDATE TO authenticated USING (user_id::text=auth.uid()::text) WITH CHECK (user_id::text=auth.uid()::text);
CREATE POLICY cloud_write_delete ON public.cloud_playlists FOR DELETE TO authenticated USING (user_id::text=auth.uid()::text);
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
FROM public.profiles p LEFT JOIN public.profile_privacy v ON v.user_id::text=p.id::text;
REVOKE ALL ON public.public_profiles FROM PUBLIC;
GRANT SELECT ON public.public_profiles TO anon,authenticated;

CREATE POLICY follows_visible ON public.follows FOR SELECT USING (
    auth.uid()::text IN (follower_id::text,following_id::text) OR
    (coalesce((SELECT show_followers FROM public.profile_privacy WHERE user_id::text=follower_id::text),false)
     AND coalesce((SELECT show_followers FROM public.profile_privacy WHERE user_id::text=following_id::text),false))
);
CREATE POLICY shared_visible ON public.shared_playlists FOR SELECT USING (
    user_id::text=auth.uid()::text OR coalesce((SELECT show_playlists FROM public.profile_privacy WHERE user_id::text=shared_playlists.user_id::text),false)
);
CREATE POLICY shared_tracks_visible ON public.shared_playlist_tracks FOR SELECT USING (
    EXISTS(SELECT 1 FROM public.shared_playlists sp WHERE sp.id=shared_playlist_tracks.playlist_id)
);
CREATE POLICY cloud_visible ON public.cloud_playlists FOR SELECT USING (
    user_id::text=auth.uid()::text OR (is_public IS TRUE AND coalesce((SELECT show_playlists FROM public.profile_privacy WHERE user_id::text=cloud_playlists.user_id::text),false))
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
    WHERE user_id::text=v_owner;
END $$;
REVOKE ALL ON FUNCTION public.gratify_set_profile_privacy(text,boolean) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.gratify_set_profile_privacy(text,boolean) TO authenticated;


-- Source: 202610010003_playback_restore.sql

ALTER TABLE public.user_play_history ADD COLUMN IF NOT EXISTS listen_count bigint NOT NULL DEFAULT 1 CHECK (listen_count >= 0);

CREATE OR REPLACE FUNCTION public.gratify_replace_queue(p_items jsonb) RETURNS void
LANGUAGE plpgsql SECURITY INVOKER SET search_path = '' AS $$
DECLARE v_owner text := auth.uid()::text;
BEGIN
    IF v_owner IS NULL THEN RAISE EXCEPTION 'Authentication required' USING ERRCODE='42501'; END IF;
    IF p_items IS NULL OR jsonb_typeof(p_items) <> 'array' OR jsonb_array_length(p_items) > 10000 THEN
        RAISE EXCEPTION 'Invalid queue';
    END IF;
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_owner || ':queue',0));
    DELETE FROM public.user_queue WHERE user_id::text=v_owner;
    INSERT INTO public.user_queue(id,user_id,video_id,title,artist_name,duration,thumbnail_url,position)
    SELECT v_owner || '_queue_' || (ordinality-1),v_owner::uuid,item->>'video_id',item->>'title',
        item->>'artist_name',(item->>'duration')::integer,item->>'thumbnail_url',(ordinality-1)::integer
    FROM jsonb_array_elements(p_items) WITH ORDINALITY AS tracks(item,ordinality);
END $$;
REVOKE ALL ON FUNCTION public.gratify_replace_queue(jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.gratify_replace_queue(jsonb) TO authenticated;


-- Source: 202610010004_playlist_delete.sql

CREATE OR REPLACE FUNCTION public.gratify_delete_owned_playlist(p_local_playlist_id bigint) RETURNS void
LANGUAGE plpgsql SECURITY INVOKER SET search_path = '' AS $$
DECLARE v_owner text := auth.uid()::text;
BEGIN
    IF v_owner IS NULL THEN RAISE EXCEPTION 'Authentication required' USING ERRCODE='42501'; END IF;
    IF p_local_playlist_id IS NULL OR p_local_playlist_id < 0 THEN RAISE EXCEPTION 'Invalid playlist'; END IF;
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_owner || ':cloud:' || p_local_playlist_id,0));
    DELETE FROM public.cloud_playlists WHERE user_id::text=v_owner AND local_playlist_id=p_local_playlist_id;
    DELETE FROM public.shared_playlists WHERE user_id::text=v_owner AND title LIKE '%|||' || p_local_playlist_id;
END $$;
REVOKE ALL ON FUNCTION public.gratify_delete_owned_playlist(bigint) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.gratify_delete_owned_playlist(bigint) TO authenticated;


-- Source: 202610010005_account_delete.sql

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
    DELETE FROM public.shared_playlist_saves WHERE user_id::text=v_owner::text;
    DELETE FROM public.shared_playlists WHERE user_id::text=v_owner::text;
    DELETE FROM public.cloud_playlists WHERE user_id::text=v_owner::text;
    DELETE FROM public.user_liked_songs WHERE user_id::text=v_owner::text;
    DELETE FROM public.user_followed_artists WHERE user_id::text=v_owner::text;
    DELETE FROM public.user_saved_albums WHERE user_id::text=v_owner::text;
    DELETE FROM public.user_play_history WHERE user_id::text=v_owner::text;
    DELETE FROM public.user_queue WHERE user_id::text=v_owner::text;
    DELETE FROM public.user_settings WHERE user_id::text=v_owner::text;
    IF to_regclass('public.user_library_state') IS NOT NULL THEN DELETE FROM public.user_library_state WHERE user_id::text=v_owner::text; END IF;
    IF to_regclass('public.playlist_sync_tombstones') IS NOT NULL THEN DELETE FROM public.playlist_sync_tombstones WHERE user_id::text=v_owner::text; END IF;
    DELETE FROM public.profile_privacy WHERE user_id::text=v_owner::text;
    DELETE FROM public.follows WHERE follower_id::text=v_owner::text OR following_id::text=v_owner::text;
    DELETE FROM public.profiles WHERE id::text=v_owner::text;
    DELETE FROM auth.users WHERE id=v_owner;
END $$;
REVOKE ALL ON FUNCTION public.gratify_delete_account() FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.gratify_delete_account() TO authenticated;



CREATE UNIQUE INDEX IF NOT EXISTS gratify_liked_owner_video ON public.user_liked_songs(user_id,video_id);
CREATE UNIQUE INDEX IF NOT EXISTS gratify_artist_owner_channel ON public.user_followed_artists(user_id,channel_id);
CREATE UNIQUE INDEX IF NOT EXISTS gratify_album_owner_browse ON public.user_saved_albums(user_id,browse_id);

-- Source: 202610010006_library_changes.sql

CREATE TABLE IF NOT EXISTS public.user_library_state (
    user_id text NOT NULL, kind text NOT NULL, item_id text NOT NULL,
    enabled boolean NOT NULL, updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(user_id,kind,item_id)
);
ALTER TABLE public.user_library_state ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS library_state_owner ON public.user_library_state;
CREATE POLICY library_state_owner ON public.user_library_state FOR ALL TO authenticated
    USING(user_id::text=auth.uid()::text) WITH CHECK(user_id::text=auth.uid()::text);
REVOKE ALL ON public.user_library_state FROM PUBLIC,anon,authenticated;
GRANT SELECT,INSERT,UPDATE,DELETE ON public.user_library_state TO authenticated;

CREATE OR REPLACE FUNCTION public.gratify_apply_library_changes(p_changes jsonb) RETURNS void
LANGUAGE plpgsql SECURITY INVOKER SET search_path='' AS $$
DECLARE v_owner text:=auth.uid()::text; c jsonb; p jsonb; k text; i text; e boolean;
BEGIN
    IF v_owner IS NULL THEN RAISE EXCEPTION 'Authentication required' USING ERRCODE='42501'; END IF;
    IF p_changes IS NULL OR jsonb_typeof(p_changes)<>'array' OR jsonb_array_length(p_changes)>100 THEN RAISE EXCEPTION 'Invalid changes'; END IF;
    FOR c IN SELECT value FROM jsonb_array_elements(p_changes) LOOP
        k:=c->>'kind'; i:=c->>'item_id'; e:=(c->>'enabled')::boolean; p:=c->'payload';
        IF k IS NULL OR k NOT IN ('user_liked_songs','user_followed_artists','user_saved_albums') OR i IS NULL OR length(i)=0 OR e IS NULL THEN RAISE EXCEPTION 'Invalid library item'; END IF;
        PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_owner || ':' || k || ':' || i,0));
        IF NOT e THEN
            CASE k
            WHEN 'user_liked_songs' THEN DELETE FROM public.user_liked_songs WHERE user_id::text=v_owner AND video_id=i;
            WHEN 'user_followed_artists' THEN DELETE FROM public.user_followed_artists WHERE user_id::text=v_owner AND channel_id=i;
            WHEN 'user_saved_albums' THEN DELETE FROM public.user_saved_albums WHERE user_id::text=v_owner AND browse_id=i;
            END CASE;
        ELSE
            IF p IS NULL OR jsonb_typeof(p)<>'object' THEN RAISE EXCEPTION 'Missing library metadata'; END IF;
            CASE k
            WHEN 'user_liked_songs' THEN
                INSERT INTO public.user_liked_songs(id,user_id,video_id,title,artist_name,artist_id,album_name,album_id,duration,thumbnail_url,favorite_at)
                VALUES(v_owner || '_' || i,v_owner::uuid,i,p->>'title',p->>'artist_name',p->>'artist_id',p->>'album_name',p->>'album_id',(p->>'duration')::integer,p->>'thumbnail_url',p->>'favorite_at')
                ON CONFLICT(user_id,video_id) DO UPDATE SET title=excluded.title,artist_name=excluded.artist_name,favorite_at=excluded.favorite_at;
            WHEN 'user_followed_artists' THEN
                INSERT INTO public.user_followed_artists(id,user_id,channel_id,name,thumbnail_url,followed_at)
                VALUES(v_owner || '_' || i,v_owner::uuid,i,p->>'name',p->>'thumbnail_url',p->>'followed_at')
                ON CONFLICT(user_id,channel_id) DO UPDATE SET name=excluded.name,thumbnail_url=excluded.thumbnail_url,followed_at=excluded.followed_at;
            WHEN 'user_saved_albums' THEN
                INSERT INTO public.user_saved_albums(id,user_id,browse_id,title,artist_name,artist_id,thumbnail_url,track_count,favorite_at)
                VALUES(v_owner || '_' || i,v_owner::uuid,i,p->>'title',p->>'artist_name',p->>'artist_id',p->>'thumbnail_url',(p->>'track_count')::integer,p->>'favorite_at')
                ON CONFLICT(user_id,browse_id) DO UPDATE SET title=excluded.title,thumbnail_url=excluded.thumbnail_url,favorite_at=excluded.favorite_at;
            END CASE;
        END IF;
        INSERT INTO public.user_library_state(user_id,kind,item_id,enabled) VALUES(v_owner,k,i,e)
        ON CONFLICT(user_id,kind,item_id) DO UPDATE SET enabled=excluded.enabled,updated_at=now();
    END LOOP;
END $$;
REVOKE ALL ON FUNCTION public.gratify_apply_library_changes(jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.gratify_apply_library_changes(jsonb) TO authenticated;


-- Source: 202610010007_playlist_identity.sql
-- Device-local numeric IDs and titles are not safe cross-device identities.
-- Keep every historical record. Ambiguous legacy copies require explicit review.

ALTER TABLE public.cloud_playlists ADD COLUMN IF NOT EXISTS client_sync_id text;
ALTER TABLE public.shared_playlists ADD COLUMN IF NOT EXISTS client_sync_id text;
UPDATE public.cloud_playlists SET client_sync_id=id::text WHERE client_sync_id IS NULL;
UPDATE public.shared_playlists SET client_sync_id=id::text WHERE client_sync_id IS NULL;
ALTER TABLE public.cloud_playlists ALTER COLUMN client_sync_id SET DEFAULT gen_random_uuid();
ALTER TABLE public.shared_playlists ALTER COLUMN client_sync_id SET DEFAULT gen_random_uuid();
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
CREATE POLICY playlist_tombstone_read ON public.playlist_sync_tombstones FOR SELECT TO authenticated USING(user_id::text=auth.uid()::text);
CREATE POLICY playlist_tombstone_insert ON public.playlist_sync_tombstones FOR INSERT TO authenticated WITH CHECK(user_id::text=auth.uid()::text);
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
    IF EXISTS(SELECT 1 FROM public.playlist_sync_tombstones WHERE user_id::text=v_owner AND client_sync_id=p_sync_id)
    THEN RAISE EXCEPTION 'Playlist was deleted'; END IF;
    SELECT sp.id INTO v_id FROM public.shared_playlists sp WHERE sp.user_id::text=v_owner AND sp.client_sync_id=p_sync_id FOR UPDATE;
    IF v_id IS NULL THEN
        INSERT INTO public.shared_playlists(user_id,client_sync_id,title,thumbnail_url,creator_name)
        VALUES(v_owner::uuid,p_sync_id,p_title,p_thumbnail_url,p_creator_name) RETURNING id INTO v_id;
    ELSE
        UPDATE public.shared_playlists SET title=p_title,thumbnail_url=p_thumbnail_url,creator_name=p_creator_name
        WHERE id::text=v_id AND user_id::text=v_owner;
    END IF;
    DELETE FROM public.shared_playlist_tracks WHERE shared_playlist_tracks.playlist_id::text=v_id;
    INSERT INTO public.shared_playlist_tracks(playlist_id,video_id,title,artists,duration_seconds,position)
    SELECT v_id::uuid,item->>'video_id',item->>'title',item->>'artists',(item->>'duration_seconds')::integer,(ordinality-1)::integer
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
    IF EXISTS(SELECT 1 FROM public.playlist_sync_tombstones WHERE user_id::text=v_owner AND client_sync_id=p_sync_id)
    THEN RAISE EXCEPTION 'Playlist was deleted'; END IF;
    SELECT cp.id,cp.is_public INTO v_id,v_public FROM public.cloud_playlists cp
    WHERE cp.user_id::text=v_owner AND cp.client_sync_id=p_sync_id FOR UPDATE;
    v_public:=coalesce(p_is_public,v_public,false);
    IF v_id IS NULL THEN
        INSERT INTO public.cloud_playlists(user_id,client_sync_id,title,thumbnail_url,is_public)
        VALUES(v_owner::uuid,p_sync_id,p_title,p_thumbnail_url,v_public) RETURNING id INTO v_id;
    ELSE
        UPDATE public.cloud_playlists SET title=p_title,thumbnail_url=p_thumbnail_url,is_public=v_public
        WHERE id::text=v_id AND user_id::text=v_owner;
    END IF;
    DELETE FROM public.cloud_playlist_items WHERE cloud_playlist_items.playlist_id::text=v_id;
    INSERT INTO public.cloud_playlist_items(playlist_id,video_id,title,artist,duration,thumbnail_url,position)
    SELECT v_id::uuid,item->>'video_id',item->>'title',item->>'artist',(item->>'duration')::integer,
        item->>'thumbnail_url',(ordinality-1)::integer
    FROM jsonb_array_elements(p_tracks) WITH ORDINALITY AS tracks(item,ordinality);
    IF v_public THEN
        SELECT coalesce(jsonb_agg(jsonb_build_object('video_id',item->>'video_id','title',item->>'title',
            'artists',item->>'artist','duration_seconds',(item->>'duration')::integer) ORDER BY ordinality),'[]'::jsonb)
        INTO v_tracks FROM jsonb_array_elements(p_tracks) WITH ORDINALITY AS tracks(item,ordinality);
        PERFORM public.gratify_replace_shared_playlist_v2(p_sync_id,p_title,p_thumbnail_url,
            (SELECT display_name FROM public.profiles WHERE id::text=v_owner),v_tracks);
    ELSE
        DELETE FROM public.shared_playlists WHERE user_id::text=v_owner AND client_sync_id=p_sync_id;
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
    UPDATE public.cloud_playlists SET is_public=false WHERE user_id::text=v_owner AND client_sync_id=p_sync_id;
    DELETE FROM public.shared_playlists WHERE user_id::text=v_owner AND client_sync_id=p_sync_id;
END $$;

CREATE OR REPLACE FUNCTION public.gratify_delete_owned_playlist_v2(p_sync_id text) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_owner text:=auth.uid()::text;
BEGIN
    IF v_owner IS NULL THEN RAISE EXCEPTION 'Authentication required' USING ERRCODE='42501'; END IF;
    IF p_sync_id IS NULL OR length(p_sync_id)<16 OR length(p_sync_id)>128 THEN RAISE EXCEPTION 'Invalid identity'; END IF;
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_owner||':playlist:'||p_sync_id,0));
    INSERT INTO public.playlist_sync_tombstones(user_id,client_sync_id) VALUES(v_owner,p_sync_id) ON CONFLICT DO NOTHING;
    DELETE FROM public.cloud_playlists WHERE user_id::text=v_owner AND client_sync_id=p_sync_id;
    DELETE FROM public.shared_playlists WHERE user_id::text=v_owner AND client_sync_id=p_sync_id;
END $$;

-- Fail closed for old clients which still use device-local IDs for destructive operations.
-- Return a single JSON value so PostgREST's default row limit cannot truncate
-- playlists of more than 1,000 tracks. One SQL statement also preserves ordering.
CREATE OR REPLACE FUNCTION public.gratify_get_cloud_playlist_items(p_playlist_id text) RETURNS jsonb
LANGUAGE sql STABLE SECURITY INVOKER SET search_path='' AS $$
    SELECT coalesce(jsonb_agg(to_jsonb(item) ORDER BY item.position,item.id),'[]'::jsonb)
    FROM public.cloud_playlist_items item WHERE item.playlist_id::text=p_playlist_id;
$$;
CREATE OR REPLACE FUNCTION public.gratify_get_shared_playlist_tracks(p_playlist_id text) RETURNS jsonb
LANGUAGE sql STABLE SECURITY INVOKER SET search_path='' AS $$
    SELECT coalesce(jsonb_agg(to_jsonb(track) ORDER BY track.position,track.id),'[]'::jsonb)
    FROM public.shared_playlist_tracks track WHERE track.playlist_id::text=p_playlist_id;
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



DO $$ DECLARE p record; BEGIN
    IF to_regclass('public.user_follows') IS NOT NULL THEN
        ALTER TABLE public.user_follows ENABLE ROW LEVEL SECURITY;
        FOR p IN SELECT policyname FROM pg_policies WHERE schemaname='public' AND tablename='user_follows'
        LOOP EXECUTE format('DROP POLICY %I ON public.user_follows',p.policyname); END LOOP;
        CREATE POLICY legacy_follows_visible ON public.user_follows FOR SELECT USING (
            auth.uid() IN (follower_id,following_id) OR
            (coalesce((SELECT show_followers FROM public.profile_privacy WHERE user_id=follower_id::text),false)
             AND coalesce((SELECT show_followers FROM public.profile_privacy WHERE user_id=following_id::text),false)));
        CREATE POLICY legacy_follows_insert ON public.user_follows FOR INSERT TO authenticated WITH CHECK (follower_id=auth.uid());
        CREATE POLICY legacy_follows_delete ON public.user_follows FOR DELETE TO authenticated USING (follower_id=auth.uid());
        REVOKE ALL ON public.user_follows FROM PUBLIC,anon,authenticated;
        GRANT SELECT ON public.user_follows TO anon,authenticated;
        GRANT INSERT,DELETE ON public.user_follows TO authenticated;
    END IF;
END $$;

NOTIFY pgrst, 'reload schema';
COMMIT;
