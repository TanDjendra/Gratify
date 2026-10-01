"""Exercise actual migrations in an isolated PostgreSQL on loopback, never Supabase."""
from pathlib import Path
import argparse, json, socket, subprocess, time, runpy
import psycopg
from psycopg.types.json import Jsonb

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "artifacts/launch-audit"
RUNTIME = OUT / "postgres-test-runtime"
DATA = OUT / "postgres-test-data"
BIN = RUNTIME / "bin"
CREATE_NO_WINDOW = 0x08000000
A = "11111111-1111-4111-8111-111111111111"
B = "22222222-2222-4222-8222-222222222222"
arguments = argparse.ArgumentParser()
arguments.add_argument('--uuid', action='store_true', help='Use the observed production UUID schema')
options = arguments.parse_args()
report_path = OUT / ('database-uuid-verification.json' if options.uuid else 'database-fixes-verification.json')

def command(*args):
    with (OUT / "postgres-test-process.log").open("a") as output:
        result = subprocess.run([str(a) for a in args], stdout=output, stderr=output,
                                creationflags=CREATE_NO_WINDOW, timeout=60)
    if result.returncode: raise RuntimeError("PostgreSQL process failed; inspect postgres-test-process.log")

with socket.socket() as reservation:
    reservation.bind(("127.0.0.1", 0)); port = reservation.getsockname()[1]
if not (DATA / "PG_VERSION").exists():
    command(BIN / "initdb.exe", "-D", DATA, "-U", "postgres", "-A", "trust", "--no-locale", "--encoding=UTF8")
