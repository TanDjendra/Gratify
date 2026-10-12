"""Collect the opt-in debug probe, without reading account state or app databases."""
import hashlib
import argparse
import json
import pathlib
import subprocess
import time

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / "artifacts/lyrics-performance-2026-10-11"
ADB = "D:/Tan/APK/platform-tools/adb.exe"
SERIAL = "emulator-5556"
PACKAGE = "com.tan.gratify.dev"


def adb(*args, check=True):
    return subprocess.run([ADB, "-s", SERIAL, *args], capture_output=True, check=check, timeout=45)


def main():
    global OUT, SERIAL
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="artifacts/lyrics-performance-2026-10-11")
    parser.add_argument("--serial", default=SERIAL)
    parser.add_argument("--physical-device", action="store_true")
    parser.add_argument("--apk", default="androidApp/build/outputs/apk/debug/androidApp-x86_64-debug.apk")
    args = parser.parse_args()
    SERIAL = args.serial
    OUT = ROOT / args.output
    OUT.mkdir(parents=True, exist_ok=True)
    environment = {
        "serial": SERIAL,
        "debug_build": True,
        "physical_device": args.physical_device,
        "software_gpu": not args.physical_device,
        "audio_network_battery_included": False,
        "apk_sha256": hashlib.sha256((ROOT / args.apk).read_bytes()).hexdigest(),
    }
    for label, command in {
        "android_release": ("shell", "getprop", "ro.build.version.release"),
        "sdk": ("shell", "getprop", "ro.build.version.sdk"),
        "abi": ("shell", "getprop", "ro.product.cpu.abi"),
        "model": ("shell", "getprop", "ro.product.model"),
        "manufacturer": ("shell", "getprop", "ro.product.manufacturer"),
        "hardware": ("shell", "getprop", "ro.hardware"),
        "soc": ("shell", "getprop", "ro.soc.model"),
        "screen": ("shell", "wm", "size"),
        "density": ("shell", "wm", "density"),
        "cpu_cores": ("shell", "getconf", "_NPROCESSORS_ONLN"),
    }.items():
        environment[label] = adb(*command).stdout.decode().strip()
    if args.physical_device:
        surface = adb("shell", "dumpsys", "SurfaceFlinger", check=False).stdout.decode(errors="replace")
        environment["gpu"] = next((line.strip() for line in surface.splitlines() if "GLES:" in line), "unknown")
        environment["memory"] = adb("shell", "cat", "/proc/meminfo").stdout.decode(errors="replace").splitlines()[:3]
    (OUT / "environment.json").write_text(json.dumps(environment, indent=2), encoding="utf-8")
    pid = adb("shell", "pidof", PACKAGE).stdout.decode().strip().split()[0]
    deadline = time.monotonic() + 600
    seen = set()
    while time.monotonic() < deadline:
        logs = adb("logcat", "-d", f"--pid={pid}", "-s", "LyricsQualityPerf:I", "AndroidRuntime:E").stdout.decode(errors="replace")
        (OUT / "runtime.log").write_text(logs, encoding="utf-8")
        for line in logs.splitlines():
            if "LyricsQualityPerf:" in line and line not in seen:
                seen.add(line)
                print(line.split("LyricsQualityPerf:", 1)[1].strip(), flush=True)
        if "FATAL EXCEPTION" in logs:
            raise RuntimeError("Probe crashed; inspect runtime.log")
        result = adb("shell", "run-as", PACKAGE, "cat", "files/lyrics-quality-performance.json", check=False)
        if result.returncode == 0:
            try:
                report = json.loads(result.stdout)
            except json.JSONDecodeError:
                time.sleep(1)
                continue
            if args.physical_device:
                report["probe_scope_label"] = report.get("scope")
                report["scope"] = "Android debug physical phone; production Simp LyricsView; line-only mode; synthetic 80/500-line fixtures"
            (OUT / "android-probe.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
            if report.get("complete"):
                (OUT / "meminfo-final.txt").write_bytes(adb("shell", "dumpsys", "meminfo", PACKAGE).stdout)
                (OUT / "gfxinfo-final.txt").write_bytes(adb("shell", "dumpsys", "gfxinfo", PACKAGE).stdout)
                print("COMPLETE", flush=True)
                return
        time.sleep(10)
    raise TimeoutError("Probe did not complete; partial measurements preserved")


if __name__ == "__main__":
    main()
