"""Send the single user-authorized recovery test; refuse a second attempt."""
from pathlib import Path
import argparse, json, secrets, urllib.request, urllib.error, urllib.parse, uuid

ROOT = Path(__file__).resolve().parents[2]
PRIVATE = ROOT / '.local-release-access/recovery-account.json'
REPORT = ROOT / 'artifacts/launch-audit/recovery-email-verification.json'
parser = argparse.ArgumentParser()
parser.add_argument('--send-authorized-once', action='store_true')
options = parser.parse_args()
if not options.send_authorized_once:
    raise SystemExit('No email sent. A single explicit user authorization is required.')
config = json.loads((ROOT / '.local-release-access/supabase-test.json').read_text())
properties = dict(l.split('=', 1) for l in (ROOT / 'local.properties').read_text().splitlines() if '=' in l and not l.lstrip().startswith(('#', '!')))
if REPORT.exists() and json.loads(REPORT.read_text()).get('send_attempts', 0) >= 1:
    raise SystemExit('Single authorized attempt already recorded; no further email sent.')

def request(path, body, admin=False):
    key = config['adminKey'] if admin else properties['SUPABASE_KEY'].strip()
    headers = {'apikey': key, 'Content-Type': 'application/json'}
    if admin: headers['Authorization'] = 'Bearer ' + key
    req = urllib.request.Request(config['url'] + path, data=json.dumps(body).encode(), headers=headers, method='POST')
    try:
        with urllib.request.urlopen(req, timeout=40) as response:
            data = response.read()
            return response.status, json.loads(data) if data else {}
    except urllib.error.HTTPError as error:
        try: data = json.loads(error.read())
        except Exception: data = {}
        return error.code, data
    except (urllib.error.URLError, OSError, TimeoutError):
        return 0, {}

if PRIVATE.exists():
    account = json.loads(PRIVATE.read_text())
    if account.get('send_attempted'):
        raise SystemExit('Single authorized attempt already recorded; no further email sent.')
else:
    account = {'email': 'supportgratify+audit' + uuid.uuid4().hex[:12] + '@gmail.com', 'password': secrets.token_hex(24)}
    status, data = request('/auth/v1/admin/users', {**account, 'email_confirm': True}, admin=True)
    if status not in (200, 201):
        raise SystemExit('Disposable alias account creation failed; no recovery email attempted.')
    account['id'] = data['id']
    PRIVATE.write_text(json.dumps(account))

# Persist before the request: an ambiguous network outcome must never cause resend.
account['send_attempted'] = True
PRIVATE.write_text(json.dumps(account))
redirect = 'com.tan.gratify://login-callback?flow=recovery'
status, data = request('/auth/v1/recover?redirect_to=' + urllib.parse.quote(redirect, safe=''), {'email': account['email']})
report = {
    'status': 'REQUEST_ACCEPTED_RECEIPT_PENDING' if status == 200 else 'OUTCOME_UNKNOWN_NO_RESEND' if status == 0 else 'REQUEST_REJECTED',
    'http_status': status,
    'error_code': data.get('error_code', data.get('code')),
    'recipient': 'Gmail alias of supportgratify@gmail.com',
    'authorized_send_attempts': 1,
    'send_attempts': 1,
    'redirect_matches_app': True,
    'main_account_password_changed': False,
    'delivery_verified': False,
    'temporary_account_cleanup_pending': True,
}
REPORT.write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report, indent=2))