command(BIN / "pg_ctl.exe", "-D", DATA, "-l", OUT / "postgres-test.log", "-o", f"-h 127.0.0.1 -p {port}", "-w", "start")
checks = []
try:
    with psycopg.connect(host="127.0.0.1", port=port, user="postgres", dbname="postgres", autocommit=True) as connection:
        connection.execute("DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public; DROP SCHEMA IF EXISTS auth CASCADE; CREATE SCHEMA auth;")
        for role in ("anon", "authenticated"):
            if not connection.execute("SELECT 1 FROM pg_roles WHERE rolname=%s", (role,)).fetchone(): connection.execute(f"CREATE ROLE {role} NOLOGIN")
        connection.execute("CREATE TABLE auth.users(id uuid PRIMARY KEY); CREATE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql STABLE AS $$ SELECT NULLIF(current_setting('request.jwt.claim.sub',true),'')::uuid $$; GRANT USAGE ON SCHEMA public,auth TO anon,authenticated; GRANT EXECUTE ON FUNCTION auth.uid() TO anon,authenticated;")
        setup = (ROOT / "supabase_setup.sql").read_text()
        if options.uuid:
            setup = runpy.run_path(str(OUT / 'uuid-schema-fixture.py'))['prepare'](setup)
            from psycopg.types.string import TextLoader
            connection.adapters.register_loader(2950, TextLoader)
        connection.execute(setup)
        connection.execute((ROOT / "supabase_user_data_rls_hotfix.sql").read_text())
        hotfix = (ROOT / "supabase_social_rls_hotfix.sql").read_text()
        if options.uuid: hotfix = hotfix.replace('gen_random_uuid()::text', 'gen_random_uuid()')
        connection.execute(hotfix)
        migrations = sorted((ROOT / "supabase/migrations").glob("20261001*.sql"))
        bundle = (OUT / ("supabase-launch-fixes-uuid.sql" if options.uuid else "supabase-launch-fixes.sql")).read_text()
        try:
            with connection.transaction():
                connection.execute("ALTER TABLE public.user_settings ALTER COLUMN settings_json TYPE jsonb USING settings_json::jsonb")
                connection.execute(bundle)
        except psycopg.errors.RaiseException:
            pass
        else:
            raise AssertionError("Bundle accepted an incompatible JSONB settings schema")
        assert connection.execute("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='cloud_playlists' AND column_name='client_sync_id'").fetchone()[0]==0
        checks.append("SQL Editor preflight rejects incompatible schema before mutations and the outer transaction rolls back")
        connection.execute(bundle)
        if options.uuid:
            connection.execute(bundle)
        else:
            for migration in migrations:
                connection.execute(migration.read_text())
        checks.append("SQL Editor bundle applies all hotfixes and seven migrations together; every migration applies again without errors")
        connection.execute("INSERT INTO auth.users(id) VALUES(%s),(%s)", (A,B))
        connection.execute("INSERT INTO public.profiles(id,display_name,now_playing_title,top_artists) VALUES(%s,'A','Private song','[]'),(%s,'B','B song','[]')", (A,B))

        def role(owner=None):
            connection.execute("RESET ROLE")
            connection.execute("SELECT set_config('request.jwt.claim.sub',%s,false)", (owner or "",))
            connection.execute("SET ROLE authenticated" if owner else "SET ROLE anon")

        def rpc(name, args=()):
            return connection.execute(f"SELECT * FROM public.{name}({','.join(['%s']*len(args))})", args)

        def must_fail(name,args):
            try: rpc(name,args)
            except psycopg.Error: return
            raise AssertionError(f"{name} unexpectedly accepted invalid input")

        track = {"video_id":"v1","title":"Song","artist":"Artist","duration":60,"thumbnail_url":None}
        role(A)
        rpc("gratify_prepare_account_deletion")
        playlist_id = rpc("gratify_replace_cloud_playlist_v2",("device-independent-playlist-A","Playlist",None,True,Jsonb([track]))).fetchone()[0]
        shared_id = connection.execute("SELECT id FROM public.shared_playlists WHERE user_id=%s",(A,)).fetchone()[0]
        must_fail("gratify_replace_cloud_playlist_v2",("device-independent-playlist-A","Replaced",None,True,Jsonb([track,{**track,"video_id":None}])))
        assert connection.execute("SELECT title FROM public.cloud_playlists WHERE id=%s",(playlist_id,)).fetchone()[0]=="Playlist"
        assert connection.execute("SELECT count(*) FROM public.cloud_playlist_items WHERE playlist_id=%s",(playlist_id,)).fetchone()[0]==1
        assert connection.execute("SELECT count(*) FROM public.shared_playlist_tracks WHERE playlist_id=%s",(shared_id,)).fetchone()[0]==1
        checks.append("Malformed replacement rolls back cloud metadata, cloud tracks and shared publication")
        same_title_id = rpc("gratify_replace_cloud_playlist_v2",("device-independent-playlist-two","Playlist",None,True,Jsonb([track]))).fetchone()[0]
        assert same_title_id != playlist_id
        assert connection.execute("SELECT count(*) FROM public.cloud_playlists").fetchone()[0]==2
        repeated_id = rpc("gratify_replace_cloud_playlist_v2",("device-independent-playlist-A","Renamed on device B",None,None,Jsonb([track]))).fetchone()[0]
        assert repeated_id==playlist_id
        assert connection.execute("SELECT count(*) FROM public.shared_playlists").fetchone()[0]==2
        checks.append("Same titles retain distinct identities; cross-device rename and retry reuse one cloud/shared identity")
        rpc("gratify_hide_owned_playlist_v2",("device-independent-playlist-two",))
        assert connection.execute("SELECT is_public FROM public.cloud_playlists WHERE id=%s",(same_title_id,)).fetchone()[0] is False
        assert connection.execute("SELECT count(*) FROM public.shared_playlists").fetchone()[0]==1
        rpc("gratify_delete_owned_playlist_v2",("device-independent-playlist-two",))
        assert connection.execute("SELECT id FROM public.cloud_playlists").fetchall()==[(playlist_id,)]
        checks.append("Hide/delete affects only the selected stable identity, preserving another playlist with the same title")
        for mutation in ("DELETE FROM public.cloud_playlists WHERE title='Playlist'", "UPDATE public.shared_playlists SET title='Unsafe'", "INSERT INTO public.shared_playlists(user_id,title) VALUES ('"+A+"','Unsafe')"):
            try: connection.execute(mutation)
            except psycopg.errors.InsufficientPrivilege: pass
            else: raise AssertionError("Direct legacy playlist mutation was allowed")
        checks.append("Direct REST mutations and legacy column grants are blocked; only owner-checked transaction RPCs can write playlists")
        rpc("gratify_set_profile_privacy",("show_recent_artists",False)); rpc("gratify_set_profile_privacy",("show_playlists",False)); rpc("gratify_set_profile_privacy",("show_followers",False))
        connection.execute("INSERT INTO public.follows(follower_id,following_id) VALUES(%s,%s)",(A,B))
        role()
        assert connection.execute("SELECT now_playing_title,top_artists FROM public.public_profiles WHERE id=%s",(A,)).fetchone()==(None,None)
        for table in ("shared_playlists","shared_playlist_tracks","cloud_playlists","cloud_playlist_items","follows"):
            assert connection.execute(f"SELECT count(*) FROM public.{table}").fetchone()[0]==0,table
        try: connection.execute("SELECT * FROM public.profiles")
        except psycopg.errors.InsufficientPrivilege: pass
        else: raise AssertionError("anon can read private profile source")
        must_fail("gratify_delete_account",())
        checks.append("Anonymous cannot read hidden activity, follow graph, playlists or child tracks, nor delete accounts")
        role(B)
        assert connection.execute("SELECT now_playing_title FROM public.public_profiles WHERE id=%s",(A,)).fetchone()[0] is None
        must_fail("gratify_replace_shared_playlist",(shared_id,5,"Hijack",None,None,Jsonb([])))
        other_owner_id = rpc("gratify_replace_cloud_playlist_v2",("device-independent-playlist-A","B private playlist",None,False,Jsonb([track]))).fetchone()[0]
        assert other_owner_id != playlist_id
        assert connection.execute("SELECT user_id FROM public.cloud_playlists WHERE id=%s",(other_owner_id,)).fetchone()[0]==B
        assert connection.execute("SELECT count(*) FROM public.user_library_state").fetchone()[0]==0
        role(A)
        assert connection.execute("SELECT now_playing_title FROM public.public_profiles WHERE id=%s",(A,)).fetchone()[0]=="Private song"
        assert connection.execute("SELECT count(*) FROM public.cloud_playlists").fetchone()[0]==1
        checks.append("Owner can read own private data; second user cannot replace another owner's playlist")
        added = {"kind":"user_liked_songs","item_id":"liked-v1","enabled":True,"payload":{"title":"Liked","duration":60}}
        rpc("gratify_apply_library_changes",(Jsonb([added]),))
        rpc("gratify_apply_library_changes",(Jsonb([{**added,"enabled":False,"payload":None}]),))
        assert connection.execute("SELECT count(*) FROM public.user_liked_songs").fetchone()[0]==0
        assert connection.execute("SELECT enabled FROM public.user_library_state WHERE item_id='liked-v1'").fetchone()[0] is False
        rpc("gratify_apply_library_changes",(Jsonb([added]),))
        assert connection.execute("SELECT count(*) FROM public.user_liked_songs").fetchone()[0]==1
        must_fail("gratify_apply_library_changes",(Jsonb([{**added,"item_id":"rollback"}, {**added,"kind":"profiles"}]),))
        assert connection.execute("SELECT count(*) FROM public.user_liked_songs WHERE video_id='rollback'").fetchone()[0]==0
        role(B); assert connection.execute("SELECT count(*) FROM public.user_liked_songs").fetchone()[0]==0
        checks.append("Explicit additions, deletions and re-additions are owner scoped; invalid batch rolls back every edit")
        role(A)
        large_tracks = [{**track,"video_id":f"large-{i}"} for i in range(1025)]
        large_id = rpc("gratify_replace_cloud_playlist_v2",("large-playlist-identity","Large playlist",None,True,Jsonb(large_tracks))).fetchone()[0]
        large_shared = connection.execute("SELECT id FROM public.shared_playlists WHERE client_sync_id='large-playlist-identity'").fetchone()[0]
        cloud_tracks = rpc("gratify_get_cloud_playlist_items",(large_id,)).fetchone()[0]
        shared_tracks = rpc("gratify_get_shared_playlist_tracks",(large_shared,)).fetchone()[0]
        assert len(cloud_tracks)==1025 and len(shared_tracks)==1025
        assert [row["video_id"] for row in cloud_tracks]==[row["video_id"] for row in large_tracks]
        assert [row["video_id"] for row in shared_tracks]==[row["video_id"] for row in large_tracks]
        role()
        assert rpc("gratify_get_cloud_playlist_items",(large_id,)).fetchone()[0]==[]
        assert rpc("gratify_get_shared_playlist_tracks",(large_shared,)).fetchone()[0]==[]
        role(A); rpc("gratify_delete_owned_playlist_v2",("large-playlist-identity",))
        checks.append("Atomic ordered snapshots preserve 1,025 tracks in one JSON value and cannot bypass private-playlist RLS")
        rpc("gratify_replace_queue",(Jsonb([{"video_id":"q","title":"Queue","duration":60}]),))
        must_fail("gratify_replace_queue",(Jsonb([{"video_id":"q2"},{"video_id":None}]),))
        assert connection.execute("SELECT video_id FROM public.user_queue").fetchone()[0]=="q"
        checks.append("Queue replacement failure preserves previous queue")
        must_fail("gratify_delete_owned_playlist",(5,))
        rpc("gratify_delete_owned_playlist_v2",("device-independent-playlist-A",))
        must_fail("gratify_replace_cloud_playlist_v2",("device-independent-playlist-A","Stale device",None,True,Jsonb([track])))
        assert connection.execute("SELECT count(*) FROM public.cloud_playlists").fetchone()[0]==0
        assert connection.execute("SELECT count(*) FROM public.shared_playlists").fetchone()[0]==0
        checks.append("Playlist deletion removes owner cloud/shared data together, rejects numeric clients and stale-device resurrection")
        rpc("gratify_delete_account")
        role(); connection.execute("RESET ROLE")
        assert connection.execute("SELECT id::text FROM auth.users").fetchall()==[(B,)]
        assert connection.execute("SELECT id FROM public.profiles").fetchall()==[(B,)]
        assert connection.execute("SELECT count(*) FROM public.user_library_state WHERE user_id=%s",(A,)).fetchone()[0]==0
        assert connection.execute("SELECT count(*) FROM public.playlist_sync_tombstones WHERE user_id=%s",(A,)).fetchone()[0]==0
        assert connection.execute("SELECT id FROM public.cloud_playlists WHERE user_id=%s",(B,)).fetchall()==[(other_owner_id,)]
        checks.append("Account deletion removes only authenticated account and its cloud state, preserving other account")
    report={"status":"PASS","environment":"Isolated local PostgreSQL 17.6 on loopback; not live Supabase", "schema":"production UUID fixture" if options.uuid else "base TEXT schema", "checks":checks}
    report_path.write_text(json.dumps(report,indent=2))
    print(json.dumps(report,indent=2))
except Exception as error:
    report_path.write_text(json.dumps({"status":"FAIL","completed_checks":checks,"error":str(error)},indent=2))
    raise
finally:
    command(BIN / "pg_ctl.exe", "-D", DATA, "-m", "fast", "-w", "stop")
