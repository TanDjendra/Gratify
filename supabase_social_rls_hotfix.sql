-- Jalankan di Supabase SQL Editor pada database yang sudah memakai setup lama.
-- Semua perubahan atomik. Policy lama dihapus karena policy permisif digabung
-- dengan OR; membiarkannya tetap ada akan melewati pembatasan pemilik.
-- Nilai is_public pada playlist yang sudah ada tidak diubah; tinjau data lama
-- jika sebelumnya ada playlist privat yang tersimpan sebagai publik.

BEGIN;

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

ALTER TABLE public.shared_playlists ALTER COLUMN id SET DEFAULT gen_random_uuid()::text;
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

ALTER TABLE public.shared_playlist_tracks ALTER COLUMN id SET DEFAULT gen_random_uuid()::text;
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
ALTER TABLE public.cloud_playlists ALTER COLUMN id SET DEFAULT gen_random_uuid()::text;
ALTER TABLE public.cloud_playlists ALTER COLUMN is_public SET DEFAULT false;
CREATE POLICY cloud_playlists_read_visible ON public.cloud_playlists FOR SELECT
    USING (is_public IS TRUE OR auth.uid()::text = user_id::text);
CREATE POLICY cloud_playlists_manage_owner ON public.cloud_playlists FOR ALL TO authenticated
    USING (auth.uid()::text = user_id::text)
    WITH CHECK (auth.uid()::text = user_id::text);
GRANT SELECT ON public.cloud_playlists TO anon, authenticated;
GRANT INSERT, UPDATE, DELETE ON public.cloud_playlists TO authenticated;

ALTER TABLE public.cloud_playlist_items ALTER COLUMN id SET DEFAULT gen_random_uuid()::text;
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

COMMIT;
