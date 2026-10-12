"""Exercise profile pin/public-link semantics in an isolated loopback PostgreSQL."""
from pathlib import Path
import json, runpy, socket, subprocess
import psycopg
from psycopg.types.json import Jsonb

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'artifacts/music-quality'
OUT.mkdir(exist_ok=True, parents=True)
BIN = ROOT / 'artifacts/launch-audit/postgres-test-runtime/bin'
DATA = OUT / 'postgres-data'
A = '11111111-1111-4111-8111-111111111111'
B = '22222222-2222-4222-8222-222222222222'

def command(*args):
    with (OUT / 'postgres-process.log').open('a') as log:
        subprocess.run([str(a) for a in args], stdout=log, stderr=log,
            creationflags=0x08000000, timeout=60, check=True)

with socket.socket() as sock:
    sock.bind(('127.0.0.1', 0))
    port = sock.getsockname()[1]
if not (DATA / 'PG_VERSION').exists():
    command(BIN / 'initdb.exe', '-D', DATA, '-U', 'postgres', '-A', 'trust', '--no-locale', '--encoding=UTF8')
command(BIN / 'pg_ctl.exe', '-D', DATA, '-l', OUT / 'postgres.log', '-o', f'-h 127.0.0.1 -p {port}', '-w', 'start')
report = {}
try:
    for kind in ('text', 'uuid'):
        checks = []
        with psycopg.connect(host='127.0.0.1', port=port, user='postgres', dbname='postgres', autocommit=True) as db:
            db.execute('DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public; DROP SCHEMA IF EXISTS auth CASCADE; CREATE SCHEMA auth')
            for role_name in ('anon', 'authenticated'):
                if not db.execute('SELECT 1 FROM pg_roles WHERE rolname=%s', (role_name,)).fetchone():
                    db.execute(f'CREATE ROLE {role_name} NOLOGIN')
            db.execute("CREATE TABLE auth.users(id uuid PRIMARY KEY); CREATE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql STABLE AS $$ SELECT NULLIF(current_setting('request.jwt.claim.sub',true),'')::uuid $$; GRANT USAGE ON SCHEMA public,auth TO anon,authenticated; GRANT EXECUTE ON FUNCTION auth.uid() TO anon,authenticated;")
            setup = (ROOT / 'supabase_setup.sql').read_text()
            if kind == 'uuid':
                setup = runpy.run_path(str(ROOT / 'artifacts/launch-audit/uuid-schema-fixture.py'))['prepare'](setup)
            db.execute(setup)
            bundle = ROOT / f'artifacts/launch-audit/supabase-launch-fixes{"-uuid" if kind == "uuid" else ""}.sql'
            db.execute(bundle.read_text())
            db.execute('INSERT INTO auth.users VALUES(%s),(%s)', (A,B))
            db.execute("INSERT INTO public.profiles(id,display_name) VALUES(%s,'A'),(%s,'B')", (A,B))

            def role(owner=None):
                db.execute('RESET ROLE')
                db.execute("SELECT set_config('request.jwt.claim.sub',%s,false)", (owner or '',))
                db.execute('SET ROLE authenticated' if owner else 'SET ROLE anon')

            def rpc(name, *args):
                return db.execute(f'SELECT * FROM public.{name}({",".join(["%s"]*len(args))})', args)

            def fail(name, *args):
                try: rpc(name, *args)
                except psycopg.Error: return
                raise AssertionError(f'{name} accepted an invalid write')

            track = Jsonb([dict(video_id='song-one', title='Lagu', artist='Senja', duration=60, thumbnail_url=None)])
            role(A)
            rpc('gratify_replace_cloud_playlist_v2', 'legacy-public-stable-id', 'Legacy', None, True, track)
            role()
            db.execute('RESET ROLE')
            migration = (ROOT / 'supabase/migrations/202610020001_profile_playlist_pins.sql').read_text()
            db.execute(migration)
            assert db.execute('SELECT is_profile_pinned FROM public.cloud_playlists').fetchone()[0]
            checks.append('Initial migration preserves previously displayed public playlists')

            role(A)
            cloud_id = rpc('gratify_replace_cloud_playlist_v3', 'owned-playlist-stable-id', 'Dipajang', None, True, track, True).fetchone()[0]
            rows = rpc('gratify_get_profile_playlists', A).fetchone()[0]
            row = next(r for r in rows if r['client_sync_id'] == 'owned-playlist-stable-id')
            assert row['is_public'] and row['is_profile_pinned'] and row['shared_playlist_id'] != row['id']
            checks.append('Publishing and pinning commit together; profile navigation returns the shared ID')
            role(B)
            assert rpc('gratify_get_profile_playlists', A).fetchone()[0] == []
            assert db.execute('SELECT count(*) FROM public.cloud_playlist_items WHERE playlist_id::text=%s', (cloud_id,)).fetchone()[0] == 1
            checks.append('Profile privacy hides profile contents while public links keep working')
            fail('gratify_set_playlist_profile_pin', 'owned-playlist-stable-id', False)
            checks.append('A different account cannot change another owner\'s pins')

            role(A)
            rpc('gratify_set_profile_privacy', 'show_playlists', True)
            role(B)
            assert len(rpc('gratify_get_profile_playlists', A).fetchone()[0]) == 2
            role(A)
            rpc('gratify_set_playlist_profile_pin', 'owned-playlist-stable-id', False)
            assert db.execute('SELECT is_public,is_profile_pinned FROM public.cloud_playlists WHERE id::text=%s', (cloud_id,)).fetchone() == (True,False)
            role(B)
            assert len(rpc('gratify_get_profile_playlists', A).fetchone()[0]) == 1
            assert db.execute('SELECT count(*) FROM public.shared_playlist_tracks WHERE playlist_id::text=%s', (row['shared_playlist_id'],)).fetchone()[0] == 1
            checks.append('Unpinning preserves the public playlist and all shared tracks')

            role(A)
            rpc('gratify_replace_cloud_playlist_v2', 'public-unpinned-stable-id', 'Publik saja', None, True, track)
            assert not db.execute("SELECT is_profile_pinned FROM public.cloud_playlists WHERE client_sync_id='public-unpinned-stable-id'").fetchone()[0]
            checks.append('Ordinary publication never implicitly pins a new playlist')
            fail('gratify_replace_cloud_playlist_v3', 'owned-playlist-stable-id', 'Should roll back', None, True, track, None)
            assert db.execute('SELECT title FROM public.cloud_playlists WHERE id::text=%s',(cloud_id,)).fetchone()[0] == 'Dipajang'
            checks.append('A failed pin rolls back publication, title changes, and track replacement')
            rpc('gratify_set_playlist_profile_pin', 'owned-playlist-stable-id', True)
            rpc('gratify_replace_cloud_playlist_v2', 'owned-playlist-stable-id', 'Privat', None, False, track)
            assert db.execute('SELECT is_public,is_profile_pinned FROM public.cloud_playlists WHERE id::text=%s', (cloud_id,)).fetchone() == (False,False)
            fail('gratify_set_playlist_profile_pin', 'owned-playlist-stable-id', True)
            checks.append('Making a playlist private clears its pin and private playlists cannot be pinned')
            role()
            assert db.execute('SELECT count(*) FROM public.cloud_playlists WHERE id::text=%s',(cloud_id,)).fetchone()[0] == 0
            assert db.execute('SELECT count(*) FROM public.cloud_playlist_items WHERE playlist_id::text=%s',(cloud_id,)).fetchone()[0] == 0
            fail('gratify_set_playlist_profile_pin', 'public-unpinned-stable-id', True)
            checks.append('Anonymous visitors cannot see private tracks or mutate pins')
            db.execute('RESET ROLE')
            db.execute(migration)
            assert not db.execute("SELECT is_profile_pinned FROM public.cloud_playlists WHERE client_sync_id='public-unpinned-stable-id'").fetchone()[0]
            checks.append('Reapplying the migration preserves explicit unpinned choices')
            report[kind] = dict(passed=True, checks=checks)
        print(kind, 'schema:', len(checks), 'checks passed')
finally:
    command(BIN / 'pg_ctl.exe', '-D', DATA, '-m', 'fast', '-w', 'stop')
    (OUT / 'profile-pin-verification.json').write_text(json.dumps(report, indent=2))
