"""Inventory Gradle catalog usage, check publisher metadata and query OSV.

Read-only network checks send public Maven coordinates and versions only.
No source files, configuration secrets, or application data are submitted.
"""
import argparse
import concurrent.futures
import csv
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import time
import tomllib
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET


def open_url(request, timeout):
    for attempt in range(3):
        try:
            return urllib.request.urlopen(request, timeout=timeout)
        except OSError as error:
            retryable = (not isinstance(error, urllib.error.HTTPError)
                         or error.code in {429, 500, 502, 503, 504})
            if not retryable or attempt == 2:
                raise
            time.sleep(attempt + 1)


def get_json(url, payload=None):
    data = None if payload is None else json.dumps(payload).encode()
    request = urllib.request.Request(url, data=data, headers={
        "Content-Type": "application/json", "User-Agent": "Gratify-library-audit/1.0"})
    with open_url(request, timeout=35) as response:
        return json.load(response)


def version_key(version):
    return tuple(int(part) for part in version.split("."))


def metadata(module):
    group, name = module.split(":")
    if group.startswith("com.github."):
        return {"module": module, "status": "FORK_REVIEW_REQUIRED"}
    google = group.startswith(("androidx.", "com.android.", "com.google.firebase", "com.google.android.", "com.google.gms"))
    base = "https://dl.google.com/dl/android/maven2" if google else "https://repo.maven.apache.org/maven2"
    url = f"{base}/{group.replace('.', '/')}/{name}/maven-metadata.xml"
    try:
        with open_url(url, timeout=25) as response:
            root = ET.fromstring(response.read())
        versions = [node.text for node in root.findall("./versioning/versions/version")]
        stable = [v for v in versions if re.fullmatch(r"\d+(?:\.\d+)*", v)]
        return {"module": module, "status": "OK", "source": url,
                "latest_stable": max(stable, key=version_key) if stable else None,
                "latest": root.findtext("./versioning/latest"),
                "release": root.findtext("./versioning/release"),
                "last_updated": root.findtext("./versioning/lastUpdated")}
    except (OSError, ET.ParseError) as error:
        return {"module": module, "status": "METADATA_UNAVAILABLE", "source": url, "error": str(error)}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--resolved", type=Path, required=True)
    parser.add_argument("--outdir", type=Path, required=True)
    parser.add_argument("--fail-on-findings", action="store_true",
                        help="Return a nonzero exit code for vulnerabilities or incomplete OSV coverage")
    args = parser.parse_args()
    workspace = Path(__file__).resolve().parents[1]
    catalog = tomllib.loads((workspace / "gradle/libs.versions.toml").read_text(encoding="utf-8"))
    sources = []
    for directory, children, files in os.walk(workspace):
        children[:] = [name for name in children if name not in {
            "build", ".gradle", ".git", ".claude", ".codex", "artifacts", "node_modules"}]
        for name in files:
            if name.endswith(".gradle.kts"):
                path = Path(directory) / name
                sources.append((path, path.read_text(encoding="utf-8")))
    inventory = []
    for alias, definition in catalog["libraries"].items():
        accessor = "libs." + re.sub(r"[-_]", ".", alias)
        usage = [str(path.relative_to(workspace)).replace("\\", "/") for path, content in sources
                 if re.search(r"(?<![\w.])" + re.escape(accessor) + r"(?:\.get\(\)|(?![\w.]))", content)]
        module = definition.get("module") or f"{definition['group']}:{definition['name']}"
        version = definition.get("version")
        version_ref = version.get("ref") if isinstance(version, dict) else None
        requested = catalog["versions"].get(version_ref) if version_ref else version
        if isinstance(requested, dict):
            requested = requested.get("strictly") or requested.get("require") or requested.get("prefer")
        inventory.append({"alias": alias, "module": module, "requested": requested,
                          "version_ref": version_ref, "used": bool(usage), "usage": usage})
    unique_modules = sorted({item["module"] for item in inventory})
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
        checks = list(executor.map(metadata, unique_modules))
    by_module = {item["module"]: item for item in checks}
    for item in inventory:
        item.update({k: v for k, v in by_module[item["module"]].items() if k != "module"})
        current, stable = item["requested"], item.get("latest_stable")
        item["stable_upgrade_available"] = bool(current and stable and re.fullmatch(r"\d+(?:\.\d+)*", current)
                                                and version_key(stable) > version_key(current))

    scopes = json.loads(args.resolved.read_text(encoding="utf-8-sig"))
    coordinates = {}
    for scope in scopes:
        for component in scope["components"]:
            key = (f"{component['group']}:{component['name']}", component["version"])
            coordinates.setdefault(key, []).append(scope["project"])
    packages = sorted(coordinates)
    vulnerability_results = []
    failures = []
    for offset in range(0, len(packages), 100):
        batch = packages[offset:offset + 100]
        try:
            result = get_json("https://api.osv.dev/v1/querybatch", {"queries": [
                {"package": {"ecosystem": "Maven", "name": name}, "version": version}
                for name, version in batch]})
            for coordinate, findings in zip(batch, result["results"], strict=True):
                ids = [v["id"] for v in findings.get("vulns", [])]
                if ids:
                    vulnerability_results.append({"module": coordinate[0], "version": coordinate[1],
                                                  "scopes": coordinates[coordinate], "vulnerability_ids": ids})
        except (OSError, ValueError) as error:
            failures.append({"offset": offset, "packages": len(batch), "error": str(error)})
    details = {}
    for identifier in sorted({v for result in vulnerability_results for v in result["vulnerability_ids"]}):
        try:
            details[identifier] = get_json(f"https://api.osv.dev/v1/vulns/{identifier}")
        except OSError as error:
            details[identifier] = {"error": str(error)}
    report = {"scanned_at_utc": datetime.now(timezone.utc).isoformat(),
              "vulnerability_database": "https://api.osv.dev/v1/querybatch",
              "catalog": inventory, "runtime_scopes": scopes,
              "resolved_unique_coordinates": len(packages), "vulnerability_findings": vulnerability_results,
              "osv_failures": failures, "vulnerability_details": details}
    args.outdir.mkdir(parents=True, exist_ok=True)
    (args.outdir / "audit.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    with (args.outdir / "catalog.csv").open("w", newline="", encoding="utf-8-sig") as stream:
        fields = ["alias", "module", "requested", "used", "latest_stable", "latest", "stable_upgrade_available", "status", "source"]
        writer = csv.DictWriter(stream, fields, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(inventory)
    print(json.dumps({"catalog_aliases": len(inventory), "used_aliases": sum(i["used"] for i in inventory),
                      "unused_aliases": [i["alias"] for i in inventory if not i["used"]],
                      "stable_upgrades": [{k: i.get(k) for k in ["alias", "requested", "latest_stable", "used"]}
                                          for i in inventory if i["stable_upgrade_available"]],
                      "runtime_coordinates": len(packages), "vulnerability_findings": vulnerability_results,
                      "osv_failed_batches": len(failures)}, indent=2))
    if args.fail_on_findings and (failures or vulnerability_results):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
