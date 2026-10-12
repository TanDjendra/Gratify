"""Run the debug-only production-renderer probe and collect Android measurements.

No account, audio, network, or battery claim: this is a controlled UI diagnostic.
Usage: python scripts/measure-player-performance.py --serial emulator-5554
"""
import argparse
import collections
import hashlib
import json
import math
import pathlib
import subprocess
import time

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / "artifacts/apple-performance"
ADB = pathlib.Path("D:/Tan/APK/platform-tools/adb.exe")
PACKAGE = "com.tan.gratify.dev"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", default="emulator-5554")
    parser.add_argument("--repeats", default=3, type=int)
    parser.add_argument("--output", default="artifacts/apple-performance")
    args = parser.parse_args()
    global OUT
    OUT = (ROOT / args.output).resolve()
    OUT.mkdir(parents=True, exist_ok=True)

    def adb(*command, check=True):
        return subprocess.run([str(ADB), "-s", args.serial, *command],
                              capture_output=True, check=check, timeout=45)

    sdk = int(adb("shell", "getprop", "ro.build.version.sdk").stdout.decode().strip())
    if sdk < 31:
        raise RuntimeError("This deadline/GPU comparison requires Android 12 / API 31 or newer")

    apk_dir = ROOT / "androidApp/build/outputs/apk/debug"
    abi = adb("shell", "getprop", "ro.product.cpu.abi").stdout.decode().strip()
    listing = json.loads((apk_dir / "output-metadata.json").read_text())
    element = next((e for e in listing["elements"] if any(
        f.get("filterType") == "ABI" and f.get("value") == abi for f in e["filters"])),
        next(e for e in listing["elements"] if not e["filters"]))
    apk = apk_dir / element["outputFile"]
    install = adb("install", "-r", str(apk))
    print(install.stdout.decode(errors="replace").strip(), flush=True)
    metadata = {
        "serial": args.serial, "debug_build": True, "synthetic_timeline": True,
        "audio_decoding_included": False, "timeline_period_ms": 100,
        "fixture_lines": 80, "words_per_line": 8, "cover_resolution": [1024, 1024],
        "warmup_per_scenario_ms": 3000, "sample_duration_ms": 12000,
        "repeats": args.repeats, "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
    }
    for label, cmd in {
        "android_release": ("shell", "getprop", "ro.build.version.release"),
        "sdk": ("shell", "getprop", "ro.build.version.sdk"),
        "model": ("shell", "getprop", "ro.product.model"),
        "size": ("shell", "wm", "size"),
        "density": ("shell", "wm", "density"),
        "cpu_cores": ("shell", "getconf", "_NPROCESSORS_ONLN"),
    }.items():
        metadata[label] = adb(*cmd).stdout.decode(errors="replace").strip()
    surface = adb("shell", "dumpsys", "SurfaceFlinger").stdout.decode(errors="replace")
    metadata["gpu"] = next((line.strip() for line in surface.splitlines() if "GLES:" in line), "unknown")
    metadata["memory"] = adb("shell", "cat", "/proc/meminfo").stdout.decode(errors="replace").splitlines()[:3]
    (OUT / "environment.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    adb("shell", "am", "force-stop", PACKAGE)
    adb("logcat", "-c")
    adb("shell", "run-as", PACKAGE, "rm", "-f", "files/player-performance.json", check=False)
    start = adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/com.tan.gratify.PlayerPerformanceActivity",
                "--ei", "repeats", str(args.repeats))
    print(start.stdout.decode(errors="replace").strip(), flush=True)
    deadline = time.monotonic() + args.repeats * 150 + 90
    seen = set()
    captured = set()
    report = None
    while time.monotonic() < deadline:
        logs = adb("logcat", "-d", "-s", "PlayerPerf:I", "AndroidRuntime:E").stdout.decode(errors="replace")
        (OUT / "runtime.log").write_text(logs, encoding="utf-8")
        for line in logs.splitlines():
            if "START " in line and line not in seen:
                seen.add(line)
                print(line, flush=True)
                phase = line.split("START ", 1)[1].strip()
                if phase in {"1 now_playing", "1 lyrics_line", "1 lyrics_rich", "1 lyrics_paused"} and phase not in captured:
                    captured.add(phase)
                    image = adb("exec-out", "screencap", "-p").stdout
                    (OUT / (phase.replace(" ", "-") + ".png")).write_bytes(image)
        if "FATAL EXCEPTION" in logs:
            raise RuntimeError("Android runtime crash: inspect runtime.log")
        result = adb("shell", "run-as", PACKAGE, "cat", "files/player-performance.json", check=False)
        if result.returncode == 0:
            try:
                report = json.loads(result.stdout)
                (OUT / "raw-results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
            except json.JSONDecodeError:
                pass  # Writer may have been midway through replacing the report.
        if report and report.get("complete"):
            break
        time.sleep(4)
    if not report or not report.get("complete"):
        raise RuntimeError("Probe did not complete; partial data preserved")
    grouped = collections.defaultdict(list)
    for sample in report["samples"]:
        grouped[sample["scenario"]].append(sample)
    summary = []
    for name, samples in grouped.items():
        total = sorted(value for sample in samples for value in sample["total_ms"]["raw_ns"])
        percentile = lambda p: round(total[max(0, math.ceil(len(total) * p) - 1)] / 1e6, 3) if total else None
        frames = sum(sample["frames"] for sample in samples)
        misses = sum(sample["deadline_misses"] for sample in samples)
        cpu_ms = sum(sample["cpu_ms"] for sample in samples)
        duration = sum(sample["duration_ms"] for sample in samples)
        row = {
            "scenario": name, "runs": len(samples), "frames": frames,
            "p50_ms": percentile(.5), "p95_ms": percentile(.95), "p99_ms": percentile(.99),
            "max_ms": round(total[-1] / 1e6, 3) if total else None,
            "deadline_misses": misses, "miss_percent": round(misses * 100 / frames, 2) if frames else None,
            "cpu_one_core_percent": round(cpu_ms * 100 / duration, 2),
            "pss_mb_range": [round(min(s["pss_kb"] for s in samples) / 1024, 1), round(max(s["pss_kb"] for s in samples) / 1024, 1)],
            "gc_count": sum(s["gc_count"] for s in samples),
            "callback_drops": sum(s["callback_drops"] for s in samples),
        }
        summary.append(row)
        print(json.dumps(row), flush=True)
    (OUT / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    (OUT / "meminfo-final.txt").write_bytes(adb("shell", "dumpsys", "meminfo", PACKAGE).stdout)
    (OUT / "gfxinfo-final.txt").write_bytes(adb("shell", "dumpsys", "gfxinfo", PACKAGE).stdout)
    print(f"COMPLETE: {OUT / 'summary.json'}", flush=True)


if __name__ == "__main__":
    main()
