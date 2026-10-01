"""Test avatar Storage boundaries with disposable accounts and one synthetic image."""
from pathlib import Path
import base64, json, secrets, urllib.request, urllib.error, uuid

ROOT=Path(__file__).resolve().parents[2]
PRIVATE=ROOT/'.local-release-access'
config=json.loads((PRIVATE/'supabase-test.json').read_text())
properties=dict(l.split('=',1) for l in (ROOT/'local.properties').read_text().splitlines() if '=' in l and not l.lstrip().startswith(('#','!')))
a=json.loads((PRIVATE/'test-accounts.json').read_text())[0]
b=None
image=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl6Z1EAAAAASUVORK5CYII=')
report={'status':'FAIL','checks':[]}

def request(path,method='GET',body=None,token=None,admin=False,raw=None):
    key=config['adminKey'] if admin else properties['SUPABASE_KEY'].strip()
    headers={'apikey':key,'Content-Type':'image/png' if raw is not None else 'application/json','x-upsert':'true'}
    if token or admin:headers['Authorization']='Bearer '+(token or key)
    q=urllib.request.Request(config['url']+path,data=raw if raw is not None else json.dumps(body).encode() if body is not None else None,method=method,headers=headers)
    try:
        with urllib.request.urlopen(q,timeout=30) as f:
            data=f.read()
            return f.status,data if raw is not None or '/object/public/' in path else json.loads(data) if data else None
    except urllib.error.HTTPError as e:return e.code,None

try:
    b={'email':'gratify.storage.'+uuid.uuid4().hex+'@example.invalid','password':secrets.token_hex(24)}
    status,data=request('/auth/v1/admin/users','POST',{**b,'email_confirm':True},admin=True)
    assert status in (200,201),('create',status)
    b['id']=data['id'];(PRIVATE/'storage-account.json').write_text(json.dumps(b))
    status,data=request('/auth/v1/token?grant_type=password','POST',{k:b[k] for k in ('email','password')})
    assert status==200,('login',status)
    b['access_token']=data['access_token'];(PRIVATE/'storage-account.json').write_text(json.dumps(b))
    path='/storage/v1/object/avatars/'+a['id']+'.jpg'
    status,_=request(path,'POST',token=a['access_token'],raw=image)
    assert status in (200,201),('owner_upload',status)
    report['checks'].append('Owner avatar upload succeeds')
    status,_=request(path,'POST',token=b['access_token'],raw=image)
    assert status in (400,401,403),('other_account_overwrite',status)
    status,_=request('/storage/v1/object/avatars','DELETE',{'prefixes':[a['id']+'.jpg']},b['access_token'])
    status,download=request('/storage/v1/object/public/avatars/'+a['id']+'.jpg')
    assert status==200 and download==image,('other_account_delete_or_public_read',status)
    report['checks'].append('Second account cannot overwrite/delete the owner avatar; public read follows the public avatar bucket')
    status,_=request(path,'POST',raw=image)
    assert status in (400,401,403),('anonymous_overwrite',status)
    report['checks'].append('Anonymous avatar overwrite is denied')
    status,_=request('/storage/v1/object/avatars','DELETE',{'prefixes':[a['id']+'.jpg']},a['access_token'])
    assert status==200,('owner_delete',status)
    status,_=request('/storage/v1/object/public/avatars/'+a['id']+'.jpg')
    assert status in (400,404),('deleted_object',status)
    report['checks'].append('Owner Storage deletion removes the synthetic image')
    report['status']='PASS'
except Exception as e:
    report['error_type']=type(e).__name__
    if isinstance(e,AssertionError):report['failed_check']=str(e)
finally:
    request('/storage/v1/object/avatars','DELETE',{'prefixes':[a['id']+'.jpg']},admin=True)
    if b and b.get('id'):
        if b.get('access_token'):request('/rest/v1/rpc/gratify_delete_account','POST',{},b['access_token'])
        status,_=request('/auth/v1/admin/users/'+b['id'],'DELETE',admin=True)
        report['temporary_account_cleanup_ok']=status in (200,204,404)
    (ROOT/'artifacts/launch-audit/live-storage-verification.json').write_text(json.dumps(report,indent=2))
    print(json.dumps(report,indent=2))
if report['status']!='PASS':raise SystemExit(1)
