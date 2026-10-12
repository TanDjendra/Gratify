"""Upload verified local artifacts, then publish the user-requested GitHub release."""
from pathlib import Path
import hashlib, json, os, subprocess, sys, urllib.error, urllib.parse, urllib.request

out = Path(__file__).resolve().parent
base = 'https://api.github.com/repos/TanDjendra/Gratify'
tag = 'v2.1.5'
env = dict(os.environ, GIT_TERMINAL_PROMPT='0', GCM_INTERACTIVE='never')
credentials = subprocess.run(['git', 'credential', 'fill'],
    input='protocol=https\nhost=github.com\n\n', capture_output=True, text=True, env=env, timeout=20)
entries = dict(line.split('=', 1) for line in credentials.stdout.splitlines() if '=' in line)
token = entries.get('password')
if not token:
    raise SystemExit('GitHub authentication unavailable; no release changed.')
headers = {'Authorization': 'Bearer ' + token, 'Accept': 'application/vnd.github+json',
           'User-Agent': 'Gratify-release', 'X-GitHub-Api-Version': '2026-03-10'}

def request(url, method='GET', payload=None, content_type=None):
    assert urllib.parse.urlparse(url).hostname in {'api.github.com', 'uploads.github.com'}
    data = json.dumps(payload).encode() if isinstance(payload, dict) else payload
    chosen = dict(headers)
    if data is not None:
        chosen['Content-Type'] = content_type or 'application/json'
    try:
        with urllib.request.urlopen(urllib.request.Request(url, data=data, headers=chosen, method=method), timeout=180) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        raise RuntimeError('GitHub request failed: HTTP ' + str(error.code)) from None

def optional_release():
    url = base + '/releases/tags/' + tag
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=30) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return None
        raise RuntimeError('GitHub release lookup failed: HTTP ' + str(error.code)) from None

if len(sys.argv) > 1 and sys.argv[1] == 'check':
    repo = request(base)
    release = optional_release()
    print(json.dumps({'repository': repo['full_name'], 'push_access': repo['permissions']['push'],
                      'target_release_exists': release is not None}))
    raise SystemExit(0)

verification = json.loads((out / 'verification.json').read_text())
assert verification['version_name'] == '2.1.5' and verification['version_code'] == 66
assert verification['build_successful'] and verification['tests']['failures'] == 0
assert not verification['experimental_playlist_features_enabled']
files = [out / name for name in verification['public_assets']]
assert all(file.is_file() and file.resolve().parent == out.resolve() for file in files)
expected = {file.name: {'size': file.stat().st_size,
                      'digest': 'sha256:' + hashlib.sha256(file.read_bytes()).hexdigest()} for file in files}
assert expected == verification['public_assets']
body = (out / 'release-notes.md').read_text(encoding='utf-8')
release = optional_release()
if release is None:
    # Existing main remains unchanged; matching actual Android source is a named release asset.
    release = request(base + '/releases', 'POST', {'tag_name': tag, 'target_commitish': 'main',
        'name': 'Gratify 2.1.5', 'body': body, 'draft': True, 'prerelease': False})
elif not release['draft']:
    raise SystemExit('Release is already published; no existing public release will be overwritten.')

record = {'id': release['id'], 'url': release['html_url'], 'tag': tag, 'published': False}
(out / 'github-publication.json').write_text(json.dumps(record, indent=2), encoding='utf-8')
print(json.dumps({'draft_prepared': tag}), flush=True)

upload_url = release['upload_url'].split('{', 1)[0]
for file in files:
    existing = next((asset for asset in release['assets'] if asset['name'] == file.name), None)
    if existing is None:
        mime = 'application/vnd.android.package-archive' if file.suffix == '.apk' else (
            'application/zip' if file.suffix == '.zip' else 'application/json' if file.suffix == '.json' else 'text/plain')
        asset = request(upload_url + '?name=' + urllib.parse.quote(file.name), 'POST', file.read_bytes(), mime)
    else:
        asset = existing
    assert asset['state'] == 'uploaded' and asset['size'] == expected[file.name]['size']
    assert asset.get('digest') == expected[file.name]['digest'], 'Uploaded asset hash mismatch; publication stopped.'
    print(json.dumps({'uploaded': file.name, 'sha256_verified': True}), flush=True)

uploaded = request(base + '/releases/' + str(release['id']))
assert {asset['name']: {'size': asset['size'], 'digest': asset.get('digest')} for asset in uploaded['assets']} == expected
published = request(base + '/releases/' + str(release['id']), 'PATCH',
    {'draft': False, 'prerelease': False, 'make_latest': 'true', 'name': 'Gratify 2.1.5', 'body': body})
assert not published['draft'] and not published['prerelease']
assert request(base + '/releases/latest')['tag_name'] == tag
record.update(published=True, published_at=published['published_at'],
              assets=[{'name': asset['name'], 'url': asset['browser_download_url'],
                       'size': asset['size'], 'sha256': asset['digest']} for asset in published['assets']])
(out / 'github-publication.json').write_text(json.dumps(record, indent=2), encoding='utf-8')
print(json.dumps({'published_release': record['url'], 'assets': len(record['assets'])}), flush=True)
