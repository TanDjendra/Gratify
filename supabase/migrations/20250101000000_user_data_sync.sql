-- Supabase Migration: Per-User Cloud Sync Tables
-- Run this in your Supabase SQL Editor

-- 1. Liked Songs
CREATE TABLE IF NOT EXISTS user_liked_songs (
    id TEXT PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    video_id TEXT NOT NULL,
    title TEXT,
    artist_name TEXT,
    artist_id TEXT,
    album_name TEXT,
    album_id TEXT,
    duration INTEGER,
    thumbnail_url TEXT,
    synced_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, video_id)
);

CREATE INDEX IF NOT EXISTS idx_liked_songs_user ON user_liked_songs(user_id);

-- 2. Followed Artists
CREATE TABLE IF NOT EXISTS user_followed_artists (
    id TEXT PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    channel_id TEXT NOT NULL,
    name TEXT,
    thumbnail_url TEXT,
    synced_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, channel_id)
);

CREATE INDEX IF NOT EXISTS idx_followed_artists_user ON user_followed_artists(user_id);

-- 3. Saved Albums
CREATE TABLE IF NOT EXISTS user_saved_albums (
    id TEXT PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    browse_id TEXT NOT NULL,
    title TEXT,
    artist_name TEXT,
    artist_id TEXT,
    thumbnail_url TEXT,
    track_count INTEGER,
    synced_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, browse_id)
);

CREATE INDEX IF NOT EXISTS idx_saved_albums_user ON user_saved_albums(user_id);

-- 4. Play History
CREATE TABLE IF NOT EXISTS user_play_history (
    id TEXT PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    video_id TEXT NOT NULL,
    title TEXT,
    artist_name TEXT,
    duration INTEGER,
    thumbnail_url TEXT,
    played_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, video_id)
);

CREATE INDEX IF NOT EXISTS idx_play_history_user ON user_play_history(user_id);

-- 5. Queue
CREATE TABLE IF NOT EXISTS user_queue (
    id TEXT PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    video_id TEXT NOT NULL,
    title TEXT,
    artist_name TEXT,
    duration TEXT,
    thumbnail_url TEXT,
    position INTEGER NOT NULL DEFAULT 0,
    synced_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_queue_user ON user_queue(user_id);

-- 6. User Settings
CREATE TABLE IF NOT EXISTS user_settings (
    user_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    settings_json JSONB NOT NULL DEFAULT '{}',
    synced_at TIMESTAMPTZ DEFAULT NOW()
);

-- Row Level Security (RLS)
ALTER TABLE user_liked_songs ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_followed_artists ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_saved_albums ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_play_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_queue ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_settings ENABLE ROW LEVEL SECURITY;

-- Policies: users can only access their own data
CREATE POLICY "Users can manage their liked songs"
    ON user_liked_songs FOR ALL
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can manage their followed artists"
    ON user_followed_artists FOR ALL
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can manage their saved albums"
    ON user_saved_albums FOR ALL
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can manage their play history"
    ON user_play_history FOR ALL
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can manage their queue"
    ON user_queue FOR ALL
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can manage their settings"
    ON user_settings FOR ALL
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);
