"""Run the exact Android ImageCompressor source on a device, without building/installing the app.

Requires Java, Android SDK build-tools/platform 36, adb, and the project's cached
Kotlin 2.4.0/Koin 4.2.2/coroutines 1.11.0 dependencies. No production source is
rewritten or stubbed. The test Context only supplies a real resolver for file URIs.
Usage: python scripts/test-android-image-compressor.py --log-dir artifacts/image-compressor/red
"""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CACHE = Path.home() / ".gradle/caches/modules-2/files-2.1"


def dependency(group, name, version):
    return next((CACHE / group / name / version).glob("*/*.jar"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--log-dir", type=Path, required=True)
    parser.add_argument("--sdk", type=Path, default=Path(os.environ.get("ANDROID_HOME", Path.home() / "AppData/Local/Android/Sdk")))
    parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL"))
    args = parser.parse_args()
    args.log_dir.mkdir(parents=True, exist_ok=True)
    log = (args.log_dir / "device-tests.log").open("w", encoding="utf-8")

    def report(text):
        print(text, flush=True)
        log.write(text + "\n")
        log.flush()

    def run(command):
        report("COMMAND: " + subprocess.list2cmdline([str(x) for x in command]))
        result = subprocess.run([str(x) for x in command], cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace")
        report(result.stdout)
        report(f"EXIT: {result.returncode}")
        if result.returncode:
            raise subprocess.CalledProcessError(result.returncode, command)
        return result.stdout

    java = shutil.which("java")
    sdk = args.sdk
    adb = [sdk / "platform-tools/adb.exe"] if os.name == "nt" else [sdk / "platform-tools/adb"]
    if args.serial:
        adb += ["-s", args.serial]
    stdlib = dependency("org.jetbrains.kotlin", "kotlin-stdlib", "2.4.0")
    reflect = dependency("org.jetbrains.kotlin", "kotlin-reflect", "2.4.0")
    coroutines = dependency("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.11.0")
    koin = dependency("io.insert-koin", "koin-core-jvm", "4.2.2")
    annotations = dependency("org.jetbrains", "annotations", "26.1.0")
    compiler = [dependency("org.jetbrains.kotlin", name, "2.4.0") for name in (
        "kotlin-compiler-embeddable", "kotlin-build-tools-api", "kotlin-script-runtime", "kotlin-daemon-embeddable")]
    platform = sdk / "platforms/android-36"
    android = platform / "android.jar"
    runtime = [stdlib, coroutines, koin, annotations]
    compiler += [reflect]
    source = ROOT / "composeApp/src/androidMain/kotlin/com/tan/gratify/utils/ImageCompressor.kt"
    common = ROOT / "composeApp/src/commonMain/kotlin/com/tan/gratify/utils/ImageCompressor.kt"
    test = ROOT / "composeApp/src/androidDeviceTest/kotlin/com/tan/gratify/utils/ImageCompressorRegressionTest.kt"
    compiled = args.log_dir.resolve() / "compiled.jar"
    dex = args.log_dir.resolve() / "dex"
    dex.mkdir(exist_ok=True)
    remote = "/data/local/tmp/gratify-image-compressor-regression"
    try:
        report(f"PRODUCTION SHA256: {hashlib.sha256(source.read_bytes()).hexdigest()}")
        run(adb + ["devices"])
        run(adb + ["shell", "getprop ro.build.version.sdk"])
        run([java, "-cp", os.pathsep.join(str(p) for p in compiler + runtime), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
             "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-Xmulti-platform", f"-Xcommon-sources={common}",
             "-classpath", os.pathsep.join(str(p) for p in runtime + [android, platform / "optional/android.test.mock.jar"]),
             "-d", compiled, common, source, test])
        run([java, "-cp", sdk / "build-tools/37.0.0/lib/d8.jar", "com.android.tools.r8.D8", "--min-api", "26",
             "--lib", android, "--output", dex, compiled, *runtime])
        archive = args.log_dir.resolve() / "regression.jar"
        with zipfile.ZipFile(archive, "w") as output:
            for path in dex.glob("*.dex"):
                output.write(path, path.name)
        run(adb + ["shell", f"mkdir -p {remote}"])
        run(adb + ["push", archive, remote + "/regression.jar"])
        entry = (f"CLASSPATH={remote}/regression.jar:/system/framework/android.test.mock.jar:/system/framework/android.test.base.jar "
                 "app_process /system/bin com.tan.gratify.utils.ImageCompressorRegressionTest")
        run(adb + ["shell", f"{entry} prepare {remote}"])
        run(adb + ["shell", f"{entry} test {remote}"])
        report("RESULT: GREEN")
        return 0
    except subprocess.CalledProcessError as error:
        report(f"RESULT: FAILED (exit {error.returncode}; inspect assertion vs tooling error)")
        return 1
    finally:
        # Only remove the isolated test directory; never touch app data or app installation.
        subprocess.run([str(x) for x in adb + ["shell", f"rm -rf {remote}"]], stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        log.close()


if __name__ == "__main__":
    sys.exit(main())
