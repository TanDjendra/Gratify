"""Verify credential isolation and atomic callback cancellation on loopback PostgreSQL."""
from pathlib import Path
import json, socket, subprocess, uuid
import psycopg

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'artifacts/youtube-oauth'
BIN = ROOT / 'artifacts/launch-audit/postgres-test-runtime/bin'
DATA = OUT / 'postgres-data'
OUT.mkdir(parents=True, exist_ok=True)
A, B = '11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222222'
def command(*args):
    with (OUT / 'postgres-process.log').open('a') as log:
        subprocess.run([str(a) for a in args], stdout=log, stderr=log,
            creationflags=0x08000000, timeout=60, check=True)
with socket.socket() as sock:
    sock.bind(('127.0.0.1', 0)); port = sock.getsockname()[1]
if not (DATA / 'PG_VERSION').exists():
    command(BIN / 'initdb.exe', '-D', DATA, '-U', 'postgres', '-A', 'trust', '--no-locale', '--encoding=UTF8')
command(BIN / 'pg_ctl.exe', '-D', DATA, '-l', OUT / 'postgres.log', '-o', f'-h 127.0.0.1 -p {port}', '-w', 'start')
checks = []
passed = False
try:
    with psycopg.connect(host='127.0.0.1', port=port, user='postgres', dbname='postgres', autocommit=True) as db:
        db.execute('DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public; DROP SCHEMA IF EXISTS auth CASCADE; CREATE SCHEMA auth')
        for name in ('anon', 'authenticated', 'service_role'):
            if not db.execute('SELECT 1 FROM pg_roles WHERE rolname=%s', (name,)).fetchone():
                db.execute(f'CREATE ROLE {name} NOLOGIN' + (' BYPASSRLS' if name == 'service_role' else ''))
        db.execute('GRANT USAGE ON SCHEMA public TO anon,authenticated,service_role; CREATE TABLE auth.users(id uuid PRIMARY KEY)')
        db.execute('INSERT INTO auth.users VALUES(%s),(%s)', (A, B))
        migration = (ROOT / 'supabase/migrations/202610020002_youtube_oauth_connections.sql').read_text()
        db.execute(migration)
        tables = ['youtube_oauth_connections', 'youtube_oauth_attempts']
        for table in tables:
            assert db.execute('SELECT relrowsecurity FROM pg_class WHERE oid=%s::regclass', ('public.'+table,)).fetchone()[0]
            checks.append(table + ' enables RLS')
            for role in ('anon', 'authenticated'):
                for privilege in ('SELECT', 'INSERT', 'UPDATE', 'DELETE'):
                    assert not db.execute('SELECT has_table_privilege(%s,%s,%s)', (role,'public.'+table,privilege)).fetchone()[0]
                checks.append(role + ' has no credential-table privileges on ' + table)
        signatures = ['complete_youtube_oauth_attempt(uuid,uuid,text,text,timestamptz)', 'disconnect_youtube_oauth(uuid)']
        for signature in signatures:
            for role in ('anon', 'authenticated'):
                assert not db.execute('SELECT has_function_privilege(%s,%s,%s)', (role,'public.'+signature,'EXECUTE')).fetchone()[0]
            assert db.execute('SELECT has_function_privilege(%s,%s,%s)', ('service_role','public.'+signature,'EXECUTE')).fetchone()[0]
            checks.append(signature + ' is executable only by backend roles')
        db.execute('SET ROLE service_role')
        def start(owner, nonce=None):
            nonce = nonce or str(uuid.uuid4())
            db.execute("INSERT INTO youtube_oauth_attempts VALUES(%s,%s,now()+interval '5 minutes') ON CONFLICT(user_id) DO UPDATE SET nonce=EXCLUDED.nonce,expires_at=EXCLUDED.expires_at", (owner,nonce))
            return nonce
        def finish(owner, nonce):
            return db.execute("SELECT complete_youtube_oauth_attempt(%s,%s,'google-subject','encrypted-token',now()+interval '1 hour')", (owner,nonce)).fetchone()[0]
        nonce = start(A)
        assert not finish(B,nonce)
        assert db.execute('SELECT count(*) FROM youtube_oauth_connections').fetchone()[0] == 0
        checks.append('A callback nonce cannot connect a different owner')
        assert finish(A,nonce) and not finish(A,nonce)
        assert db.execute('SELECT user_id FROM youtube_oauth_connections').fetchone()[0] == uuid.UUID(A)
        checks.append('A callback is consumed once and writes only its owner')
        db.execute('UPDATE youtube_oauth_connections SET needs_reconnect=true WHERE user_id=%s',(A,))
        assert finish(A,start(A))
        assert not db.execute('SELECT needs_reconnect FROM youtube_oauth_connections WHERE user_id=%s',(A,)).fetchone()[0]
        checks.append('New consent replaces a revoked grant')
        pending = start(A)
        db.execute('SELECT disconnect_youtube_oauth(%s)', (A,))
        assert not finish(A,pending)
        assert db.execute('SELECT count(*) FROM youtube_oauth_connections').fetchone()[0] == 0
        assert db.execute('SELECT count(*) FROM youtube_oauth_attempts').fetchone()[0] == 0
        checks.append('Disconnect deletes the grant and blocks pending callbacks atomically')
        pending = start(A)
        db.execute("UPDATE youtube_oauth_attempts SET expires_at=now()-interval '1 minute'")
        assert not finish(A,pending)
        checks.append('Expired callbacks cannot store credentials')
        nonce1 = start(A); nonce2 = start(A)
        assert not finish(A,nonce1) and finish(A,nonce2)
        checks.append('Only the newest authorization attempt can complete')
        db.execute('RESET ROLE'); db.execute(migration)
        assert db.execute('SELECT count(*) FROM youtube_oauth_connections').fetchone()[0] == 1
        checks.append('Reapplying migration preserves existing encrypted connections')
        db.execute('DELETE FROM auth.users WHERE id=%s', (A,))
        assert db.execute('SELECT count(*) FROM youtube_oauth_connections').fetchone()[0] == 0
        checks.append('Deleting an account removes its encrypted connection')
        passed = True
        print(f'{len(checks)} PostgreSQL checks passed')
finally:
    command(BIN / 'pg_ctl.exe', '-D', DATA, '-m', 'fast', '-w', 'stop')
    (OUT / 'database-verification.json').write_text(json.dumps(dict(passed=passed, checks=checks), indent=2))
