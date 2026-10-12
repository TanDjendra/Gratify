import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
import sys
import hashlib
import gratify_delta
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("prepare", Path(__file__).with_name("prepare-update-release.py"))
prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)


class PreparationTest(unittest.TestCase):
    def inspect(self, code=62, version="2.1.1", package="com.tan.gratify", signer="a" * 64, abis="'arm64-v8a' 'armeabi-v7a' 'x86_64'", channel="full", namespace="android:", expected_package="com.tan.gratify"):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "full.apk"
            apk.write_bytes(b"synthetic-apk")
            responses = [subprocess.CompletedProcess([], 0, f"Signer #1 certificate SHA-256 digest: {signer}\n"),
                         subprocess.CompletedProcess([], 0, f"package: name='{package}' versionCode='{code}' versionName='{version}'\nnative-code: {abis}\n"),
                         subprocess.CompletedProcess([], 0, f'E: manifest (line=1)\n  E: application (line=2)\n    E: meta-data (line=3)\n      A: android:name(0x01010003)="com.tan.gratify.UPDATE_CHANNEL" (Raw: "com.tan.gratify.UPDATE_CHANNEL")\n      A: android:value(0x01010024)="{channel}" (Raw: "{channel}")\n')]
            responses[2].stdout = responses[2].stdout.replace('A: android:', 'A: ' + namespace)
            with patch.object(prepare.subprocess, "run", side_effect=responses):
                return prepare.inspect_apk(apk, Path("aapt2"), Path("apksigner"), "a" * 64, 61, expected_package)

    def test_valid_metadata(self):
        result = self.inspect()
        self.assertEqual(62, result["versionCode"])
        self.assertEqual("Gratify.apk", result["apkName"])
        self.assertEqual(64, len(result["sha256"]))

    def test_same_and_older_code_rejected(self):
        for code in (61, 60):
            with self.assertRaises(ValueError): self.inspect(code=code)

    def test_build_tools_37_namespace_output(self):
        self.assertEqual('full', self.inspect(namespace='http://schemas.android.com/apk/res/android:')['channel'])
        with self.assertRaises(ValueError):
            self.inspect(channel='foss', namespace='http://schemas.android.com/apk/res/android:')

    def test_wrong_signer_rejected(self):
        with self.assertRaises(ValueError): self.inspect(signer="b" * 64)

    def test_debug_package_rejected(self):
        with self.assertRaises(ValueError): self.inspect(package="com.tan.gratify.dev")

    def test_successor_requires_explicit_package_selection(self):
        with self.assertRaises(ValueError): self.inspect(package="com.tan.gratify.next")
        self.assertEqual("com.tan.gratify.next", self.inspect(package="com.tan.gratify.next", expected_package="com.tan.gratify.next")["packageName"])
        with self.assertRaises(ValueError): self.inspect(expected_package="com.tan.gratify.next")
        with self.assertRaises(ValueError): self.inspect(package="com.tan.gratify.next.dev", expected_package="com.tan.gratify.next")

    def test_prerelease_rejected(self):
        with self.assertRaises(ValueError): self.inspect(version="2.1.1-dev")

    def test_abi_split_rejected(self):
        with self.assertRaises(ValueError): self.inspect(abis="'arm64-v8a'")

    def test_foss_apk_cannot_be_renamed_into_full_channel(self):
        with self.assertRaises(ValueError): self.inspect(channel="foss")

    def prepare_delta(self, base_code=61, patch_fraction=None):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source, target, output = root / "old.apk", root / "Gratify-full.apk", root / "output"
            source.write_bytes(bytes(range(256)) * 1000)
            target.write_bytes(source.read_bytes()[:10000] + b"changed" * 100 + source.read_bytes()[10700:])
            def meta(path, code):
                return {"versionCode": code, "versionName": f"2.1.{code - 61}", "packageName": "com.tan.gratify",
                    "channel": "full", "apkName": "Gratify.apk", "sha256": gratify_delta.digest(path), "sizeBytes": path.stat().st_size}
            metadata, base = meta(target, 62), meta(source, base_code)
            args = ["prepare", "--apk", str(target), "--aapt2", "aapt2", "--apksigner", "apksigner",
                "--expected-certificate", "a" * 64, "--previous-version-code", "61", "--output", str(output), "--base-apk", str(source)]
            def generator(command, **kwargs):
                self.assertEqual(300, kwargs["timeout"])
                if patch_fraction is None:
                    gratify_delta.generate(command[2], command[3], command[4])
                else:
                    Path(command[4]).write_bytes(b"x" * int(target.stat().st_size * patch_fraction))
                return subprocess.CompletedProcess(command, 0)
            with patch.object(sys, "argv", args), patch.object(prepare, "inspect_apk", side_effect=[metadata, base]), patch.object(prepare.subprocess, "run", side_effect=generator):
                prepare.main()
            import json
            prepared = json.loads((output / "gratify-update.json").read_text())
            self.assertEqual(gratify_delta.digest(target), gratify_delta.digest(output / "Gratify.apk"))
            return prepared, [file.name for file in output.iterdir()]

    def test_optional_delta_is_verified_and_published_in_metadata(self):
        metadata, files = self.prepare_delta()
        delta = metadata["deltas"][0]
        self.assertEqual(61, delta["fromVersionCode"])
        self.assertIn(delta["patchName"], files)
        self.assertLess(delta["sizeBytes"], metadata["sizeBytes"] * 0.7)

    def test_same_and_newer_source_cannot_prepare_update(self):
        for code in (62, 63):
            with self.assertRaises(SystemExit): self.prepare_delta(base_code=code)

    def test_unhelpful_delta_is_skipped_but_full_apk_remains(self):
        metadata, files = self.prepare_delta(patch_fraction=0.8)
        self.assertNotIn("deltas", metadata)
        self.assertEqual({"Gratify.apk", "gratify-update.json"}, set(files))


if __name__ == "__main__": unittest.main()
