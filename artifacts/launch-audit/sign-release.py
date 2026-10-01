"""Sign the existing release APK with the user's original key; no credential-bearing CLI arguments."""
from pathlib import Path
import argparse, hashlib, json, os, subprocess
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "artifacts/launch-audit"
parser = argparse.ArgumentParser()
parser.add_argument("--apply", action="store_true")
options = parser.parse_args()
properties = {}
for line in (ROOT / "local.properties").read_text().splitlines():
    if "=" in line and not line.lstrip().startswith(("#", "!")):
        key, value = line.split("=", 1)
        properties[key.strip()] = value.strip()

def setting(name, default=""):
    return os.environ.get(name) or properties.get(name) or default

keystore = Path(setting("KEYSTORE_PATH", "D:/Tan/script/Gratify/gratify.jks"))
# The owner placed signing settings beside the original keystore.
# Read only signing fields from that file, never adopt another project's backend credentials.
adjacent_properties = keystore.parent / "local.properties"
if adjacent_properties.is_file() and adjacent_properties.resolve() != (ROOT / "local.properties").resolve():
    for line in adjacent_properties.read_text().splitlines():
        if "=" in line and not line.lstrip().startswith(("#", "!")):
            key, value = line.split("=", 1)
            if key.strip() in ("KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD") and not properties.get(key.strip()):
                properties[key.strip()] = value.strip()
source = ROOT / "androidApp/build/outputs/apk/release/androidApp-universal-release-unsigned.apk"
destination = source.parent / "Gratify-release-signed.apk"
aligned = source.parent / "Gratify-release-aligned.apk"
sdk = Path(os.environ.get("ANDROID_HOME", str(Path.home() / "AppData/Local/Android/Sdk")))
build_tools = sdk / "build-tools/37.0.0"
java = Path(os.environ.get("JAVA_HOME", "D:/Tan/APK/JDK")) / "bin/java.exe"
required = ("KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
report = {"time_utc": datetime.now(timezone.utc).isoformat(), "status": "NOT_SIGNED",
          "keystore_exists": keystore.is_file(), "unsigned_apk_exists": source.is_file(),
          "missing_settings": [key for key in required if not setting(key)]}
if not options.apply:
    print(json.dumps(report, indent=2))
    raise SystemExit(0)
try:
    if not keystore.is_file() or not source.is_file() or report["missing_settings"]:
        raise ValueError("Signing prerequisites are missing")
    environment = dict(os.environ)
    environment["GRATIFY_SIGN_STORE_PASSWORD"] = setting("KEYSTORE_PASSWORD")
    environment["GRATIFY_SIGN_KEY_PASSWORD"] = setting("KEY_PASSWORD")
    def run(arguments):
        result = subprocess.run([str(arg) for arg in arguments], env=environment,
                                capture_output=True, text=True, timeout=120, creationflags=0x08000000)
        if result.returncode:
            # Tool errors may contain paths or secrets; report stage and return code only.
            report["failed_tool"] = Path(str(arguments[0])).name
            report["tool_exit_code"] = result.returncode
            raise RuntimeError("Signing tool failed")
        return result.stdout
    run([build_tools / "zipalign.exe", "-f", "-P", "16", "4", source, aligned])
    run([java, "-jar", build_tools / "lib/apksigner.jar", "sign", "--ks", keystore,
         "--ks-key-alias", setting("KEY_ALIAS"), "--ks-pass", "env:GRATIFY_SIGN_STORE_PASSWORD",
         "--key-pass", "env:GRATIFY_SIGN_KEY_PASSWORD", "--out", destination, aligned])
    verified = run([java, "-jar", build_tools / "lib/apksigner.jar", "verify", "--verbose", "--print-certs", destination])
    run([build_tools / "zipalign.exe", "-c", "-P", "16", "4", destination])
    report.update(status="SIGNED_AND_VERIFIED", apk=str(destination), bytes=destination.stat().st_size,
                  unsigned_source_sha256=hashlib.sha256(source.read_bytes()).hexdigest(),
                  sha256=hashlib.sha256(destination.read_bytes()).hexdigest(),
                  certificate_sha256=[line.split("certificate SHA-256 digest:", 1)[-1].strip() for line in verified.splitlines()
                                      if "certificate SHA-256 digest:" in line],
                  original_keystore_sha256=hashlib.sha256(keystore.read_bytes()).hexdigest(),
                  installed_application_update_tested=False)
    runtime_path = OUT / "device-runtime-verification.json"
    if runtime_path.is_file():
        runtime = json.loads(runtime_path.read_text())
        if runtime.get("signed_apk_sha256") == report["sha256"]:
            report["installed_application_update_tested"] = runtime.get("installed_update_session_retained") is True
    aligned.unlink(missing_ok=True)
except Exception as error:
    report["error_type"] = type(error).__name__
finally:
    (OUT / "signing-verification.json").write_text(json.dumps(report, indent=2))
print(json.dumps(report, indent=2))
if report["status"] != "SIGNED_AND_VERIFIED":
    raise SystemExit(1)
