-- ====================================================================
-- GRATIFY SUPABASE DATABASE SETUP & PERMISSION FIX SCRIPT
-- ====================================================================
-- Jalankan seluruh script ini di SQL Editor untuk instalasi baru.
-- Untuk database yang sudah menjalankan versi lama, terapkan
-- supabase_user_data_rls_hotfix.sql dan supabase_social_rls_hotfix.sql.
-- Hotfix tersebut menghapus semua policy lama, termasuk yang namanya berbeda.
-- ====================================================================

-- 1. TABEL PROFILES
CREATE TABLE IF NOT EXISTS public.profiles (
    id TEXT PRIMARY KEY,
    display_name TEXT,
    avatar_url TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS display_name TEXT;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS avatar_url TEXT;
-- Catatan ala "Notes IG" untuk mengekspresikan lagu/mood, tampil ke teman.
-- note_updated_at menyimpan epoch millis (TEXT, seperti last_active_at) untuk kedaluwarsa 24 jam.
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS note TEXT;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS note_updated_at TEXT;

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Profiles are viewable by everyone" ON public.profiles;
CREATE POLICY "Profiles are viewable by everyone" ON public.profiles FOR SELECT USING (true);
DROP POLICY IF EXISTS "Users can insert their own profile" ON public.profiles;
CREATE POLICY "Users can insert their own profile" ON public.profiles FOR INSERT TO authenticated
    WITH CHECK (auth.uid()::text = id::text);
DROP POLICY IF EXISTS "Users can update their own profile" ON public.profiles;
CREATE POLICY "Users can update their own profile" ON public.profiles FOR UPDATE TO authenticated
    USING (auth.uid()::text = id::text)
    WITH CHECK (auth.uid()::text = id::text);
REVOKE ALL ON public.profiles FROM anon, authenticated, PUBLIC;
GRANT SELECT ON public.profiles TO anon, authenticated;
GRANT INSERT, UPDATE ON public.profiles TO authenticated;


-- 2. TABEL FOLLOWS
CREATE TABLE IF NOT EXISTS public.follows (
    follower_id TEXT NOT NULL,
    following_id TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    PRIMARY KEY (follower_id, following_id)
);

ALTER TABLE public.follows ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Follows are viewable by everyone" ON public.follows;
CREATE POLICY "Follows are viewable by everyone" ON public.follows FOR SELECT USING (true);
DROP POLICY IF EXISTS "Users can follow others" ON public.follows;
CREATE POLICY "Users can follow others" ON public.follows FOR INSERT TO authenticated
    WITH CHECK (auth.uid()::text = follower_id::text);
DROP POLICY IF EXISTS "Users can unfollow" ON public.follows;
CREATE POLICY "Users can unfollow" ON public.follows FOR DELETE TO authenticated
    USING (auth.uid()::text = follower_id::text);
REVOKE ALL ON public.follows FROM anon, authenticated, PUBLIC;
GRANT SELECT ON public.follows TO anon, authenticated;
GRANT INSERT, DELETE ON public.follows TO authenticated;


-- 3. TABEL SHARED PLAYLISTS
CREATE TABLE IF NOT EXISTS public.shared_playlists (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id TEXT NOT NULL,
    title TEXT NOT NULL,
    thumbnail_url TEXT,
    creator_name TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- Pastikan kolom creator_name ditambahkan jika tabel sudah terlanjur dibuat
ALTER TABLE public.shared_playlists ADD COLUMN IF NOT EXISTS creator_name TEXT;
ALTER TABLE public.shared_playlists ADD COLUMN IF NOT EXISTS thumbnail_url TEXT;
ALTER TABLE public.shared_playlists ALTER COLUMN id SET DEFAULT gen_random_uuid()::text;

ALTER TABLE public.shared_playlists ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Shared playlists viewable by everyone" ON public.shared_playlists;
CREATE POLICY "Shared playlists viewable by everyone" ON public.shared_playlists FOR SELECT USING (true);
DROP POLICY IF EXISTS "Anyone authenticated can share a playlist" ON public.shared_playlists;
CREATE POLICY "Anyone authenticated can share a playlist" ON public.shared_playlists FOR INSERT TO authenticated
    WITH CHECK (auth.uid()::text = user_id::text);
DROP POLICY IF EXISTS "Users can update shared playlists" ON public.shared_playlists;
CREATE POLICY "Users can update shared playlists" ON public.shared_playlists FOR UPDATE TO authenticated
    USING (auth.uid()::text = user_id::text)
    WITH CHECK (auth.uid()::text = user_id::text);
DROP POLICY IF EXISTS "Users can delete shared playlists" ON public.shared_playlists;
CREATE POLICY "Users can delete shared playlists" ON public.shared_playlists FOR DELETE TO authenticated
    USING (auth.uid()::text = user_id::text);
REVOKE ALL ON public.shared_playlists FROM anon, authenticated, PUBLIC;
GRANT SELECT ON public.shared_playlists TO anon, authenticated;
GRANT INSERT (user_id, title, thumbnail_url, creator_name) ON public.shared_playlists TO authenticated;
GRANT UPDATE (title, thumbnail_url, creator_name) ON public.shared_playlists TO authenticated;
GRANT DELETE ON public.shared_playlists TO authenticated;

-- ============================================================================
-- Hitungan "ditambahkan X kali" (unik per user, seperti Spotify)
-- ----------------------------------------------------------------------------
-- Kolom penghitung di shared_playlists (dijaga otomatis oleh trigger di bawah).
ALTER TABLE public.shared_playlists ADD COLUMN IF NOT EXISTS add_count INTEGER NOT NULL DEFAULT 0;

-- Tabel catatan siapa saja yang menambahkan playlist ke pustakanya.
-- UNIQUE(playlist_id, user_id) memastikan tiap user hanya dihitung satu kali.
CREATE TABLE IF NOT EXISTS public.shared_playlist_saves (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    playlist_id TEXT NOT NULL REFERENCES public.shared_playlists(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    UNIQUE (playlist_id, user_id)
);

ALTER TABLE public.shared_playlist_saves ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Saves viewable by everyone" ON public.shared_playlist_saves;
DROP POLICY IF EXISTS "Users can view their own saves" ON public.shared_playlist_saves;
CREATE POLICY "Users can view their own saves" ON public.shared_playlist_saves FOR SELECT TO authenticated
    USING (auth.uid()::text = user_id::text);
DROP POLICY IF EXISTS "Users can record their own save" ON public.shared_playlist_saves;
CREATE POLICY "Users can record their own save" ON public.shared_playlist_saves FOR INSERT TO authenticated
    WITH CHECK (auth.uid()::text = user_id::text);
DROP POLICY IF EXISTS "Users can remove their own save" ON public.shared_playlist_saves;
CREATE POLICY "Users can remove their own save" ON public.shared_playlist_saves FOR DELETE TO authenticated
    USING (auth.uid()::text = user_id::text);
REVOKE ALL ON public.shared_playlist_saves FROM anon, authenticated, PUBLIC;
GRANT SELECT ON public.shared_playlist_saves TO authenticated;
GRANT INSERT, DELETE ON public.shared_playlist_saves TO authenticated;

-- Trigger menjaga shared_playlists.add_count tetap sinkron dengan jumlah baris saves.
CREATE OR REPLACE FUNCTION public.sync_shared_playlist_add_count()
RETURNS TRIGGER AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN
        UPDATE public.shared_playlists
            SET add_count = add_count + 1
            WHERE id = NEW.playlist_id;
    ELSIF (TG_OP = 'DELETE') THEN
        UPDATE public.shared_playlists
            SET add_count = GREATEST(add_count - 1, 0)
            WHERE id = OLD.playlist_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_shared_playlist_saves_count ON public.shared_playlist_saves;
CREATE TRIGGER trg_shared_playlist_saves_count
    AFTER INSERT OR DELETE ON public.shared_playlist_saves
    FOR EACH ROW EXECUTE FUNCTION public.sync_shared_playlist_add_count();

-- Backfill agar add_count konsisten dengan data yang mungkin sudah ada.
UPDATE public.shared_playlists sp
    SET add_count = (
        SELECT COUNT(*) FROM public.shared_playlist_saves s WHERE s.playlist_id = sp.id
    );


-- 4. TABEL SHARED PLAYLIST TRACKS
CREATE TABLE IF NOT EXISTS public.shared_playlist_tracks (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    playlist_id TEXT REFERENCES public.shared_playlists(id) ON DELETE CASCADE,
    video_id TEXT NOT NULL,
    title TEXT NOT NULL,
    artists TEXT NOT NULL,
    thumbnail_url TEXT,
    duration_seconds INTEGER,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

ALTER TABLE public.shared_playlist_tracks ADD COLUMN IF NOT EXISTS video_id TEXT;
ALTER TABLE public.shared_playlist_tracks ADD COLUMN IF NOT EXISTS artists TEXT;
ALTER TABLE public.shared_playlist_tracks ADD COLUMN IF NOT EXISTS duration_seconds INTEGER;
ALTER TABLE public.shared_playlist_tracks ADD COLUMN IF NOT EXISTS thumbnail_url TEXT;
ALTER TABLE public.shared_playlist_tracks ALTER COLUMN id SET DEFAULT gen_random_uuid()::text;

ALTER TABLE public.shared_playlist_tracks ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Shared playlist tracks viewable by everyone" ON public.shared_playlist_tracks;
CREATE POLICY "Shared playlist tracks viewable by everyone" ON public.shared_playlist_tracks FOR SELECT USING (true);
DROP POLICY IF EXISTS "Anyone can add tracks to shared playlists" ON public.shared_playlist_tracks;
CREATE POLICY "Anyone can add tracks to shared playlists" ON public.shared_playlist_tracks FOR INSERT TO authenticated
    WITH CHECK (EXISTS (
        SELECT 1 FROM public.shared_playlists sp
        WHERE sp.id = playlist_id AND sp.user_id::text = auth.uid()::text
    ));
DROP POLICY IF EXISTS "Anyone can delete tracks from shared playlists" ON public.shared_playlist_tracks;
CREATE POLICY "Anyone can delete tracks from shared playlists" ON public.shared_playlist_tracks FOR DELETE TO authenticated
    USING (EXISTS (
        SELECT 1 FROM public.shared_playlists sp
        WHERE sp.id = playlist_id AND sp.user_id::text = auth.uid()::text
    ));
REVOKE ALL ON public.shared_playlist_tracks FROM anon, authenticated, PUBLIC;
GRANT SELECT ON public.shared_playlist_tracks TO anon, authenticated;
GRANT INSERT, DELETE ON public.shared_playlist_tracks TO authenticated;


-- 5. TABEL CLOUD PLAYLISTS & ITEMS (Pustaka Cloud)
CREATE TABLE IF NOT EXISTS public.cloud_playlists (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id TEXT,
    local_playlist_id BIGINT,
    title TEXT NOT NULL,
    description TEXT,
    thumbnail_url TEXT,
    is_public BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

ALTER TABLE public.cloud_playlists ADD COLUMN IF NOT EXISTS local_playlist_id BIGINT;
ALTER TABLE public.cloud_playlists ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE public.cloud_playlists ADD COLUMN IF NOT EXISTS is_public BOOLEAN DEFAULT false;
ALTER TABLE public.cloud_playlists ADD COLUMN IF NOT EXISTS thumbnail_url TEXT;
ALTER TABLE public.cloud_playlists ALTER COLUMN id SET DEFAULT gen_random_uuid()::text;
ALTER TABLE public.cloud_playlists ALTER COLUMN is_public SET DEFAULT false;

ALTER TABLE public.cloud_playlists ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Cloud playlists viewable by everyone" ON public.cloud_playlists;
CREATE POLICY "Cloud playlists viewable by everyone" ON public.cloud_playlists FOR SELECT
    USING (is_public IS TRUE OR auth.uid()::text = user_id::text);
DROP POLICY IF EXISTS "Users can manage cloud playlists" ON public.cloud_playlists;
CREATE POLICY "Users can manage cloud playlists" ON public.cloud_playlists FOR ALL TO authenticated
    USING (auth.uid()::text = user_id::text)
    WITH CHECK (auth.uid()::text = user_id::text);
REVOKE ALL ON public.cloud_playlists FROM anon, authenticated, PUBLIC;
GRANT SELECT ON public.cloud_playlists TO anon, authenticated;
GRANT INSERT, UPDATE, DELETE ON public.cloud_playlists TO authenticated;


CREATE TABLE IF NOT EXISTS public.cloud_playlist_items (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    playlist_id TEXT REFERENCES public.cloud_playlists(id) ON DELETE CASCADE,
    video_id TEXT NOT NULL,
    title TEXT NOT NULL,
    artist TEXT NOT NULL,
    duration INTEGER DEFAULT 0,
    thumbnail_url TEXT,
    position INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

ALTER TABLE public.cloud_playlist_items ADD COLUMN IF NOT EXISTS video_id TEXT;
ALTER TABLE public.cloud_playlist_items ADD COLUMN IF NOT EXISTS position INTEGER DEFAULT 0;
ALTER TABLE public.cloud_playlist_items ADD COLUMN IF NOT EXISTS duration INTEGER DEFAULT 0;
ALTER TABLE public.cloud_playlist_items ADD COLUMN IF NOT EXISTS thumbnail_url TEXT;
ALTER TABLE public.cloud_playlist_items ALTER COLUMN id SET DEFAULT gen_random_uuid()::text;

ALTER TABLE public.cloud_playlist_items ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Cloud playlist items viewable by everyone" ON public.cloud_playlist_items;
CREATE POLICY "Cloud playlist items viewable by everyone" ON public.cloud_playlist_items FOR SELECT
    USING (EXISTS (
        SELECT 1 FROM public.cloud_playlists cp
        WHERE cp.id = playlist_id AND (cp.is_public IS TRUE OR cp.user_id::text = auth.uid()::text)
    ));
DROP POLICY IF EXISTS "Users can manage cloud playlist items" ON public.cloud_playlist_items;
CREATE POLICY "Users can manage cloud playlist items" ON public.cloud_playlist_items FOR ALL TO authenticated
    USING (EXISTS (
        SELECT 1 FROM public.cloud_playlists cp
        WHERE cp.id = playlist_id AND cp.user_id::text = auth.uid()::text
    ))
    WITH CHECK (EXISTS (
        SELECT 1 FROM public.cloud_playlists cp
        WHERE cp.id = playlist_id AND cp.user_id::text = auth.uid()::text
    ));
REVOKE ALL ON public.cloud_playlist_items FROM anon, authenticated, PUBLIC;
GRANT SELECT ON public.cloud_playlist_items TO anon, authenticated;
GRANT INSERT, UPDATE, DELETE ON public.cloud_playlist_items TO authenticated;


-- ====================================================================
-- 7. USER DATA SYNC TABLES (Per-User Cloud Backup)
-- ====================================================================

-- 7.1 USER LIKED SONGS
CREATE TABLE IF NOT EXISTS public.user_liked_songs (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    video_id TEXT NOT NULL,
    title TEXT,
    artist_name TEXT,
    artist_id TEXT,
    album_name TEXT,
    album_id TEXT,
    duration INTEGER,
    thumbnail_url TEXT,
    favorite_at TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    UNIQUE(user_id, video_id)
);

ALTER TABLE public.user_liked_songs ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "User liked songs viewable by everyone" ON public.user_liked_songs;
DROP POLICY IF EXISTS "Users can manage liked songs" ON public.user_liked_songs;
CREATE POLICY "Users can manage liked songs" ON public.user_liked_songs FOR ALL TO authenticated
    USING (auth.uid()::text = user_id::text)
    WITH CHECK (auth.uid()::text = user_id::text);
REVOKE ALL ON public.user_liked_songs FROM anon, authenticated, PUBLIC;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.user_liked_songs TO authenticated;


-- 7.2 USER FOLLOWED ARTISTS
CREATE TABLE IF NOT EXISTS public.user_followed_artists (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    channel_id TEXT NOT NULL,
    name TEXT,
    thumbnail_url TEXT,
    followed_at TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    UNIQUE(user_id, channel_id)
);

ALTER TABLE public.user_followed_artists ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "User followed artists viewable by everyone" ON public.user_followed_artists;
DROP POLICY IF EXISTS "Users can manage followed artists" ON public.user_followed_artists;
CREATE POLICY "Users can manage followed artists" ON public.user_followed_artists FOR ALL TO authenticated
    USING (auth.uid()::text = user_id::text)
    WITH CHECK (auth.uid()::text = user_id::text);
REVOKE ALL ON public.user_followed_artists FROM anon, authenticated, PUBLIC;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.user_followed_artists TO authenticated;


-- 7.3 USER SAVED ALBUMS
CREATE TABLE IF NOT EXISTS public.user_saved_albums (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    browse_id TEXT NOT NULL,
    title TEXT,
    artist_name TEXT,
    artist_id TEXT,
    thumbnail_url TEXT,
    track_count INTEGER,
    favorite_at TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    UNIQUE(user_id, browse_id)
);

ALTER TABLE public.user_saved_albums ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "User saved albums viewable by everyone" ON public.user_saved_albums;
DROP POLICY IF EXISTS "Users can manage saved albums" ON public.user_saved_albums;
CREATE POLICY "Users can manage saved albums" ON public.user_saved_albums FOR ALL TO authenticated
    USING (auth.uid()::text = user_id::text)
    WITH CHECK (auth.uid()::text = user_id::text);
REVOKE ALL ON public.user_saved_albums FROM anon, authenticated, PUBLIC;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.user_saved_albums TO authenticated;


-- 7.4 USER PLAY HISTORY
CREATE TABLE IF NOT EXISTS public.user_play_history (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    video_id TEXT NOT NULL,
    title TEXT,
    artist_name TEXT,
    duration INTEGER,
    thumbnail_url TEXT,
    played_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

ALTER TABLE public.user_play_history ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "User play history viewable by everyone" ON public.user_play_history;
DROP POLICY IF EXISTS "Users can manage play history" ON public.user_play_history;
CREATE POLICY "Users can manage play history" ON public.user_play_history FOR ALL TO authenticated
    USING (auth.uid()::text = user_id::text)
    WITH CHECK (auth.uid()::text = user_id::text);
REVOKE ALL ON public.user_play_history FROM anon, authenticated, PUBLIC;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.user_play_history TO authenticated;


-- 7.5 USER QUEUE
CREATE TABLE IF NOT EXISTS public.user_queue (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    video_id TEXT NOT NULL,
    title TEXT,
    artist_name TEXT,
    duration INTEGER,
    thumbnail_url TEXT,
    position INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

ALTER TABLE public.user_queue ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "User queue viewable by everyone" ON public.user_queue;
DROP POLICY IF EXISTS "Users can manage queue" ON public.user_queue;
CREATE POLICY "Users can manage queue" ON public.user_queue FOR ALL TO authenticated
    USING (auth.uid()::text = user_id::text)
    WITH CHECK (auth.uid()::text = user_id::text);
REVOKE ALL ON public.user_queue FROM anon, authenticated, PUBLIC;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.user_queue TO authenticated;


-- 7.6 USER SETTINGS
CREATE TABLE IF NOT EXISTS public.user_settings (
    user_id TEXT PRIMARY KEY,
    settings_json TEXT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

ALTER TABLE public.user_settings ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "User settings viewable by everyone" ON public.user_settings;
DROP POLICY IF EXISTS "Users can manage settings" ON public.user_settings;
CREATE POLICY "Users can manage settings" ON public.user_settings FOR ALL TO authenticated
    USING (auth.uid()::text = user_id::text)
    WITH CHECK (auth.uid()::text = user_id::text);
REVOKE ALL ON public.user_settings FROM anon, authenticated, PUBLIC;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.user_settings TO authenticated;


-- 7.7 REPAIR: ganti semua policy lama dengan pembatasan per pengguna
-- ------------------------------------------------------------------
-- Policy PostgreSQL yang permisif digabung dengan OR. Karena itu semua policy lama,
-- termasuk yang namanya berbeda, harus dihapus agar tidak membuka data pengguna lain.
-- Cast ke text mendukung instalasi lama dengan user_id TEXT dan migrasi dengan UUID.
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
        FOR p IN
            SELECT policyname FROM pg_policies
            WHERE schemaname = 'public' AND tablename = t
        LOOP
            EXECUTE format('DROP POLICY %I ON public.%I', p.policyname, t);
        END LOOP;

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
