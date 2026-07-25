-- ====================================================================
-- GRATIFY SUPABASE DATABASE SETUP & PERMISSION FIX SCRIPT (V2)
-- ====================================================================
-- Jalankan seluruh script ini di SQL Editor pada Supabase Dashboard kamu
-- untuk melengkapi kolom yang hilang (seperti 'creator_name') 
-- dan memastikan semua fitur berbagi playlist lancar 100%.
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
CREATE POLICY "Users can insert their own profile" ON public.profiles FOR INSERT WITH CHECK (true);
DROP POLICY IF EXISTS "Users can update their own profile" ON public.profiles;
CREATE POLICY "Users can update their own profile" ON public.profiles FOR UPDATE USING (true);
GRANT ALL ON public.profiles TO authenticated, anon;


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
CREATE POLICY "Users can follow others" ON public.follows FOR INSERT WITH CHECK (true);
DROP POLICY IF EXISTS "Users can unfollow" ON public.follows;
CREATE POLICY "Users can unfollow" ON public.follows FOR DELETE USING (true);
GRANT ALL ON public.follows TO authenticated, anon;


-- 3. TABEL SHARED PLAYLISTS
CREATE TABLE IF NOT EXISTS public.shared_playlists (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    title TEXT NOT NULL,
    thumbnail_url TEXT,
    creator_name TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- Pastikan kolom creator_name ditambahkan jika tabel sudah terlanjur dibuat
ALTER TABLE public.shared_playlists ADD COLUMN IF NOT EXISTS creator_name TEXT;
ALTER TABLE public.shared_playlists ADD COLUMN IF NOT EXISTS thumbnail_url TEXT;

ALTER TABLE public.shared_playlists ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Shared playlists viewable by everyone" ON public.shared_playlists;
CREATE POLICY "Shared playlists viewable by everyone" ON public.shared_playlists FOR SELECT USING (true);
DROP POLICY IF EXISTS "Anyone authenticated can share a playlist" ON public.shared_playlists;
CREATE POLICY "Anyone authenticated can share a playlist" ON public.shared_playlists FOR INSERT WITH CHECK (true);
DROP POLICY IF EXISTS "Users can update shared playlists" ON public.shared_playlists;
CREATE POLICY "Users can update shared playlists" ON public.shared_playlists FOR UPDATE USING (true);
DROP POLICY IF EXISTS "Users can delete shared playlists" ON public.shared_playlists;
CREATE POLICY "Users can delete shared playlists" ON public.shared_playlists FOR DELETE USING (true);
GRANT ALL ON public.shared_playlists TO authenticated, anon;

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
CREATE POLICY "Saves viewable by everyone" ON public.shared_playlist_saves FOR SELECT USING (true);
DROP POLICY IF EXISTS "Users can record their own save" ON public.shared_playlist_saves;
CREATE POLICY "Users can record their own save" ON public.shared_playlist_saves FOR INSERT WITH CHECK (true);
DROP POLICY IF EXISTS "Users can remove their own save" ON public.shared_playlist_saves;
CREATE POLICY "Users can remove their own save" ON public.shared_playlist_saves FOR DELETE USING (true);
GRANT ALL ON public.shared_playlist_saves TO authenticated, anon;

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
    id TEXT PRIMARY KEY,
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

ALTER TABLE public.shared_playlist_tracks ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Shared playlist tracks viewable by everyone" ON public.shared_playlist_tracks;
CREATE POLICY "Shared playlist tracks viewable by everyone" ON public.shared_playlist_tracks FOR SELECT USING (true);
DROP POLICY IF EXISTS "Anyone can add tracks to shared playlists" ON public.shared_playlist_tracks;
CREATE POLICY "Anyone can add tracks to shared playlists" ON public.shared_playlist_tracks FOR INSERT WITH CHECK (true);
DROP POLICY IF EXISTS "Anyone can delete tracks from shared playlists" ON public.shared_playlist_tracks;
CREATE POLICY "Anyone can delete tracks from shared playlists" ON public.shared_playlist_tracks FOR DELETE USING (true);
GRANT ALL ON public.shared_playlist_tracks TO authenticated, anon;


-- 5. TABEL CLOUD PLAYLISTS & ITEMS (Pustaka Cloud)
CREATE TABLE IF NOT EXISTS public.cloud_playlists (
    id TEXT PRIMARY KEY,
    user_id TEXT,
    local_playlist_id BIGINT,
    title TEXT NOT NULL,
    description TEXT,
    thumbnail_url TEXT,
    is_public BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

ALTER TABLE public.cloud_playlists ADD COLUMN IF NOT EXISTS local_playlist_id BIGINT;
ALTER TABLE public.cloud_playlists ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE public.cloud_playlists ADD COLUMN IF NOT EXISTS is_public BOOLEAN DEFAULT true;
ALTER TABLE public.cloud_playlists ADD COLUMN IF NOT EXISTS thumbnail_url TEXT;

ALTER TABLE public.cloud_playlists ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Cloud playlists viewable by everyone" ON public.cloud_playlists;
CREATE POLICY "Cloud playlists viewable by everyone" ON public.cloud_playlists FOR SELECT USING (true);
DROP POLICY IF EXISTS "Users can manage cloud playlists" ON public.cloud_playlists;
CREATE POLICY "Users can manage cloud playlists" ON public.cloud_playlists FOR ALL USING (true);
GRANT ALL ON public.cloud_playlists TO authenticated, anon;


CREATE TABLE IF NOT EXISTS public.cloud_playlist_items (
    id TEXT PRIMARY KEY,
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

ALTER TABLE public.cloud_playlist_items ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Cloud playlist items viewable by everyone" ON public.cloud_playlist_items;
CREATE POLICY "Cloud playlist items viewable by everyone" ON public.cloud_playlist_items FOR SELECT USING (true);
DROP POLICY IF EXISTS "Users can manage cloud playlist items" ON public.cloud_playlist_items;
CREATE POLICY "Users can manage cloud playlist items" ON public.cloud_playlist_items FOR ALL USING (true);
GRANT ALL ON public.cloud_playlist_items TO authenticated, anon;


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
CREATE POLICY "User liked songs viewable by everyone" ON public.user_liked_songs FOR SELECT USING (true);
DROP POLICY IF EXISTS "Users can manage liked songs" ON public.user_liked_songs;
CREATE POLICY "Users can manage liked songs" ON public.user_liked_songs FOR ALL USING (true);
GRANT ALL ON public.user_liked_songs TO authenticated, anon;


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
CREATE POLICY "User followed artists viewable by everyone" ON public.user_followed_artists FOR SELECT USING (true);
DROP POLICY IF EXISTS "Users can manage followed artists" ON public.user_followed_artists;
CREATE POLICY "Users can manage followed artists" ON public.user_followed_artists FOR ALL USING (true);
GRANT ALL ON public.user_followed_artists TO authenticated, anon;


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
CREATE POLICY "User saved albums viewable by everyone" ON public.user_saved_albums FOR SELECT USING (true);
DROP POLICY IF EXISTS "Users can manage saved albums" ON public.user_saved_albums;
CREATE POLICY "Users can manage saved albums" ON public.user_saved_albums FOR ALL USING (true);
GRANT ALL ON public.user_saved_albums TO authenticated, anon;


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
CREATE POLICY "User play history viewable by everyone" ON public.user_play_history FOR SELECT USING (true);
DROP POLICY IF EXISTS "Users can manage play history" ON public.user_play_history;
CREATE POLICY "Users can manage play history" ON public.user_play_history FOR ALL USING (true);
GRANT ALL ON public.user_play_history TO authenticated, anon;


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
CREATE POLICY "User queue viewable by everyone" ON public.user_queue FOR SELECT USING (true);
DROP POLICY IF EXISTS "Users can manage queue" ON public.user_queue;
CREATE POLICY "Users can manage queue" ON public.user_queue FOR ALL USING (true);
GRANT ALL ON public.user_queue TO authenticated, anon;


-- 7.6 USER SETTINGS
CREATE TABLE IF NOT EXISTS public.user_settings (
    user_id TEXT PRIMARY KEY,
    settings_json TEXT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

ALTER TABLE public.user_settings ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "User settings viewable by everyone" ON public.user_settings;
CREATE POLICY "User settings viewable by everyone" ON public.user_settings FOR SELECT USING (true);
DROP POLICY IF EXISTS "Users can manage settings" ON public.user_settings;
CREATE POLICY "Users can manage settings" ON public.user_settings FOR ALL USING (true);
GRANT ALL ON public.user_settings TO authenticated, anon;


-- 7.7 REPAIR: paksa policy tabel sync jadi permisif
-- ------------------------------------------------------------------
-- DROP POLICY IF EXISTS di atas hanya menghapus policy dengan NAMA persis itu. Kalau DB
-- live masih memegang policy lama bernama lain (mis. "Users can insert their own liked
-- songs" dengan WITH CHECK auth.uid() = user_id), policy itu tetap ada dan INSERT ditolak:
--     42501 new row violates row-level security policy
-- Akibatnya syncUp tidak pernah berhasil, cloud kosong, dan pustaka user hilang begitu
-- DB lokal di-wipe saat ganti akun. Blok ini menghapus SEMUA policy di tabel sync lalu
-- membuat ulang yang permisif, jadi hasilnya tidak bergantung pada nama policy lama.
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
        FOR p IN
            SELECT policyname FROM pg_policies
            WHERE schemaname = 'public' AND tablename = t
        LOOP
            EXECUTE format('DROP POLICY %I ON public.%I', p.policyname, t);
        END LOOP;

        EXECUTE format(
            'CREATE POLICY %I ON public.%I FOR SELECT USING (true)',
            t || '_select_all', t
        );
        -- WITH CHECK wajib eksplisit: tanpa itu INSERT/UPDATE tetap divalidasi dan bisa ditolak.
        EXECUTE format(
            'CREATE POLICY %I ON public.%I FOR ALL USING (true) WITH CHECK (true)',
            t || '_manage_all', t
        );
        EXECUTE format('GRANT ALL ON public.%I TO authenticated, anon', t);
    END LOOP;
END $$;
