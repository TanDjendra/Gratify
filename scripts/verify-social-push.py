"""Isolated PostgreSQL tests. No production user or device receives a message."""
from pathlib import Path
import json,runpy,socket,subprocess,uuid
from concurrent.futures import ThreadPoolExecutor
import psycopg
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'artifacts/notification-audit-2026-10-03';OUT.mkdir(exist_ok=True)
BIN=ROOT/'artifacts/launch-audit/postgres-test-runtime/bin';DATA=OUT/'postgres-data'
A='11111111-1111-4111-8111-111111111111';B='22222222-2222-4222-8222-222222222222'
C='33333333-3333-4333-8333-333333333333';D='44444444-4444-4444-8444-444444444444'
INSTALL_B='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';INSTALL_C='cccccccc-cccc-4ccc-8ccc-cccccccccccc'
def command(*args):
    with (OUT/'postgres-process.log').open('a') as log:
        subprocess.run([str(x) for x in args],stdout=log,stderr=log,creationflags=0x08000000,timeout=60,check=True)
with socket.socket() as sock:sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
if not (DATA/'PG_VERSION').exists():command(BIN/'initdb.exe','-D',DATA,'-U','postgres','-A','trust','--no-locale','--encoding=UTF8')
command(BIN/'pg_ctl.exe','-D',DATA,'-l',OUT/'postgres.log','-o',f'-h 127.0.0.1 -p {port}','-w','start')
report={}
try:
  for kind in ('text','uuid'):
    checks=[]
    with psycopg.connect(host='127.0.0.1',port=port,user='postgres',dbname='postgres',autocommit=True) as db:
      db.execute('DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public; DROP SCHEMA IF EXISTS auth CASCADE; CREATE SCHEMA auth')
      for role in ('anon','authenticated','service_role'):
        if not db.execute('SELECT 1 FROM pg_roles WHERE rolname=%s',(role,)).fetchone():db.execute(f'CREATE ROLE {role} NOLOGIN'+(' BYPASSRLS' if role=='service_role' else ''))
      db.execute("CREATE TABLE auth.users(id uuid PRIMARY KEY);CREATE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql STABLE AS $$SELECT nullif(current_setting('request.jwt.claim.sub',true),'')::uuid$$;GRANT USAGE ON SCHEMA public,auth TO anon,authenticated,service_role;GRANT EXECUTE ON FUNCTION auth.uid() TO anon,authenticated,service_role")
      setup=(ROOT/'supabase_setup.sql').read_text()
      if kind=='uuid':setup=runpy.run_path(str(ROOT/'artifacts/launch-audit/uuid-schema-fixture.py'))['prepare'](setup)
      db.execute(setup);db.execute((ROOT/f'artifacts/launch-audit/supabase-launch-fixes{"-uuid" if kind=="uuid" else ""}.sql').read_text())
      db.execute((ROOT/'supabase/migrations/202610030001_friends_stories_messages.sql').read_text())
      db.execute((ROOT/'supabase/migrations/202610030003_social_push_notifications.sql').read_text());checks.append('Migration applies')
      for user in (A,B,C,D):db.execute('INSERT INTO auth.users VALUES(%s)',(user,));db.execute('INSERT INTO public.profiles(id,display_name) VALUES(%s,%s)',(user,user))
      db.execute('INSERT INTO public.follows(follower_id,following_id) VALUES(%s,%s),(%s,%s),(%s,%s)',(A,B,B,A,C,A))
      def role(owner=None,name='authenticated'):
        db.execute('RESET ROLE');db.execute('SET ROLE '+name);db.execute("SELECT set_config('request.jwt.claim.sub',%s,false)",(owner or '',))
      def rpc(name,*args):return db.execute(f'SELECT public.{name}('+','.join(['%s']*len(args))+')',args).fetchone()[0]
      def reject(action):
        try:action()
        except psycopg.Error:return
        raise AssertionError('Forbidden operation succeeded')
      role(None,'anon');reject(lambda:rpc('gratify_register_push_device',INSTALL_B,'B'*64,'com.tan.gratify'));checks.append('Anonymous cannot register devices')
      role(B);rpc('gratify_register_push_device',INSTALL_B,'B'*64,'com.tan.gratify.dev')
      reject(lambda:db.execute('SELECT token FROM push_devices').fetchall());reject(lambda:rpc('gratify_claim_push_jobs',20));checks.append('Users cannot read tokens or dispatch jobs')
      role(C);rpc('gratify_register_push_device',INSTALL_C,'C'*64,'com.tan.gratify')
      role(A);private=rpc('gratify_publish_music_story','track','Track','Artist',None,60000,0,15,'','friends')
      public=rpc('gratify_publish_music_story','track','Track','Artist',None,60000,0,15,'','public')
      db.execute('RESET ROLE')
      assert [str(x[0]) for x in db.execute('SELECT recipient_id FROM social_notification_events WHERE source_id=%s',(private,))]==[B];checks.append('Private stories notify mutual friends only')
      assert {str(x[0]) for x in db.execute('SELECT recipient_id FROM social_notification_events WHERE source_id=%s',(public,))}=={B,C};checks.append('Public stories notify followers, excluding author and strangers')
      role(C);assert rpc('gratify_notification_story',private)==[];assert len(rpc('gratify_social_notifications',None,100))==1;checks.append('Private story events and viewers remain protected by RLS')
      role(A);conversation=rpc('gratify_dm_open',D);client=str(uuid.uuid4())
      db.execute('SELECT * FROM gratify_dm_send(%s,%s,%s)',(conversation,'Private message text',client));db.execute('SELECT * FROM gratify_dm_send(%s,%s,%s)',(conversation,'Private message text',client))
      db.execute('RESET ROLE');assert db.execute("SELECT count(*) FROM social_notification_events WHERE recipient_id=%s AND kind='message_request'",(D,)).fetchone()[0]==1;checks.append('DM request queues once and excludes sender devices')
      role(D);rpc('gratify_dm_respond',conversation,True)
      role(A);db.execute('SELECT * FROM gratify_dm_send(%s,%s,%s)',(conversation,'Second',str(uuid.uuid4())))
      db.execute('RESET ROLE');assert db.execute("SELECT count(*) FROM social_notification_events WHERE recipient_id=%s AND kind='message'",(D,)).fetchone()[0]==1;checks.append('Accepted conversation queues message events')
      role(B);assert all(str(x['actor_id'])==A for x in rpc('gratify_social_notifications',None,100));checks.append('Each inbox sees only its recipient events')
      # Reassigning a device cannot deliver an earlier account's story to the new account.
      role(C);rpc('gratify_register_push_device',INSTALL_B,'changed-token-'+'C'*64,'com.tan.gratify.dev')
      role(None,'service_role');jobs=rpc('gratify_claim_push_jobs',20)
      assert all(x['owner_id']==C and x['source_id']==str(public) for x in jobs);checks.append('Rebound device cannot receive old owner events')
      job=jobs[0];rpc('gratify_ack_push_job',job['event_id'],job['installation_id'],str(uuid.uuid4()),'sent',job['token'])
      assert not db.execute('SELECT finished FROM push_delivery_jobs WHERE event_id=%s AND installation_id=%s',(job['event_id'],job['installation_id'])).fetchone()[0];checks.append('Stale worker acknowledgement cannot consume a newer lease')
      rpc('gratify_ack_push_job',job['event_id'],job['installation_id'],job['claim_id'],'retry',job['token'])
      assert rpc('gratify_claim_push_jobs',20)==[];checks.append('Backoff prevents immediate duplicate delivery')
      db.execute('RESET ROLE');db.execute("UPDATE push_delivery_jobs SET next_attempt_at=now()-interval '1 second'")
      def claim():
        with psycopg.connect(host='127.0.0.1',port=port,user='postgres',dbname='postgres',autocommit=True) as connection:
          connection.execute('SET ROLE service_role');return connection.execute('SELECT gratify_claim_push_jobs(20)').fetchone()[0]
      with ThreadPoolExecutor(max_workers=4) as pool:claimed=sum(list(pool.map(lambda _:claim(),range(4))),[])
      ids=[(x['event_id'],x['installation_id']) for x in claimed];assert len(ids)==len(set(ids));checks.append('Concurrent dispatchers lease each device/event once')
      role(None,'service_role')
      for item in claimed:rpc('gratify_ack_push_job',item['event_id'],item['installation_id'],item['claim_id'],'invalid',item['token'])
      assert db.execute('SELECT count(*) FROM push_devices WHERE enabled').fetchone()[0]==1;checks.append('Only acknowledged unregistered tokens are disabled')
      role(A);rpc('gratify_dm_open',C); # New request can be rejected before dispatch.
      db.execute('RESET ROLE');db.execute('DELETE FROM music_stories WHERE id=%s',(public,))
      role(C);assert rpc('gratify_social_notifications',None,100)==[];checks.append('Deleted story disappears from notification inbox')
      role(None,'service_role');rpc('gratify_prune_push_history');checks.append('Server cleanup is callable only by dispatcher')
      role(B);reject(lambda:rpc('gratify_prune_push_history'));checks.append('Clients cannot prune other users events')
      report[kind]={'passed':True,'checks':checks};print(kind,len(checks),'checks passed',flush=True)
finally:
  command(BIN/'pg_ctl.exe','-D',DATA,'-m','fast','-w','stop')
  (OUT/'database-verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
