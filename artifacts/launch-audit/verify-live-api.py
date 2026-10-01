"""Verify real Auth/PostgREST with two temporary accounts; never print credentials.

All accounts created here are recorded for cleanup. No real-user account is mutated.
Use --keep-for-device to retain only account A for the emulator session.
"""
from pathlib import Path
import argparse, base64, json, secrets, urllib.error, urllib.parse, urllib.request, uuid

ROOT = Path(__file__).resolve().parents[2]
PRIVATE = ROOT / '.local-release-access'
OUT = ROOT / 'artifacts/launch-audit'
config = json.loads((PRIVATE / 'supabase-test.json').read_text())
properties = dict(line.split('=',1) for line in (ROOT / 'local.properties').read_text().splitlines() if '=' in line and not line.lstrip().startswith(('#','!')))
public_key = properties['SUPABASE_KEY'].strip()
assert properties['SUPABASE_URL'].strip().rstrip('/') == config['url']
parser = argparse.ArgumentParser()
parser.add_argument('--keep-for-device',action='store_true')
options = parser.parse_args()
created = []
checks = []
report = {'status':'FAIL','environment':'Live Supabase Auth and PostgREST','checks':checks}

def request(path,method='GET',body=None,token=None,admin=False,raw=None,content_type='application/json'):
    key = config['adminKey'] if admin else public_key
    headers = {'apikey':key,'Content-Type':content_type}
    if token or admin: headers['Authorization']='Bearer '+(token or key)
    data = raw if raw is not None else json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(config['url']+path,data=data,method=method,headers=headers)
    try:
        with urllib.request.urlopen(req,timeout=30) as response:
            payload=response.read()
            return response.status, json.loads(payload) if payload else None
    except urllib.error.HTTPError as error:
        # Provider messages can include credentials/user identifiers. Return status only.
        return error.code, None

def rpc(name,args,token):
    status,data=request('/rest/v1/rpc/'+name,'POST',args,token)
    assert status in (200,204), (name,status)
    return data

def require_hidden(table,query,token):
    status,data=request('/rest/v1/'+table+'?'+query,token=token)
    assert status in (200,401,403), (table,status)
    assert data in (None,[]), table

try:
    for _ in range(2):
        account={'email':'gratify.audit.'+uuid.uuid4().hex+'@example.invalid','password':secrets.token_hex(24)}
        status,data=request('/auth/v1/admin/users','POST',{**account,'email_confirm':True},admin=True)
        assert status in (200,201), ('create_test_account',status)
        account['id']=data['id']; created.append(account)
        (PRIVATE / 'test-accounts.json').write_text(json.dumps(created))
        status,data=request('/auth/v1/token?grant_type=password','POST',{k:account[k] for k in ('email','password')})
        assert status==200, ('password_login',status)
        account['access_token']=data['access_token']
        (PRIVATE / 'test-accounts.json').write_text(json.dumps(created))
    a,b=created
    checks.append('Two temporary Auth accounts created and signed in using real password tokens')
    track={'video_id':'gratify-audit-track','title':'Audit track','artist':'Audit','duration':60}
    def playlist(token,title,identity):
        return rpc('gratify_replace_cloud_playlist_v2',{'p_sync_id':identity,'p_title':title,'p_thumbnail_url':None,'p_is_public':True,'p_tracks':[track]},token)[0]['playlist_id']
    aid=playlist(a['access_token'],'Audit playlist',a['id'])
    assert len(rpc('gratify_get_cloud_playlist_items',{'p_playlist_id':aid},a['access_token']))==1
    assert rpc('gratify_get_cloud_playlist_items',{'p_playlist_id':aid},b['access_token'])==[]
    assert rpc('gratify_get_cloud_playlist_items',{'p_playlist_id':aid},None)==[]
    require_hidden('cloud_playlists','id=eq.'+aid,b['access_token'])
    checks.append('Actual JWT owners can read their playlist; account B and anonymous cannot read A private playlist or snapshot')
    bid=playlist(b['access_token'],'B playlist',a['id'])
    assert bid!=aid
    rpc('gratify_set_profile_privacy',{'p_setting':'show_playlists','p_visible':True},a['access_token'])
    assert len(rpc('gratify_get_cloud_playlist_items',{'p_playlist_id':aid},None))==1
    rpc('gratify_set_profile_privacy',{'p_setting':'show_playlists','p_visible':False},a['access_token'])
    checks.append('Owner identities remain separate; explicit privacy choices control anonymous publication')
    rpc('gratify_apply_library_changes',{'p_changes':[{'kind':'user_liked_songs','item_id':'gratify-audit-liked','enabled':True,'payload':{'title':'Audit liked','duration':60}}]},a['access_token'])
    require_hidden('user_liked_songs','user_id=eq.'+a['id'],b['access_token'])
    status,_=request('/rest/v1/cloud_playlists?id=eq.'+aid,'DELETE',token=b['access_token'])
    assert status in (401,403), ('legacy_direct_delete',status)
    checks.append('Library data cannot be read by a second account and direct playlist deletion is revoked')
    rpc('gratify_delete_owned_playlist_v2',{'p_sync_id':a['id']},a['access_token'])
    status,_=request('/rest/v1/rpc/gratify_replace_cloud_playlist_v2','POST',{'p_sync_id':a['id'],'p_title':'Stale','p_thumbnail_url':None,'p_is_public':False,'p_tracks':[]},a['access_token'])
    assert status>=400
    assert len(rpc('gratify_get_cloud_playlist_items',{'p_playlist_id':bid},b['access_token']))==1
    checks.append('Deleted identities cannot be resurrected; account B data remains intact')
    rpc('gratify_prepare_account_deletion',{},b['access_token'])
    rpc('gratify_delete_account',{},b['access_token'])
    status,_=request('/auth/v1/admin/users/'+b['id'],admin=True)
    assert status==404, ('deleted_auth_account',status)
    status,_=request('/auth/v1/user',token=a['access_token'])
    assert status==200
    checks.append('Account deletion RPC removes B Auth record while A session remains valid')
    report['status']='PASS'
except Exception as error:
    report['error_type']=type(error).__name__
    if isinstance(error,AssertionError): report['failed_check']=str(error)
finally:
    kept=[]
    for index,account in enumerate(created):
        if options.keep_for_device and report['status']=='PASS' and index==0:
            kept.append(account); continue
        status,_=request('/auth/v1/admin/users/'+account['id'],'DELETE',admin=True)
        if status not in (200,204,404): report.setdefault('cleanup_failed',[]).append(index)
    (PRIVATE / 'test-accounts.json').write_text(json.dumps(kept))
    report['temporary_accounts_retained_for_device']=len(kept)
    (OUT / 'live-api-verification.json').write_text(json.dumps(report,indent=2))
    print(json.dumps(report,indent=2))
if report['status']!='PASS': raise SystemExit(1)
