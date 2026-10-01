from pathlib import Path
import hashlib
import json
import zipfile

ROOT = Path(__file__).resolve().parents[2]
expected = {}
for name in ("terms-of-use.md", "privacy-policy.md"):
    content = (ROOT / "docs/legal" / name).read_bytes()
    resource = ROOT / "composeApp/src/commonMain/composeResources/files/legal" / name
    assert resource.read_bytes() == content, f"Resource copy differs: {name}"
    assert b"Tan Heradhe Rat Djendra (TanDjendra)" in content
    assert b"Indonesia" in content and b"Status: DRAF" in content
    expected[name] = content

results = []
for variant, filename in (
    ("debug", "androidApp-universal-debug.apk"),
    ("release", "androidApp-universal-release-unsigned.apk"),
):
    path = ROOT / "androidApp/build/outputs/apk" / variant / filename
    with zipfile.ZipFile(path) as package:
        documents = []
        for name, content in expected.items():
            matches = [entry for entry in package.namelist() if entry.endswith("/legal/" + name)]
            assert len(matches) == 1, f"Missing or duplicate legal document: {variant}/{name}"
            assert package.read(matches[0]) == content, f"Stale packaged legal document: {variant}/{name}"
            documents.append({"name": name, "sha256": hashlib.sha256(content).hexdigest()})
    results.append({"variant": variant, "apk": str(path.relative_to(ROOT)), "bytes": path.stat().st_size, "documents": documents})

report = {"status": "PASS", "documents_approved": False, "packages": results}
(ROOT / "artifacts/launch-audit/packaged-legal-verification.json").write_text(json.dumps(report, indent=2))
print("PASS: both APKs include the current legal drafts and confirmed operator identity")
