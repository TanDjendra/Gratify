"""Run the social migration against an owned, isolated loopback PostgreSQL."""
from pathlib import Path
import json, socket, subprocess, uuid
import psycopg

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'artifacts/friends-social'
OUT.mkdir(parents=True, exist_ok=True)
BIN = ROOT / 'artifacts/launch-audit/postgres-test-runtime/bin'
DATA = OUT / 'postgres-data'
A, B, C = (f'{n}{n}{n}{n}{n}{n}{n}{n}-{n}{n}{n}{n}-4{n}{n}{n}-8{n}{n}{n}-{n}' + n * 11 for n in ('1','2','3'))

def command(*args):
    with (OUT / 'postgres-process.log').open('a') as log:
        subprocess.run([str(a) for a in args], stdout=log, stderr=log, creationflags=0x08000000, timeout=60, check=True)

with socket.socket() as sock:
    sock.bind(('127.0.0.1', 0)); port = sock.getsockname()[1]
if not (DATA / 'PG_VERSION').exists():
    command(BIN/'initdb.exe','-D',DATA,'-U','postgres','-A','trust','--no-locale','--encoding=UTF8')
command(BIN/'pg_ctl.exe','-D',DATA,'-l',OUT/'postgres.log','-o',f'-h 127.0.0.1 -p {port}','-w','start')
report = {}
try:
  for kind in ('text','uuid'):
    checks = []
    with psycopg.connect(host='127.0.0.1',port=port,user='postgres',dbname='postgres',autocommit=True) as db:
      db.execute('DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public; DROP SCHEMA IF EXISTS auth CASCADE; CREATE SCHEMA auth')
      for name in ('anon','authenticated'):
        if not db.execute('SELECT 1 FROM pg_roles WHERE rolname=%s',(name,)).fetchone(): db.execute(f'CREATE ROLE {name} NOLOGIN')
      db.execute("CREATE TABLE auth.users(id uuid PRIMARY KEY); CREATE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql STABLE AS $$ SELECT NULLIF(current_setting('request.jwt.claim.sub',true),'')::uuid $$; GRANT USAGE ON SCHEMA public,auth TO anon,authenticated; GRANT EXECUTE ON FUNCTION auth.uid() TO anon,authenticated;")
      db.execute(f"CREATE TABLE public.profiles(id {kind} PRIMARY KEY,display_name text,avatar_url text); CREATE TABLE public.follows(follower_id {kind},following_id {kind},PRIMARY KEY(follower_id,following_id)); ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY; ALTER TABLE public.follows ENABLE ROW LEVEL SECURITY;")
      db.execute("CREATE POLICY own_profile ON public.profiles FOR SELECT TO authenticated USING(id::text=auth.uid()::text); CREATE POLICY own_follows ON public.follows FOR SELECT TO authenticated USING(auth.uid()::text IN(follower_id::text,following_id::text)); CREATE VIEW public.public_profiles WITH(security_barrier=true) AS SELECT * FROM public.profiles; GRANT SELECT ON public.public_profiles,public.follows TO authenticated;")
      db.execute("INSERT INTO auth.users VALUES(%s),(%s),(%s)",(A,B,C))
      db.execute("INSERT INTO public.profiles(id,display_name) VALUES(%s,'Alia'),(%s,'Bima'),(%s,'Citra')",(A,B,C))
      db.execute("INSERT INTO public.follows VALUES(%s,%s),(%s,%s)",(A,B,B,A))
      db.execute((ROOT/'supabase/migrations/202610030001_friends_stories_messages.sql').read_text())
      def role(user=None):
        db.execute('RESET ROLE'); db.execute("SELECT set_config('request.jwt.claim.sub',%s,false)",(user or '',)); db.execute('SET ROLE authenticated' if user else 'SET ROLE anon')
      def rpc(name,*args):return db.execute(f'SELECT * FROM public.{name}({",".join(["%s"]*len(args))})',args)
      def denied(name,*args):
        try: rpc(name,*args)
        except psycopg.Error:return
        raise AssertionError(f'{name} accepted invalid access')
      def denied_sql(sql,args=()):
        try:db.execute(sql,args)
        except psycopg.Error:return
        raise AssertionError('Direct mutation unexpectedly allowed')
      def story(audience='friends',seconds=15,start=5000,duration=60000):
        return rpc('gratify_publish_music_story','sample','Lagu','Artis',None,duration,start,seconds,'Catatan',audience).fetchone()[0]
      role(A); private = story(); public = story('public')
      assert db.execute("SELECT expires_at-created_at=interval '24 hours' FROM public.music_stories WHERE id=%s",(private,)).fetchone()[0]
      checks.append('Authenticated author can publish a story with an excerpt')
      role(B); assert {str(s['id']) for s in rpc('gratify_music_stories',False,0).fetchone()[0]}=={str(private),str(public)}
      checks.append('Mutual friend can read friends-only stories even with private follow lists')
      role(C); assert [s['id'] for s in rpc('gratify_music_stories',True,0).fetchone()[0]]==[str(public)]
      assert rpc('gratify_music_stories',False,0).fetchone()[0]==[]
      checks.append('Outsider sees public stories but cannot read friends-only stories')
      role(); denied('gratify_music_stories',True,0); denied_sql('SELECT * FROM public.music_stories')
      checks.append('Anonymous access is denied')
      role(A)
      for seconds in (8,15,30): story(seconds=seconds,start=60000-seconds*1000)
      checks.append('8, 15 and 30 second segments can end exactly at the end of the track')
      for seconds,start,duration,audience in ((10,0,60000,'friends'),(15,-1,60000,'friends'),(30,40000,60000,'friends'),(8,0,7999,'friends'),(15,0,60000,'everyone')):
        denied('gratify_publish_music_story','sample','Lagu','Artis',None,duration,start,seconds,'',audience)
      checks.append('Unsupported duration, negative/beyond-end start, short track and invalid audience are rejected')
      denied_sql('INSERT INTO public.music_stories(owner_id,video_id,title,artist,track_duration_ms,start_ms,clip_seconds) VALUES(%s,\'x\',\'x\',\'x\',30000,0,8)',(B,))
      denied_sql('UPDATE public.music_stories SET expires_at=now()+interval \'3 days\'')
      checks.append('Clients cannot spoof story author, timestamps or expiry')
      role(C); db.execute('DELETE FROM public.music_stories WHERE id=%s',(public,))
      role(A); assert any(s['id']==str(public) for s in rpc('gratify_music_stories',False,0).fetchone()[0])
      db.execute('DELETE FROM public.music_stories WHERE id=%s',(public,))
      checks.append('Only the owner can delete a story')
      db.execute('RESET ROLE'); db.execute("UPDATE public.music_stories SET expires_at=now()-interval '1 second' WHERE id=%s",(private,))
      role(A); assert not any(s['id']==str(private) for s in rpc('gratify_music_stories',False,0).fetchone()[0])
      role(B); assert db.execute('SELECT count(*) FROM public.music_stories WHERE id=%s',(private,)).fetchone()[0]==0
      checks.append('Expired stories disappear from feeds and direct reads, including for their owner')
      role(A); hidden_after_unfollow=story()
      db.execute('RESET ROLE'); db.execute('DELETE FROM public.follows WHERE follower_id::text=%s AND following_id::text=%s',(B,A))
      role(B); assert db.execute('SELECT count(*) FROM public.music_stories WHERE id=%s',(hidden_after_unfollow,)).fetchone()[0]==0
      db.execute('RESET ROLE'); db.execute('INSERT INTO public.follows VALUES(%s,%s)',(B,A)); role(A)
      checks.append('Removing a mutual follow immediately revokes access to friends-only stories')
      role(A); conv=rpc('gratify_dm_open',B).fetchone()[0]
      role(B); assert rpc('gratify_dm_open',A).fetchone()[0]==conv
      checks.append('Mutual users share one accepted conversation regardless of opener')
      role(A); client=uuid.uuid4(); m=rpc('gratify_dm_send',conv,'Halo',client).fetchone(); mid=m[0]
      assert rpc('gratify_dm_send',conv,'Halo',client).fetchone()[0]==mid
      assert len(rpc('gratify_dm_messages',conv,None).fetchone()[0])==1
      denied('gratify_dm_send',conv,'Changed',client)
      checks.append('Lost-response retries are idempotent, but cannot reuse an ID for changed content')
      role(C); denied('gratify_dm_messages',conv,None); denied('gratify_dm_send',conv,'Attack',uuid.uuid4()); denied('gratify_dm_read',conv,mid)
      assert db.execute('SELECT count(*) FROM public.direct_messages WHERE conversation_id=%s',(conv,)).fetchone()[0]==0
      checks.append('Third users cannot read, send or mark another conversation as read')
      role(B); inbox=rpc('gratify_dm_inbox').fetchone()[0]; assert inbox[0]['unread_count']==1
      rpc('gratify_dm_read',conv,mid); assert rpc('gratify_dm_inbox').fetchone()[0][0]['unread_count']==0
      role(A); assert rpc('gratify_dm_inbox').fetchone()[0][0]['peer_read_at'] is not None
      checks.append('Read receipts reflect a visible message cursor and clear unread count')
      role(C); request=rpc('gratify_dm_open',A).fetchone()[0]
      rm=rpc('gratify_dm_send',request,'Sapa',uuid.uuid4()).fetchone()[0]
      denied('gratify_dm_send',request,'Again',uuid.uuid4()); denied('gratify_dm_respond',request,True)
      role(A); denied('gratify_dm_send',request,'Reply before accept',uuid.uuid4())
      assert next(c for c in rpc('gratify_dm_inbox').fetchone()[0] if c['id']==str(request))['accepted'] is False
      checks.append('Nonfriend request allows one opening message and only its recipient can accept')
      rpc('gratify_dm_respond',request,True); rpc('gratify_dm_send',request,'Diterima',uuid.uuid4())
      checks.append('Accepting a request enables two-way messaging')
      role(C); denied_sql('INSERT INTO public.direct_messages(conversation_id,sender_id,client_id,body) VALUES(%s,%s,%s,\'fake\')',(conv,A,uuid.uuid4()))
      denied_sql('UPDATE public.direct_conversations SET accepted=true')
      checks.append('Direct table writes cannot spoof sender or bypass request acceptance')
      rejected=rpc('gratify_dm_open',B).fetchone()[0]; rpc('gratify_dm_send',rejected,'Hello',uuid.uuid4())
      role(B); rpc('gratify_dm_respond',rejected,False)
      role(C); denied('gratify_dm_send',rejected,'Again',uuid.uuid4()); denied('gratify_dm_open',B)
      assert not any(c['id']==str(rejected) for c in rpc('gratify_dm_inbox').fetchone()[0])
      checks.append('Rejected requests cannot be reopened or flooded by their sender')
      role(A); denied('gratify_dm_send',conv,' ',uuid.uuid4()); denied('gratify_dm_send',conv,'a'*2001,uuid.uuid4())
      denied('gratify_dm_open',A)
      checks.append('Blank/oversized messages and self-chat are rejected')
      for n in range(60):
        db.execute('RESET ROLE'); db.execute("INSERT INTO public.direct_messages(conversation_id,sender_id,client_id,body,created_at) VALUES(%s,%s,%s,%s,now()-interval '1 hour'+%s*interval '1 second')",(conv,A,uuid.uuid4(),str(n),n)); role(A)
      page=rpc('gratify_dm_messages',conv,None).fetchone()[0]; assert len(page)==50
      older=rpc('gratify_dm_messages',conv,page[0]['id']).fetchone()[0]
      assert len(older)==11 and not {m['id'] for m in page}&{m['id'] for m in older}
      denied('gratify_dm_messages',conv,rm)
      checks.append('Keyset history pages are bounded, disjoint and cannot use a cursor from another chat')
      role(A); existing_count=db.execute('SELECT count(*) FROM public.music_stories WHERE owner_id=%s AND expires_at>now()',(A,)).fetchone()[0]
      for _ in range(20-existing_count): story()
      denied('gratify_publish_music_story','sample','Lagu','Artis',None,60000,0,8,'','friends')
      checks.append('An account cannot exceed twenty active stories')
      db.execute('RESET ROLE'); db.execute('DELETE FROM auth.users WHERE id=%s',(C,))
      assert db.execute('SELECT count(*) FROM public.direct_conversations WHERE user_low=%s OR user_high=%s',(C,C)).fetchone()[0]==0
      assert db.execute('SELECT count(*) FROM public.direct_messages WHERE conversation_id=%s',(conv,)).fetchone()[0]==61
      checks.append('Deleting an account removes its conversations without affecting unrelated chats')
      role(); denied('gratify_dm_inbox'); denied('gratify_dm_open',A); denied('gratify_dm_send',conv,'x',uuid.uuid4())
      checks.append('Anonymous clients cannot open an inbox or send messages')
    report[kind]={'passed':len(checks),'checks':checks}
finally:
  command(BIN/'pg_ctl.exe','-D',DATA,'-m','fast','-w','stop')
(OUT/'database-verification.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({k:v['passed'] for k,v in report.items()}))
