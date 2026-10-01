"""Isolated fixture matching the types and FK dependencies observed in production."""
import re

def prepare(sql):
    columns = {
        'profiles': ['id'], 'cloud_playlists': ['id', 'user_id'],
        'cloud_playlist_items': ['id', 'playlist_id'],
        'shared_playlists': ['id', 'user_id'], 'shared_playlist_tracks': ['id', 'playlist_id'],
        'shared_playlist_saves': ['id', 'playlist_id'],
        'user_liked_songs': ['user_id'], 'user_followed_artists': ['user_id'],
        'user_saved_albums': ['user_id'], 'user_play_history': ['user_id'],
        'user_queue': ['user_id'], 'user_settings': ['user_id'],
    }
    for table, names in columns.items():
        pattern = rf'(CREATE TABLE IF NOT EXISTS public\.{table} \()(.*?)(\n\);)'
        def convert(match):
            body = match[2]
            for name in names:
                body = re.sub(rf'(?m)^(\s*{name}\s+)TEXT\b', r'\1UUID', body)
            return match[1] + body + match[3]
        sql, count = re.subn(pattern, convert, sql, flags=re.S)
        assert count == 1, table
    sql = sql.replace('gen_random_uuid()::text', 'gen_random_uuid()')
    # Production has only PKs on these tables; the deployment adds owner/item indexes.
    sql = re.sub(r',\s*UNIQUE\(user_id, (video_id|channel_id|browse_id)\)', '', sql)
    for table in columns:
        if table not in ('profiles', 'cloud_playlist_items', 'shared_playlist_tracks', 'shared_playlist_saves'):
            sql += f'\nALTER TABLE public.{table} ADD FOREIGN KEY(user_id) REFERENCES auth.users(id) ON DELETE CASCADE;'
    sql += '\nALTER TABLE public.profiles ADD FOREIGN KEY(id) REFERENCES auth.users(id) ON DELETE CASCADE;'
    sql += '''
CREATE TABLE public.user_follows(follower_id uuid REFERENCES auth.users(id) ON DELETE CASCADE,
    following_id uuid REFERENCES auth.users(id) ON DELETE CASCADE, PRIMARY KEY(follower_id,following_id));
GRANT SELECT,INSERT,DELETE ON public.user_follows TO anon,authenticated;
'''
    return sql
