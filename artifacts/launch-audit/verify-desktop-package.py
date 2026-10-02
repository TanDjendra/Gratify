"""Verify the Windows release package without running installers or reading account data."""
from datetime import datetime, timezone
from pathlib import Path
import hashlib
import json
import xml.etree.ElementTree as ET
import zipfile
import argparse

root = Path(__file__).resolve().parents[2]
evidence = root / 'artifacts/launch-audit'
package = root / 'desktopApp/build/compose/binaries/main-release/app/Gratify'
parser = argparse.ArgumentParser()
parser.add_argument('--build-log', default='desktop-final-build.log')
options = parser.parse_args()
build_log = evidence / options.build_log
assert 'BUILD SUCCESSFUL' in build_log.read_text(encoding='utf-8', errors='replace')
exe = package / 'Gratify.exe'
assert exe.is_file() and exe.stat().st_size > 0
assert (package / 'runtime/bin/server/jvm.dll').is_file()
vlc = package / 'app/resources/vlc'
assert (vlc / 'libvlc.dll').is_file() and (vlc / 'libvlccore.dll').is_file()
assert (vlc / 'COPYING.txt').is_file() and (vlc / 'AUTHORS.txt').is_file()
plugins = list((vlc / 'plugins').rglob('*.dll'))
assert len(plugins) > 30, 'Incomplete bundled player'
cfg = (package / 'app/Gratify.cfg').read_text(encoding='utf-8')
assert 'app.mainclass=com.tan.gratify.MainKt' in cfg
classpath = []
for line in cfg.splitlines():
    if line.startswith('app.classpath='):
        path = Path(line.split('=', 1)[1].replace('$APPDIR', str(package / 'app')))
        assert path.is_file(), f'Missing packaged classpath file: {path.name}'
        classpath.append({'name': path.name, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
documents = {}
for name in ('terms-of-use.md', 'privacy-policy.md'):
    expected = (root / 'docs/legal' / name).read_bytes()
    found = []
    for jar in (package / 'app').glob('*.jar'):
        with zipfile.ZipFile(jar) as archive:
            for entry in archive.namelist():
                if entry.endswith('/legal/' + name):
                    assert archive.read(entry) == expected, f'Stale desktop legal document: {name}'
                    found.append(jar.name)
    assert len(found) == 1, f'Missing or duplicate desktop legal document: {name}'
    documents[name] = hashlib.sha256(expected).hexdigest()
suites = []
for xml in (root / 'composeApp/build/test-results/jvmTest').glob('TEST-*.xml'):
    suite = ET.parse(xml).getroot()
    suites.append({'name': suite.attrib['name'], 'tests': int(suite.attrib['tests']),
        'failures': int(suite.attrib['failures']), 'errors': int(suite.attrib['errors'])})
assert sum(s['failures'] + s['errors'] for s in suites) == 0
desktop = next(s for s in suites if s['name'].startswith('DesktopDeepLinkTest'))
assert desktop['tests'] == 10
google = next((s for s in suites if s['name'].startswith('GoogleLoginControllerTest')), None)
if options.build_log == 'google-login-fix-build.log':
    assert google and google['tests'] == 6, 'Missing Google login regression checks'
report = {
    'time_utc': datetime.now(timezone.utc).isoformat(), 'status': 'PACKAGE_AND_REGRESSION_TESTS_VERIFIED',
    'package': str(package.relative_to(root)), 'exe_sha256': hashlib.sha256(exe.read_bytes()).hexdigest(),
    'build_log': build_log.name, 'bundled_runtime': True, 'bundled_vlc': True,
    'vlc_plugins': len(plugins), 'jar_count': len(classpath), 'classpath': classpath,
    'packaged_legal_source_sha256': documents, 'legal_status': 'DRAFT_NOT_OFFICIAL',
    'desktop_regression_tests': desktop['tests'], 'compose_jvm_tests': sum(s['tests'] for s in suites),
    'google_login_regression_tests': google['tests'] if google else 0,
    'failures': 0, 'native_ui_verified': False,
    'desktop_sentry_event_verified': False, 'full_desktop_uat_verified': False,
    'scope': 'Local Windows release packaging; SDK callbacks tested against a disposable local HTTP server, not a real mailbox or live Supabase reset.',
}
(evidence / 'desktop-verification.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps({k: report[k] for k in ('status', 'vlc_plugins', 'jar_count', 'desktop_regression_tests', 'compose_jvm_tests', 'failures')}))
