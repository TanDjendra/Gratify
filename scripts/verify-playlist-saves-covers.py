"""Real isolated PostgreSQL tests for TEXT/UUID schemas, RLS and save concurrency."""
from pathlib import Path
import json, runpy, socket, subprocess
from concurrent.futures import ThreadPoolExecutor
import psycopg
from psycopg.types.json import Jsonb

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'artifacts/playlist-audit-2026-10-03'
OUT.mkdir(parents=True,exist_ok=True)
BIN=ROOT/'artifacts/launch-audit/postgres-test-runtime/bin'
DATA=OUT/'postgres-data'
A='11111111-1111-4111-8111-111111111111'
B='22222222-2222-4222-8222-222222222222'
C='33333333-3333-4333-8333-333333333333'
def command(*args):
    with (OUT/'postgres-process.log').open('a') as log:
        subprocess.run([str(x) for x in args],stdout=log,stderr=log,creationflags=0x08000000,timeout=60,check=True)
with socket.socket() as sock:
    sock.bind(('127.0.0.1',0)); port=sock.getsockname()[1]
if not (DATA/'PG_VERSION').exists():
    command(BIN/'initdb.exe','-D',DATA,'-U','postgres','-A','trust','--no-locale','--encoding=UTF8')
command(BIN/'pg_ctl.exe','-D',DATA,'-l',OUT/'postgres.log','-o',f'-h 127.0.0.1 -p {port}','-w','start')
report={}
try:
    for kind in ('text','uuid'):
        checks=[]
        with psycopg.connect(host='127.0.0.1',port=port,user='postgres',dbname='postgres',autocommit=True) as db:
            db.execute('DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public; DROP SCHEMA IF EXISTS auth CASCADE; CREATE SCHEMA auth; DROP SCHEMA IF EXISTS storage CASCADE; CREATE SCHEMA storage')
            for role in ('anon','authenticated'):
                if not db.execute('SELECT 1 FROM pg_roles WHERE rolname=%s',(role,)).fetchone(): db.execute(f'CREATE ROLE {role} NOLOGIN')
            db.execute("CREATE TABLE auth.users(id uuid PRIMARY KEY); CREATE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql STABLE AS $$ SELECT NULLIF(current_setting('request.jwt.claim.sub',true),'')::uuid $$; GRANT USAGE ON SCHEMA public,auth,storage TO anon,authenticated; GRANT EXECUTE ON FUNCTION auth.uid() TO anon,authenticated")
            setup=(ROOT/'supabase_setup.sql').read_text()
            if kind=='uuid': setup=runpy.run_path(str(ROOT/'artifacts/launch-audit/uuid-schema-fixture.py'))['prepare'](setup)
            db.execute(setup)
            db.execute((ROOT/f'artifacts/launch-audit/supabase-launch-fixes{"-uuid" if kind=="uuid" else ""}.sql').read_text())
            db.execute((ROOT/'supabase/migrations/202610020001_profile_playlist_pins.sql').read_text())
            db.execute('CREATE TABLE storage.buckets(id text PRIMARY KEY,name text,public boolean,file_size_limit bigint,allowed_mime_types text[]); CREATE TABLE storage.objects(bucket_id text,name text,PRIMARY KEY(bucket_id,name)); ALTER TABLE storage.objects ENABLE ROW LEVEL SECURITY; GRANT SELECT,INSERT,DELETE ON storage.objects TO anon,authenticated')
            migration=(ROOT/'supabase/migrations/202610030002_playlist_saves_covers.sql').read_text()
            db.execute(migration); checks.append('Migration applies atomically on '+kind)
            db.execute('INSERT INTO auth.users VALUES(%s),(%s),(%s)',(A,B,C))
            db.execute("INSERT INTO profiles(id,display_name) VALUES(%s,'Owner'),(%s,'Listener'),(%s,'Other')",(A,B,C))
            def role(owner=None):
                db.execute('RESET ROLE'); db.execute("SELECT set_config('request.jwt.claim.sub',%s,false)",(owner or '',)); db.execute('SET ROLE authenticated' if owner else 'SET ROLE anon')
            def rpc(name,*args): return db.execute(f'SELECT * FROM public.{name}({",".join(["%s"]*len(args))})',args).fetchone()[0]
            def rejected(action):
                try: action()
                except psycopg.Error: return
                raise AssertionError('Invalid operation accepted')
            sync='playlist-stable-identity-one'
            path=f'{A}/{sync}/cover.jpg'
            thumb='https://example.test/storage/v1/object/public/playlist-covers/'+path
            tracks=Jsonb([dict(video_id='song',title='Song',artist='Artist',duration=60,thumbnail_url=None)])
            role(A); cloud=rpc('gratify_replace_cloud_playlist_v3',sync,'Playlist',thumb,True,tracks,True)
            source=db.execute('SELECT id::text FROM shared_playlists WHERE client_sync_id=%s',(sync,)).fetchone()[0]
            db.execute('INSERT INTO storage.objects VALUES(%s,%s)',('playlist-covers',path)); checks.append('Owner uploads cover to their identity path')
            rejected(lambda: rpc('gratify_set_playlist_saved',source,True,A)); checks.append('Owner cannot inflate own save count')
            role(B); first=rpc('gratify_set_playlist_saved',source,True,B); assert first['add_count']==1
            assert rpc('gratify_set_playlist_saved',source,True,B)['add_count']==1; checks.append('Repeated save counts once')
            db.execute('INSERT INTO shared_playlist_saves(playlist_id,user_id) VALUES(%s,%s) ON CONFLICT(playlist_id,user_id) DO NOTHING',(source,B))
            assert db.execute('SELECT add_count FROM shared_playlists WHERE id::text=%s',(source,)).fetchone()[0]==1; checks.append('Legacy APK idempotent save remains supported under stricter RLS')
            assert len(rpc('gratify_get_saved_playlists'))==1; checks.append('Saved playlists can be restored on another device')
            assert db.execute('SELECT count(*) FROM storage.objects').fetchone()[0]==1; checks.append('Other user can read public cover')
            rejected(lambda: db.execute('INSERT INTO storage.objects VALUES(%s,%s)',('playlist-covers',f'{A}/{sync}/foreign.jpg'))); checks.append('Other user cannot overwrite owner cover')
            rejected(lambda: rpc('gratify_set_playlist_saved',source,True,A)); checks.append('Account change during request is rejected')
            rejected(lambda: db.execute('INSERT INTO shared_playlist_saves(playlist_id,user_id) VALUES(%s,%s)',(source,C))); checks.append('Direct REST insert cannot bypass save validation')
            role(C); assert rpc('gratify_set_playlist_saved',source,True,C)['add_count']==2
            assert len(db.execute('SELECT * FROM shared_playlist_saves').fetchall())==1; checks.append('Save identities remain private to their user')
            role(); assert db.execute('SELECT count(*) FROM storage.objects').fetchone()[0]==1
            rejected(lambda: rpc('gratify_set_playlist_saved',source,True,B)); checks.append('Anonymous read works while anonymous mutation is blocked')
            role(A); rpc('gratify_hide_owned_playlist_v2',sync)
            assert db.execute('SELECT id::text,add_count FROM shared_playlists WHERE client_sync_id=%s',(sync,)).fetchone()==(source,2); checks.append('Making private preserves ID and existing saves')
            assert db.execute('SELECT count(*) FROM storage.objects').fetchone()[0]==1; checks.append('Private cover remains visible to owner')
            role(B); assert not db.execute('SELECT id FROM shared_playlists WHERE id::text=%s',(source,)).fetchall()
            assert db.execute('SELECT count(*) FROM storage.objects').fetchone()[0]==0
            assert rpc('gratify_get_shared_playlist_tracks',source)==[]; checks.append('Private metadata, tracks and cover hidden from others')
            rejected(lambda: rpc('gratify_set_playlist_saved',source,True,B)); checks.append('Private source cannot acquire new saves')
            assert rpc('gratify_set_playlist_saved',source,False,B)['add_count']==1; checks.append('User can unsave a now-private source')
            assert rpc('gratify_set_playlist_saved',source,False,B)['add_count']==1; checks.append('Repeated removal never decrements twice')
            role(A); rpc('gratify_replace_cloud_playlist_v3',sync,'Updated',thumb,True,tracks,True)
            assert db.execute('SELECT id::text,add_count FROM shared_playlists WHERE client_sync_id=%s',(sync,)).fetchone()==(source,1); checks.append('Republish preserves identity, remaining saves and updates metadata')
            rpc('gratify_set_profile_privacy','show_playlists',True)
            role(B); assert rpc('gratify_get_profile_playlists',A)[0]['add_count']==1; checks.append('Profile exposes authoritative aggregate count')
            def concurrent(_):
                with psycopg.connect(host='127.0.0.1',port=port,user='postgres',dbname='postgres',autocommit=True) as other:
                    other.execute("SELECT set_config('request.jwt.claim.sub',%s,false)",(B,)); other.execute('SET ROLE authenticated')
                    return other.execute('SELECT gratify_set_playlist_saved(%s,true,%s)',(source,B)).fetchone()[0]
            with ThreadPoolExecutor(max_workers=8) as pool: assert all(r['add_count']==2 for r in pool.map(concurrent,range(16)))
            checks.append('16 concurrent saves from one account count once')
            role(A); rpc('gratify_replace_cloud_playlist_v2',sync,'Private again',thumb,False,tracks)
            assert db.execute('SELECT id::text FROM shared_playlists WHERE client_sync_id=%s',(sync,)).fetchone()[0]==source; checks.append('Ordinary private cloud backup preserves shared identity too')
            db.execute('RESET ROLE'); db.execute(migration)
            assert db.execute('SELECT add_count FROM shared_playlists WHERE id::text=%s',(source,)).fetchone()[0]==2; checks.append('Reapply/backfill preserves accurate counts')
            assert not db.execute("SELECT public FROM storage.buckets WHERE id='playlist-covers'").fetchone()[0]; checks.append('Cover bucket never permits unauthenticated public object URLs')
            role(A); rpc('gratify_delete_owned_playlist_v2',sync)
            assert not db.execute('SELECT id FROM shared_playlists WHERE id::text=%s',(source,)).fetchall(); checks.append('Explicit delete still removes source permanently')
            report[kind]={'passed':True,'checks':checks}; print(kind,len(checks),'checks passed',flush=True)
finally:
    command(BIN/'pg_ctl.exe','-D',DATA,'-m','fast','-w','stop')
    (OUT/'database-verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
