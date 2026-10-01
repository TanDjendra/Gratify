"""Compile a reviewed variant for the UUID schema observed on Gratify production.

Preserves UUID PK/FK types and every existing row. No data conversion or deduplication.
"""
from pathlib import Path
import re
from pglast import parser

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'artifacts/launch-audit'
source = (OUT / 'supabase-launch-fixes.sql').read_text()
# Preflight accepts exactly the observed live UUID columns; shared saves remain TEXT owners.
uuid_columns = {
    'cloud_playlists': ['id', 'user_id'], 'cloud_playlist_items': ['playlist_id'],
    'shared_playlists': ['id', 'user_id'], 'shared_playlist_tracks': ['playlist_id'],
    'user_liked_songs': ['user_id'], 'user_followed_artists': ['user_id'],
    'user_saved_albums': ['user_id'], 'user_play_history': ['user_id'],
    'user_queue': ['user_id'], 'user_settings': ['user_id'],
}
for table, columns in uuid_columns.items():
    for col in columns:
        source = source.replace(f"table_name='{table}' AND column_name='{col}' AND data_type='text'",
                                f"table_name='{table}' AND column_name='{col}' AND data_type='uuid'")
        source = source.replace(f'{table}.{col} expected text', f'{table}.{col} expected uuid')
# UUID defaults work for both the existing UUID identifiers and new TEXT sync identifiers.
source = source.replace('SET DEFAULT gen_random_uuid()::text', 'SET DEFAULT gen_random_uuid()')
# Existing function inputs and return values remain TEXT at the API boundary.
source = re.sub(r'(?<![\w:])(user_id)(?![\w:]|\s+text)(\s*=)', r'\1::text\2', source)
source = re.sub(r'(?<![\w:])(sp\.user_id|cp\.user_id|shared_playlists\.user_id|cloud_playlists\.user_id)(?![\w:])(\s*=)', r'\1::text\2', source)
source = source.replace('user_id=shared_playlists.user_id', 'user_id::text=shared_playlists.user_id::text')
source = source.replace('user_id=cloud_playlists.user_id', 'user_id::text=cloud_playlists.user_id::text')
# The previous regex already casts the left side in these expressions.
source = source.replace('user_id::text=shared_playlists.user_id)', 'user_id::text=shared_playlists.user_id::text)')
source = source.replace('user_id::text=cloud_playlists.user_id)', 'user_id::text=cloud_playlists.user_id::text)')
source = source.replace('sp.id=p_playlist_id', 'sp.id::text=p_playlist_id')
source = source.replace('item.playlist_id=p_playlist_id', 'item.playlist_id::text=p_playlist_id')
source = source.replace('track.playlist_id=p_playlist_id', 'track.playlist_id::text=p_playlist_id')
source = re.sub(r'(?<![\w:])(id|sp\.id|cp\.id|playlist_id|shared_playlist_tracks\.playlist_id|cloud_playlist_items\.playlist_id)(\s*=v_(?:id|shared))', r'\1::text\2', source)
source = source.replace('SET client_sync_id=id WHERE', 'SET client_sync_id=id::text WHERE')
# Cast only insertion values whose actual target columns are UUID.
source = source.replace('VALUES(v_owner,v_title,', 'VALUES(v_owner::uuid,v_title,')
source = source.replace('VALUES(v_owner,p_local_playlist_id,', 'VALUES(v_owner::uuid,p_local_playlist_id,')
source = source.replace('VALUES(v_owner,p_sync_id,p_title,', 'VALUES(v_owner::uuid,p_sync_id,p_title,')
source = source.replace('SELECT v_id,', 'SELECT v_id::uuid,')
source = source.replace("(ordinality-1),v_owner,item->>'video_id'", "(ordinality-1),v_owner::uuid,item->>'video_id'")
source = source.replace("|| i,v_owner,i,", "|| i,v_owner::uuid,i,")
# ON CONFLICT requires actual unique constraints; fail safely on historical duplicates.
indexes = '''
CREATE UNIQUE INDEX IF NOT EXISTS gratify_liked_owner_video ON public.user_liked_songs(user_id,video_id);
CREATE UNIQUE INDEX IF NOT EXISTS gratify_artist_owner_channel ON public.user_followed_artists(user_id,channel_id);
CREATE UNIQUE INDEX IF NOT EXISTS gratify_album_owner_browse ON public.user_saved_albums(user_id,browse_id);
'''
source = source.replace('-- Source: 202610010006_library_changes.sql', indexes + '\n-- Source: 202610010006_library_changes.sql')
# The legacy UUID follow table must not bypass the current follow privacy controls.
legacy = '''
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
'''
source = source.replace("NOTIFY pgrst, 'reload schema';", legacy + "\nNOTIFY pgrst, 'reload schema';")
source = source.replace('Prepared for review; not evidence of live deployment.', 'UUID production variant; preserves existing UUID primary and foreign keys.')
parser.parse_sql(source)
parser.parse_plpgsql_json(source)
(OUT / 'supabase-launch-fixes-uuid.sql').write_text(source)
print('UUID bundle prepared and syntax validated; no rows removed or identifier types converted.')
