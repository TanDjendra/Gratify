"""Validate offline by default. --apply uses the configured database URL without printing it."""
from pathlib import Path
import argparse, json, os, re
from pglast import parser

ROOT = Path(__file__).resolve().parents[2]
FILES = [ROOT / "supabase_user_data_rls_hotfix.sql", ROOT / "supabase_social_rls_hotfix.sql"] + sorted((ROOT / "supabase/migrations").glob("20261001*.sql"))
args = argparse.ArgumentParser()
args.add_argument("--apply", action="store_true")
options = args.parse_args()
for file in FILES:
    parser.parse_sql(file.read_text()); parser.parse_plpgsql_json(file.read_text())
print("SQL and PLpgSQL syntax validated for", len(FILES), "files.")
if not options.apply: raise SystemExit(0)

properties = {}
for line in (ROOT / "local.properties").read_text().splitlines():
    if "=" in line and not line.lstrip().startswith(("#", "!")):
        key, value = line.split("=",1); properties[key.strip()] = value.strip()
url = os.environ.get("SUPABASE_DB_URL") or properties.get("SUPABASE_DB_URL")
if not url: raise SystemExit("SUPABASE_DB_URL is not configured. No database changes made.")

import psycopg
import certifi
report = {"status":"NOT_APPLIED", "files":[f.name for f in FILES]}
try:
    # Require the current schema contract; never coerce existing data to another type silently.
    root_certificate = os.environ.get("SUPABASE_SSL_ROOT_CERT") or properties.get("SUPABASE_SSL_ROOT_CERT") or certifi.where()
    with psycopg.connect(url, connect_timeout=15, sslmode="verify-full", sslrootcert=root_certificate) as connection:
        types = {(table,col):kind for table,col,kind in connection.execute("SELECT table_name,column_name,data_type FROM information_schema.columns WHERE table_schema='public'").fetchall()}
        required = {
            "profiles":["id","display_name","avatar_url","note","note_updated_at"],
            "follows":["follower_id","following_id"],
            "shared_playlists":["id","user_id","title","created_at"],
            "shared_playlist_tracks":["playlist_id","video_id","title","artists","duration_seconds"],
            "shared_playlist_saves":["playlist_id","user_id"],
            "cloud_playlists":["id","user_id","local_playlist_id","title","is_public","created_at"],
            "cloud_playlist_items":["playlist_id","video_id","title","artist","duration","thumbnail_url","position"],
            "user_liked_songs":["id","user_id","video_id","favorite_at"],
            "user_followed_artists":["id","user_id","channel_id","followed_at"],
            "user_saved_albums":["id","user_id","browse_id","favorite_at"],
            "user_play_history":["id","user_id","video_id","played_at"],
            "user_queue":["id","user_id","video_id","duration","position"],
            "user_settings":["user_id","settings_json"],
        }
        incompatible = [f"{table}.{col}: missing" for table,cols in required.items() for col in cols if (table,col) not in types]
        for table in required:
            if "user_id" in required[table] and types.get((table,"user_id")) != "text": incompatible.append(f"{table}.user_id: expected text")
        for table, column in (("cloud_playlists","id"),("shared_playlists","id"),
                              ("cloud_playlist_items","playlist_id"),("shared_playlist_tracks","playlist_id")):
            if types.get((table,column)) != "text": incompatible.append(f"{table}.{column}: expected text")
        if types.get(("user_queue","duration")) != "integer": incompatible.append("user_queue.duration: expected integer")
        if types.get(("user_settings","settings_json")) != "text": incompatible.append("user_settings.settings_json: expected text")
        if incompatible:
            report["schema_issues"] = incompatible
            raise ValueError("Schema requires a reviewed compatibility migration before applying these fixes")
        for file in FILES:
            sql = re.sub(r"(?m)^\s*(BEGIN|COMMIT);\s*$", "", file.read_text())
            connection.execute(sql)
        assert connection.execute("SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname IN ('gratify_replace_shared_playlist','gratify_replace_cloud_playlist','gratify_set_profile_privacy','gratify_replace_queue','gratify_delete_owned_playlist','gratify_prepare_account_deletion','gratify_delete_account','gratify_apply_library_changes','gratify_replace_shared_playlist_v2','gratify_replace_cloud_playlist_v2','gratify_hide_owned_playlist_v2','gratify_delete_owned_playlist_v2','gratify_get_cloud_playlist_items','gratify_get_shared_playlist_tracks')").fetchone()[0] == 14
        assert connection.execute("SELECT count(*) FROM pg_policies WHERE schemaname='public' AND tablename IN ('profiles','follows','shared_playlists','shared_playlist_tracks','cloud_playlists') AND cmd='ALL'").fetchone()[0] == 0
        assert not connection.execute("SELECT has_function_privilege('authenticated','public.gratify_delete_owned_playlist(bigint)','EXECUTE')").fetchone()[0]
        assert connection.execute("SELECT has_function_privilege('authenticated','public.gratify_delete_owned_playlist_v2(text)','EXECUTE')").fetchone()[0]
        for table in ("cloud_playlists","cloud_playlist_items","shared_playlists","shared_playlist_tracks"):
            assert not connection.execute("SELECT has_table_privilege('authenticated',%s,'DELETE')", ("public."+table,)).fetchone()[0]
        connection.execute("NOTIFY pgrst, 'reload schema'")
    report["status"]="APPLIED"
    report["tls_mode"]="verify-full"
    print("All launch fixes committed together; PostgREST schema reload requested.")
except Exception as error:
    report["error_type"]=type(error).__name__
    report["sqlstate"]=getattr(error,"sqlstate",None)
    print("Database fixes were not committed. Error type:", report["error_type"], "SQLSTATE:", report["sqlstate"])
    # Do not print the exception: libpq errors can include credential-bearing URLs.
finally:
    (ROOT / "artifacts/launch-audit/database-deployment.json").write_text(json.dumps(report,indent=2))
if report["status"] != "APPLIED": raise SystemExit(1)
