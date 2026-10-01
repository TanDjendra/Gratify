from pathlib import Path
import json, xml.etree.ElementTree as ET, hashlib
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "artifacts/launch-audit"
modules = ["core/domain","core/data","core/service/ktorExt","core/service/kotlinYtmusicScraper","core/service/lyricsService","composeApp"]
suites = []
for module in modules:
    for file in (ROOT/module/"build/test-results/jvmTest").glob("TEST-*.xml"):
        suite = ET.parse(file).getroot()
        suites.append({"module":module,"suite":suite.attrib["name"],"tests":int(suite.attrib["tests"]),"failures":int(suite.attrib["failures"]),"errors":int(suite.attrib["errors"])})
tests = sum(x["tests"] for x in suites)
failures = sum(x["failures"]+x["errors"] for x in suites)
log = (OUT/"completion-final-build.log").read_text(errors="replace")
successful = "BUILD SUCCESSFUL" in log and "BUILD FAILED" not in log
packaging_log = log
packaging_successful = "BUILD SUCCESSFUL" in packaging_log and "BUILD FAILED" not in packaging_log
packaged_legal = json.loads((OUT/"packaged-legal-verification.json").read_text())
database = json.loads((OUT/"database-fixes-verification.json").read_text())
signing = json.loads((OUT/"signing-verification.json").read_text()) if (OUT/"signing-verification.json").exists() else {"status":"NOT_SIGNED"}
unsigned = ROOT/"androidApp/build/outputs/apk/release/androidApp-universal-release-unsigned.apk"
signed_path = Path(signing["apk"]) if signing.get("apk") else None
signed_verified = (signing.get("status")=="SIGNED_AND_VERIFIED" and signed_path is not None and signed_path.is_file()
                   and hashlib.sha256(signed_path.read_bytes()).hexdigest()==signing.get("sha256")
                   and hashlib.sha256(unsigned.read_bytes()).hexdigest()==signing.get("unsigned_source_sha256"))
lintfile = ROOT/"androidApp/build/reports/lint-results-debug.xml"
lint = None
if lintfile.exists():
    issues = ET.parse(lintfile).getroot().findall("issue")
    lint = {"errors":sum(x.attrib.get("severity") in ("Error","Fatal") for x in issues),"warnings":sum(x.attrib.get("severity")=="Warning" for x in issues)}
report = {"time_utc":datetime.now(timezone.utc).isoformat(),"full_build_successful":successful,
          "tests":tests,"failures":failures,"suites":suites,"database":database,
          "legal_resource_packaging_successful":packaging_successful,"packaged_legal":packaged_legal,
          "lint":lint,"live_supabase_verified":False,"device_uat_verified":False,
          "release_signed":signed_verified,"signing":signing,"legal_documents_approved":False,"sentry_production_configured":False}
(OUT/"fixes-verification.json").write_text(json.dumps(report,indent=2))

trackerfile=OUT/"bug-tracker.json"
tracker=json.loads(trackerfile.read_text())
server_ids={"F02","F03","F05","F11","F17","L01"}
for bug in tracker:
    bug.setdefault("original_audit_status",bug["status"])
    bug["assignee"]="Codex — perbaikan lokal"
    bug["repair_report"]="LAUNCH_FIXES_2026-10-01.md"
    if bug["id"] in server_ids:
        bug["status"]="READY_FOR_DATABASE_DEPLOYMENT"
    elif bug["id"]=="L02": bug["status"]="DRAFT_REQUIRES_OWNER_REVIEW"
    elif bug["id"]=="L03": bug["status"]="IMPLEMENTED_REQUIRES_SENTRY_CONFIGURATION"
    elif bug["id"]=="L04": bug["status"]="IMPLEMENTED_REQUIRES_CI_RUN"
    elif bug["id"] in {"F20","L05"}: bug["status"]="FIXED_SOURCE_REVIEWED"
    else: bug["status"]="FIXED_LOCAL_REQUIRES_UAT" if successful and not failures else "IMPLEMENTED_VERIFICATION_PENDING"
trackerfile.write_text(json.dumps(tracker,indent=2,ensure_ascii=False))
summary={"tests":tests,"test_failures":failures,"build_successful":successful,"release_signed":signed_verified,"legal_resource_packaging_successful":packaging_successful,"local_database_checks":len(database["checks"]),"lint":lint}
print(json.dumps(summary,indent=2))
if not successful or not packaging_successful or packaged_legal["status"] != "PASS" or database["status"] != "PASS" or lint is None or lint["errors"] or failures or tests<37: raise SystemExit(1)
