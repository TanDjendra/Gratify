"""Prepare the APK/update metadata and record evidence before any public upload."""
from pathlib import Path
import hashlib, importlib.util, json, os, shutil, subprocess, sys, xml.etree.ElementTree as ET, zipfile

out = Path(__file__).resolve().parent
root = out.parents[1]
bt = Path('C:/Users/LENOVO/AppData/Local/Android/Sdk/build-tools/37.0.0')
env = dict(os.environ, JAVA_HOME='D:/Tan/APK/JDK')
env['PATH'] = env['JAVA_HOME'] + '/bin;' + env['PATH']
assert 'BUILD SUCCESSFUL' in (out / 'build-final2.log').read_text(errors='replace')
suites = [ET.parse(path).getroot() for path in (root / 'composeApp/build/test-results/jvmTest').glob('TEST-*.xml')]
tests = {key: sum(int(suite.attrib.get(key, 0)) for suite in suites) for key in ('tests', 'failures', 'errors', 'skipped')}
assert tests == {'tests': 13, 'failures': 0, 'errors': 0, 'skipped': 0}
signed = json.loads((out / 'release-apks.json').read_text())
for record in signed:
    assert hashlib.sha256(Path(record['path']).read_bytes()).hexdigest() == record['sha256']
    assert record['release_signature_verified'] and not record['debuggable']

source = json.loads((out / 'source-snapshot.json').read_text())
archive = out / source['archive']
assert hashlib.sha256(archive.read_bytes()).hexdigest() == source['sha256']
with zipfile.ZipFile(archive) as package:
    assert package.testzip() is None
    for relative in source['source_files']:
        assert package.read('Gratify-2.1.5/' + relative) == (root / relative).read_bytes()
    policy = package.read('Gratify-2.1.5/core/domain/src/commonMain/kotlin/com/tan/domain/utils/PlaylistFeaturePolicy.kt')
    assert b'experimentalEnabled = false' in policy
    about = package.read('Gratify-2.1.5/composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/home/AboutSupportContent.kt')
    assert b'https://tanheradhe.my.id' in about and b'github.com/maxrave-dev/SimpMusic' not in about
    assert b'about_upstream_credit' in about

sys.path.insert(0, str(root / 'scripts'))
spec = importlib.util.spec_from_file_location('prepare', root / 'scripts/prepare-update-release.py')
prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)
os.environ.update(JAVA_HOME=env['JAVA_HOME'], PATH=env['PATH'])
universal = out / 'Gratify-2.1.5-release-universal.apk'
metadata = prepare.inspect_apk(universal, bt / 'aapt2.exe', bt / 'apksigner.bat',
    signed[0]['certificate_sha256'], 65, 'com.tan.gratify.next')
assert metadata['versionCode'] == 66 and metadata['versionName'] == '2.1.5'
shutil.copyfile(universal, out / 'Gratify.apk')
assert hashlib.sha256((out / 'Gratify.apk').read_bytes()).hexdigest() == metadata['sha256']
(out / 'gratify-update.json').write_text(json.dumps(metadata, indent=2) + '\n', encoding='utf-8')
names = ('Gratify.apk', 'Gratify-2.1.5-release-arm64-v8a.apk', 'gratify-update.json', 'Gratify-2.1.5-source.zip')
checksums = ''.join(hashlib.sha256((out / name).read_bytes()).hexdigest() + '  ' + name + '\n' for name in names)
(out / 'SHA256SUMS.txt').write_text(checksums, encoding='utf-8')
assets = {name: {'size': (out / name).stat().st_size,
    'digest': 'sha256:' + hashlib.sha256((out / name).read_bytes()).hexdigest()} for name in (*names, 'SHA256SUMS.txt')}
verification = {'version_name': '2.1.5', 'version_code': 66, 'build_successful': True, 'tests': tests,
    'experimental_playlist_features_enabled': False, 'release_apks': signed, 'public_assets': assets,
    'developer_site': 'https://tanheradhe.my.id/', 'developer_site_http_status': 200,
    'separate_simpmusic_link_removed': True, 'simpmusic_credit_and_license_retained': True,
    'matching_android_source_snapshot': source['archive'],
    'release_runtime_or_long_playback_tested_this_round': False, 'uploaded_to_github': False}
(out / 'verification.json').write_text(json.dumps(verification, indent=2), encoding='utf-8')
print(json.dumps({'version': '2.1.5', 'tests_passed': tests['tests'], 'public_assets_verified': len(assets)}))
