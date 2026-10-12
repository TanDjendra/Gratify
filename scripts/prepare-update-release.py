"""Prepare verified public release files from an existing signed APK; never build/sign/upload."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import sys
from gratify_delta import digest, FORMAT


def inspect_apk(apk, aapt2, apksigner, expected_certificate, previous_code, expected_package="com.tan.gratify"):
    if expected_package not in {"com.tan.gratify", "com.tan.gratify.next"}:
        raise ValueError("Unknown release package")
    if not re.fullmatch(r"[a-fA-F0-9]{64}", expected_certificate):
        raise ValueError("Expected certificate must be the public SHA-256 fingerprint")
    signing = subprocess.run([str(apksigner), "verify", "--print-certs", str(apk)],
                             capture_output=True, text=True, check=True).stdout
    fingerprints = {value.lower() for value in re.findall(r"certificate SHA-256 digest:\s*([a-fA-F0-9]{64})", signing)}
    if fingerprints != {expected_certificate.lower()}:
        raise ValueError("APK signing certificate differs from the selected release channel")
    badging = subprocess.run([str(aapt2), "dump", "badging", str(apk)],
                             capture_output=True, text=True, check=True).stdout
    identity = re.search(r"package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'", badging)
    if not identity or identity[1] != expected_package:
        raise ValueError("APK must use the selected release package " + expected_package)
    code, version = int(identity[2]), identity[3]
    if code <= previous_code:
        raise ValueError("versionCode must exceed the previously distributed build")
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", version):
        raise ValueError("Only a stable release version may be prepared")
    abis = re.search(r"^native-code:\s*(.*)$", badging, re.MULTILINE)
    if not abis or not all(f"'{abi}'" in abis[1] for abi in ("arm64-v8a", "armeabi-v7a", "x86_64")):
        raise ValueError("Use the universal release APK, not an ABI split")
    xml = subprocess.run([str(aapt2), "dump", "xmltree", "--file", "AndroidManifest.xml", str(apk)],
                         capture_output=True, text=True, check=True).stdout
    channels = []
    for node in re.split(r"(?m)^\s*E: ", xml):
        if node.startswith("meta-data "):
            attrs = dict(re.findall(r'A: (?:android:|http://schemas\.android\.com/apk/res/android:)(name|value)\([^)]*\)="([^"]*)"', node))
            if attrs.get("name") == "com.tan.gratify.UPDATE_CHANNEL":
                channels.append(attrs.get("value"))
    if channels != ["full"]:
        raise ValueError("APK manifest must declare exactly one Gratify full update channel")
    size = apk.stat().st_size
    if not 0 < size <= 300 * 1024 * 1024:
        raise ValueError("APK size is outside the updater limit")
    sha = hashlib.sha256()
    with apk.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            sha.update(block)
    return {"versionCode": code, "versionName": version, "packageName": identity[1],
            "channel": "full", "apkName": "Gratify.apk", "sha256": sha.hexdigest(), "sizeBytes": size}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--aapt2", type=Path, required=True)
    parser.add_argument("--apksigner", type=Path, required=True)
    parser.add_argument("--expected-certificate", required=True)
    parser.add_argument("--expected-package", choices=["com.tan.gratify", "com.tan.gratify.next"], default="com.tan.gratify")
    parser.add_argument("--previous-version-code", type=int, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--base-apk", type=Path, action="append", default=[], help="Optional signed APK from an earlier compatible release (maximum two)")
    args = parser.parse_args()
    try:
        if "full" not in args.apk.name.lower():
            raise ValueError("Select the APK produced by build_and_sign_apk.sh --release --full")
        metadata = inspect_apk(args.apk, args.aapt2, args.apksigner, args.expected_certificate, args.previous_version_code, args.expected_package)
        if len(args.base_apk) > 2:
            raise ValueError("Prepare patches for at most two previous releases")
        bases = [inspect_apk(base, args.aapt2, args.apksigner, args.expected_certificate, 0, args.expected_package) for base in args.base_apk]
        for base in bases:
            if base["versionCode"] >= metadata["versionCode"]:
                raise ValueError("Delta source version must be older than the target")
        if len({base["sha256"] for base in bases}) != len(bases):
            raise ValueError("Duplicate delta sources")
        # Fail before writing if this would overwrite an existing preparation/source APK.
        args.output.mkdir(parents=True, exist_ok=True)
        target = args.output / "Gratify.apk"
        manifest = args.output / "gratify-update.json"
        if target.exists() or manifest.exists():
            raise ValueError("Output already contains release files; select a new directory")
        with tempfile.TemporaryDirectory(prefix="gratify-release-", dir=args.output) as temporary:
            prepared = Path(temporary)
            shutil.copyfile(args.apk, prepared / target.name)
            copied_hash = hashlib.sha256()
            with (prepared / target.name).open("rb") as stream:
                for block in iter(lambda: stream.read(1024 * 1024), b""):
                    copied_hash.update(block)
            copied = copied_hash.hexdigest()
            if copied != metadata["sha256"]:
                raise ValueError("APK changed during preparation")
            patches = []
            for source, base in zip(args.base_apk, bases):
                name = f"Gratify-from-{base['versionCode']}-{base['sha256'][:12]}.gdelta"
                if (args.output / name).exists():
                    raise ValueError("Output already contains a delta file")
                # Heavy generation happens in a timeout-bound publisher process, never on a phone.
                subprocess.run([sys.executable, str(Path(__file__).with_name("gratify_delta.py")),
                                str(source), str(prepared / target.name), str(prepared / name)], check=True, timeout=300)
                if digest(source) != base["sha256"]:
                    raise ValueError("Source APK changed during delta preparation")
                size = (prepared / name).stat().st_size
                if size > metadata["sizeBytes"] * 7 // 10:
                    (prepared / name).unlink()
                    print(f"Skipped source code {base['versionCode']}: savings below 30%.")
                    continue
                patches.append({"fromVersionCode": base["versionCode"], "fromSha256": base["sha256"],
                    "fromSizeBytes": base["sizeBytes"], "patchName": name, "sha256": digest(prepared / name),
                    "sizeBytes": size, "format": FORMAT})
            if patches:
                metadata["deltas"] = patches
            if digest(prepared / target.name) != metadata["sha256"]:
                raise ValueError("Target APK changed during delta preparation")
            (prepared / manifest.name).write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
            for item in patches:
                os.replace(prepared / item["patchName"], args.output / item["patchName"])
            os.replace(prepared / target.name, target)
            os.replace(prepared / manifest.name, manifest)
        print(f"Prepared v{metadata['versionName']} (code {metadata['versionCode']}); no release was published.")
        print(f"Upload Gratify.apk, gratify-update.json and any prepared .gdelta files to the same stable GitHub release tag v{metadata['versionName']}.")
    except (ValueError, OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
        parser.exit(1, f"Release preparation stopped: {error}\n")


if __name__ == "__main__":
    main()
