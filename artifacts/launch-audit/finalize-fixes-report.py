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
import argparse
parser = argparse.ArgumentParser()
parser.add_argument("--build-log", default="playlist-device-fixes-build-final.log")
args = parser.parse_args()
log = (OUT/args.build_log).read_text(errors="replace")
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
def evidence(name):
    path=OUT/name
    return json.loads(path.read_text()) if path.is_file() else {}
deployment=evidence("database-deployment.json")
live=evidence("live-api-verification.json")
monitoring=evidence("sentry-verification.json")
runtime=evidence("device-runtime-verification.json")
ci=evidence("ci-verification.json")
server_verified=deployment.get("status")=="APPLIED" and live.get("status")=="PASS"
# Refuse to replace previous evidence with a failed/incomplete build.
if not successful or not packaging_successful or packaged_legal["status"] != "PASS" or database["status"] != "PASS" or lint is None or lint["errors"] or failures or tests<37:
    raise SystemExit("Verification prerequisites failed; previous reports preserved")
report = {"time_utc":datetime.now(timezone.utc).isoformat(),"build_log":args.build_log,"full_build_successful":successful,
          "tests":tests,"failures":failures,"suites":suites,"database":database,
          "legal_resource_packaging_successful":packaging_successful,"packaged_legal":packaged_legal,
          "lint":lint,"live_supabase_verified":server_verified,"database_deployment":deployment,
          "live_auth_postgrest":live,"device_uat_verified":runtime.get("full_uat_verified",False),"device_runtime":runtime,
          "release_signed":signed_verified,"signing":signing,"legal_documents_approved":False,
          "sentry_production_configured":monitoring.get("status","").startswith("EVENT_RECEIVED"),"sentry":monitoring,"ci":ci}
(OUT/"fixes-verification.json").write_text(json.dumps(report,indent=2))
trackerfile=OUT/"bug-tracker.json"
tracker=json.loads(trackerfile.read_text())
server_ids={"F02","F03","F05","F11","F17","L01"}
for bug in tracker:
    bug.setdefault("original_audit_status",bug["status"])
    bug["repair_report"]="LAUNCH_COMPLETION_2026-10-01.md"
    if bug["id"] in server_ids:
        bug["status"]="DEPLOYED_LIVE_RPC_RLS_VERIFIED_REQUIRES_DEVICE_UAT" if server_verified else "READY_FOR_DATABASE_DEPLOYMENT"
    elif bug["id"]=="L02":bug["status"]="DRAFT_REQUIRES_OWNER_REVIEW"
    elif bug["id"]=="L03":
        bug["status"]=("ANDROID_EVENT_RECEIVED_CI_MAPPING_UPLOAD_VERIFIED_DESKTOP_UNVERIFIED" if monitoring.get("ci_mapping_upload_verified") and monitoring.get("status", "").startswith("EVENT_RECEIVED") else "EVENT_RECEIVED_REQUIRES_MAPPING_VERIFICATION" if monitoring.get("status","").startswith("EVENT_RECEIVED") else "IMPLEMENTED_REQUIRES_SENTRY_CONFIGURATION")
    elif bug["id"]=="L04":
        bug["status"]="FIXED_CI_VERIFIED" if ci.get("conclusion")=="success" else "IMPLEMENTED_REQUIRES_CURRENT_CI_SUCCESS"
    # Runtime discoveries and existing UAT statuses retain their specific evidence.
trackerfile.write_text(json.dumps(tracker,indent=2,ensure_ascii=False))
print(json.dumps({"tests":tests,"test_failures":failures,"build_successful":successful,"release_signed":signed_verified,
                  "live_supabase_verified":server_verified,"device_full_uat":runtime.get("full_uat_verified",False)},indent=2))
