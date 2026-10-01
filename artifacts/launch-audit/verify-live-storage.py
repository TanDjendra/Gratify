"""Test avatar Storage boundaries with disposable accounts and one synthetic image."""
from pathlib import Path
import base64, json, secrets, urllib.request, urllib.error, uuid

ROOT=Path(__file__).resolve().parents[2]
PRIVATE=ROOT/'.local-release-access'
config=json.loads((PRIVATE/'supabase-test.json').read_text())
properties=dict(l.split('=',1) for l in (ROOT/'local.properties').read_text().splitlines() if '=' in l and not l.lstrip().startswith(('#','!')))
a=None
b=None
created=[]
image=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl6Z1EAAAAASUVORK5CYII=')
report={'status':'FAIL','checks':[]}

def request(path,method='GET',body=None,token=None,admin=False,raw=None,content_type='image/png'):
    key=config['adminKey'] if admin else properties['SUPABASE_KEY'].strip()
    headers={'apikey':key,'Content-Type':content_type if raw is not None else 'application/json','x-upsert':'true'}
    if token or admin:headers['Authorization']='Bearer '+(token or key)
    q=urllib.request.Request(config['url']+path,data=raw if raw is not None else json.dumps(body).encode() if body is not None else None,method=method,headers=headers)
    try:
        with urllib.request.urlopen(q,timeout=30) as f:
            data=f.read()
            return f.status,data if raw is not None or '/object/public/' in path or '/object/authenticated/' in path else json.loads(data) if data else None
    except urllib.error.HTTPError as e:return e.code,None
    except (urllib.error.URLError,TimeoutError,OSError):return 0,None

def create_account():
    account={'email':'gratify.storage.'+uuid.uuid4().hex+'@example.invalid','password':secrets.token_hex(24)}
    status,data=request('/auth/v1/admin/users','POST',{**account,'email_confirm':True},admin=True)
    assert status in (200,201),('create',status)
    account['id']=data['id'];created.append(account)
    (PRIVATE/'storage-account.json').write_text(json.dumps(created))
    status,data=request('/auth/v1/token?grant_type=password','POST',{k:account[k] for k in ('email','password')})
    assert status==200,('login',status)
    account['access_token']=data['access_token']
    (PRIVATE/'storage-account.json').write_text(json.dumps(created))
    return account

try:
    a=create_account()
    b=create_account()
    path='/storage/v1/object/avatars/'+a['id']+'.jpg'
    status,_=request(path,'POST',token=a['access_token'],raw=image)
    assert status in (200,201),('owner_upload',status)
    report['checks'].append('Owner avatar upload succeeds')
    status,private_download=request('/storage/v1/object/authenticated/avatars/'+a['id']+'.jpg',token=a['access_token'])
    assert status==200 and private_download==image,('owner_authenticated_read',status)
    status,_=request(path,'POST',token=a['access_token'],raw=image)
    assert status in (200,201),('owner_upsert',status)
    report['checks'].append('Owner can read and replace their avatar')
    status,_=request('/storage/v1/object/avatars/'+a['id']+'-wrong.jpg','POST',token=a['access_token'],raw=image)
    assert status in (400,401,403),('owner_wrong_path',status)
    status,_=request(path,'POST',token=a['access_token'],raw=image,content_type='application/pdf')
    assert status in (400,403,415),('unsupported_mime',status)
    status,_=request(path,'POST',token=a['access_token'],raw=b'0'*(5242880+1))
    assert status in (400,413),('oversized_avatar',status)
    report['checks'].append('Owner cannot use another path, unsupported MIME or a file over 5 MB')
    status,_=request(path,'POST',token=b['access_token'],raw=image)
    assert status in (400,401,403),('other_account_overwrite',status)
    status,_=request('/storage/v1/object/avatars','DELETE',{'prefixes':[a['id']+'.jpg']},b['access_token'])
    status,download=request('/storage/v1/object/public/avatars/'+a['id']+'.jpg')
    assert status==200 and download==image,('other_account_delete_or_public_read',status)
    report['checks'].append('Second account cannot overwrite/delete the owner avatar; public read follows the public avatar bucket')
    status,_=request(path,'POST',raw=image)
    assert status in (400,401,403),('anonymous_overwrite',status)
    report['checks'].append('Anonymous avatar overwrite is denied')
    status,removed=request('/storage/v1/object/avatars','DELETE',{'prefixes':[a['id']+'.jpg']},a['access_token'])
    assert status==200 and isinstance(removed,list) and any(x.get('name')==a['id']+'.jpg' for x in removed),('owner_delete',status)
    status,listed=request('/storage/v1/object/list/avatars','POST',{'prefix':a['id']+'.jpg','limit':10},a['access_token'])
    assert status==200 and listed==[],('deleted_metadata',status)
    status,_=request('/storage/v1/object/authenticated/avatars/'+a['id']+'.jpg?cacheNonce='+uuid.uuid4().hex,token=a['access_token'])
    assert status in (400,404),('deleted_origin_object',status)
    public_status,_=request('/storage/v1/object/public/avatars/'+a['id']+'.jpg')
    report['public_cached_read_after_delete_status']=public_status
    report['cache_reference']='https://supabase.com/docs/guides/storage/cdn/smart-cdn#bypassing-cache'
    report['cache_caveat']='Previously requested public avatar URLs may remain cached. Origin read and metadata are verified separately; immediate removal from all caches is not claimed.'
    report['checks'].append('Owner deletion returns the owned object; authenticated origin read and metadata confirm removal')
    report['status']='PASS'
except Exception as e:
    report['error_type']=type(e).__name__
    if isinstance(e,AssertionError):report['failed_check']=str(e)
finally:
    cleanup_ok=True
    for account in created:
        request('/storage/v1/object/avatars','DELETE',{'prefixes':[account['id']+'.jpg']},admin=True)
        if account.get('access_token'):
            request('/rest/v1/rpc/gratify_prepare_account_deletion','POST',{},account['access_token'])
            request('/rest/v1/rpc/gratify_delete_account','POST',{},account['access_token'])
        status,_=request('/auth/v1/admin/users/'+account['id'],'DELETE',admin=True)
        cleanup_ok=cleanup_ok and status in (200,204,404)
    report['temporary_account_cleanup_ok']=cleanup_ok
    if cleanup_ok:(PRIVATE/'storage-account.json').write_text('[]')
    else:report['status']='FAIL_CLEANUP_PENDING'
    (ROOT/'artifacts/launch-audit/live-storage-verification.json').write_text(json.dumps(report,indent=2))
    print(json.dumps(report,indent=2))
if report['status']!='PASS':raise SystemExit(1)
