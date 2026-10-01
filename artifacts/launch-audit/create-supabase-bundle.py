"""Prepare a reviewable SQL Editor alternative with the same preflight and atomic transaction."""
from pathlib import Path
import ast, re
from pglast import parser

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "artifacts/launch-audit"
applier = ast.parse((OUT / "apply-database-fixes.py").read_text())
required = next(ast.literal_eval(node.value) for node in ast.walk(applier)
                if isinstance(node, ast.Assign) and any(isinstance(target, ast.Name) and target.id == "required" for target in node.targets))
checks = []
for table, columns in required.items():
    for column in columns:
        checks.append(f"IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='{table}' AND column_name='{column}') THEN RAISE EXCEPTION 'Schema mismatch: {table}.{column} missing'; END IF;")
types = {(table, "user_id"): "text" for table, columns in required.items() if "user_id" in columns}
types.update({("cloud_playlists", "id"): "text", ("shared_playlists", "id"): "text",
              ("cloud_playlist_items", "playlist_id"): "text", ("shared_playlist_tracks", "playlist_id"): "text",
              ("user_queue", "duration"): "integer", ("user_settings", "settings_json"): "text"})
for (table, column), kind in types.items():
    checks.append(f"IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='{table}' AND column_name='{column}' AND data_type='{kind}') THEN RAISE EXCEPTION 'Schema mismatch: {table}.{column} expected {kind}'; END IF;")
files = [ROOT / "supabase_user_data_rls_hotfix.sql", ROOT / "supabase_social_rls_hotfix.sql"] + sorted((ROOT / "supabase/migrations").glob("20261001*.sql"))
sql = "-- Gratify launch fixes. Prepared for review; not evidence of live deployment.\n-- Run on the authorized Gratify project only. All changes commit together.\nBEGIN;\nDO $$ BEGIN\n" + "\n".join(checks) + "\nEND $$;\n"
for file in files:
    sql += "\n-- Source: " + file.name + "\n" + re.sub(r"(?m)^\s*(BEGIN|COMMIT);\s*$", "", file.read_text()) + "\n"
sql += "\nNOTIFY pgrst, 'reload schema';\nCOMMIT;\n"
parser.parse_sql(sql); parser.parse_plpgsql_json(sql)
(OUT / "supabase-launch-fixes.sql").write_text(sql)
print("SQL Editor bundle prepared and syntax validated:", len(files), "source files; schema preflight; one transaction")
