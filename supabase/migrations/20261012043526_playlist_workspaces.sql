-- Shared playlists use their own canonical records. Legacy personal snapshot writers
-- cannot address these records, so an older APK cannot overwrite a collaborator.
CREATE SCHEMA IF NOT EXISTS gratify_workspace_private;
REVOKE ALL ON SCHEMA gratify_workspace_private FROM PUBLIC,anon,authenticated;
GRANT USAGE ON SCHEMA gratify_workspace_private TO authenticated,anon;

CREATE TABLE gratify_workspace_private.playlists (
 id uuid PRIMARY KEY, owner_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
 kind text NOT NULL CHECK(kind IN ('collaborative','blend')),
 title text NOT NULL CHECK(length(btrim(title)) BETWEEN 1 AND 100),
 description text NOT NULL DEFAULT '' CHECK(length(description)<=500),
 is_public boolean NOT NULL DEFAULT false, revision bigint NOT NULL DEFAULT 0,
 generated_at timestamptz, created_at timestamptz NOT NULL DEFAULT now(),
 CHECK(kind<>'blend' OR NOT is_public)
);
CREATE INDEX workspace_owner ON gratify_workspace_private.playlists(owner_id);
CREATE TABLE gratify_workspace_private.members (
 playlist_id uuid REFERENCES gratify_workspace_private.playlists(id) ON DELETE CASCADE,
 user_id uuid REFERENCES auth.users(id) ON DELETE CASCADE,
 consent boolean NOT NULL DEFAULT false, joined_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(playlist_id,user_id)
);
CREATE INDEX workspace_member_user ON gratify_workspace_private.members(user_id,playlist_id);
CREATE TABLE gratify_workspace_private.items (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 playlist_id uuid NOT NULL REFERENCES gratify_workspace_private.playlists(id) ON DELETE CASCADE,
 video_id text NOT NULL CHECK(length(video_id) BETWEEN 1 AND 128),
 title text NOT NULL CHECK(length(title) BETWEEN 1 AND 512),
 artist text NOT NULL DEFAULT '' CHECK(length(artist)<=512),
 thumbnail_url text CHECK(length(thumbnail_url)<=2048),
 duration integer NOT NULL DEFAULT 0 CHECK(duration BETWEEN 0 AND 86400),
 position integer NOT NULL, added_by uuid REFERENCES auth.users(id) ON DELETE SET NULL,
 UNIQUE(playlist_id,video_id)
);
CREATE INDEX workspace_items_order ON gratify_workspace_private.items(playlist_id,position,id);
CREATE TABLE gratify_workspace_private.operations (
 user_id uuid REFERENCES auth.users(id) ON DELETE CASCADE, operation_id uuid,
 playlist_id uuid NOT NULL REFERENCES gratify_workspace_private.playlists(id) ON DELETE CASCADE,
 payload jsonb NOT NULL, PRIMARY KEY(user_id,operation_id)
);
CREATE TABLE gratify_workspace_private.invites (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), token_hash text NOT NULL UNIQUE,
 playlist_id uuid NOT NULL REFERENCES gratify_workspace_private.playlists(id) ON DELETE CASCADE,
 target_id uuid REFERENCES auth.users(id) ON DELETE CASCADE,
 expires_at timestamptz NOT NULL DEFAULT now()+interval '7 days', revoked boolean NOT NULL DEFAULT false,
 uses integer NOT NULL DEFAULT 0, max_uses integer NOT NULL DEFAULT 10,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX workspace_invites_playlist ON gratify_workspace_private.invites(playlist_id);
CREATE TABLE gratify_workspace_private.folders (
 id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
 title text NOT NULL CHECK(length(btrim(title)) BETWEEN 1 AND 100),
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX workspace_folder_user ON gratify_workspace_private.folders(user_id);
CREATE TABLE gratify_workspace_private.placements (
 user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE, ref text NOT NULL CHECK(length(ref)<=160),
 folder_id uuid NOT NULL REFERENCES gratify_workspace_private.folders(id) ON DELETE CASCADE,
 PRIMARY KEY(user_id,ref)
);
CREATE INDEX workspace_placement_folder ON gratify_workspace_private.placements(folder_id);

ALTER TABLE public.direct_messages ADD COLUMN IF NOT EXISTS kind text NOT NULL DEFAULT 'text';
ALTER TABLE public.direct_messages ADD COLUMN IF NOT EXISTS invitation_id uuid;

DO $rls$ DECLARE t text; BEGIN
 FOREACH t IN ARRAY ARRAY['playlists','members','items','operations','invites','folders','placements'] LOOP
  EXECUTE format('ALTER TABLE gratify_workspace_private.%I ENABLE ROW LEVEL SECURITY',t);
  EXECUTE format('REVOKE ALL ON gratify_workspace_private.%I FROM PUBLIC,anon,authenticated',t);
 END LOOP;
END $rls$;

-- Single transaction boundary for operations, invites and folder moves. The private
-- implementation alone has elevated table access. Every path checks the caller.
CREATE FUNCTION gratify_workspace_private.api(p_action text,p_args jsonb) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $fn$
DECLARE
 me uuid:=auth.uid(); h gratify_workspace_private.playlists; inv gratify_workspace_private.invites;
 fid uuid; pid uuid; op uuid; token text; v_ref text; result jsonb; prior jsonb; role_owner boolean;
 n integer; item_id uuid; peer uuid; conversation uuid; message_id uuid;
BEGIN
 IF p_args IS NULL OR jsonb_typeof(p_args)<>'object' OR octet_length(p_args::text)>65536 THEN RAISE EXCEPTION 'Invalid request'; END IF;
 IF p_action='public' THEN
  RETURN (SELECT coalesce(jsonb_agg(to_jsonb(x)),'[]'::jsonb) FROM
    (SELECT id,owner_id,kind,title,description,is_public,revision,created_at FROM gratify_workspace_private.playlists
     WHERE is_public AND kind='collaborative' ORDER BY created_at DESC,id LIMIT 100) x);
 END IF;
 IF me IS NULL OR NOT EXISTS(SELECT 1 FROM auth.users WHERE id=me) THEN RAISE EXCEPTION 'Authentication required' USING ERRCODE='42501'; END IF;
 IF p_action='library' THEN
  RETURN jsonb_build_object('playlists',(SELECT coalesce(jsonb_agg(to_jsonb(p) ORDER BY p.created_at DESC),'[]'::jsonb)
   FROM gratify_workspace_private.playlists p JOIN gratify_workspace_private.members m ON m.playlist_id=p.id WHERE m.user_id=me),
   'folders',(SELECT coalesce(jsonb_agg(to_jsonb(f) ORDER BY f.created_at DESC),'[]'::jsonb) FROM gratify_workspace_private.folders f WHERE f.user_id=me),
   'placements',(SELECT coalesce(jsonb_agg(to_jsonb(x)),'[]'::jsonb) FROM gratify_workspace_private.placements x WHERE x.user_id=me));
 END IF;
 IF p_action IN ('create','folder_create') THEN
  PERFORM pg_advisory_xact_lock(hashtextextended(me::text,20261012));
  pid:=(p_args->>'id')::uuid;
  IF length(btrim(p_args->>'title')) NOT BETWEEN 1 AND 100 OR p_args->>'title' IS NULL THEN RAISE EXCEPTION 'Invalid title'; END IF;
  IF p_action='folder_create' THEN
   IF EXISTS(SELECT 1 FROM gratify_workspace_private.folders WHERE id=pid AND user_id<>me) THEN RAISE EXCEPTION 'Unavailable' USING ERRCODE='42501'; END IF;
   IF NOT EXISTS(SELECT 1 FROM gratify_workspace_private.folders WHERE id=pid) AND (SELECT count(*) FROM gratify_workspace_private.folders WHERE user_id=me)>=100 THEN RAISE EXCEPTION 'Folder limit reached'; END IF;
   INSERT INTO gratify_workspace_private.folders(id,user_id,title) VALUES(pid,me,btrim(p_args->>'title')) ON CONFLICT(id) DO NOTHING;
   RETURN jsonb_build_object('id',pid);
  END IF;
  IF p_args->>'kind' NOT IN ('collaborative','blend') OR p_args->>'kind' IS NULL THEN RAISE EXCEPTION 'Invalid kind'; END IF;
  IF p_args->>'kind'='blend' AND coalesce((p_args->>'consent')::boolean,false) IS NOT TRUE THEN RAISE EXCEPTION 'Consent required'; END IF;
  IF EXISTS(SELECT 1 FROM gratify_workspace_private.playlists WHERE id=pid AND owner_id<>me) THEN RAISE EXCEPTION 'Unavailable' USING ERRCODE='42501'; END IF;
  IF NOT EXISTS(SELECT 1 FROM gratify_workspace_private.playlists WHERE id=pid) AND (SELECT count(*) FROM gratify_workspace_private.playlists WHERE owner_id=me)>=100 THEN RAISE EXCEPTION 'Playlist limit reached'; END IF;
  INSERT INTO gratify_workspace_private.playlists(id,owner_id,kind,title) VALUES(pid,me,p_args->>'kind',btrim(p_args->>'title')) ON CONFLICT(id) DO NOTHING;
  INSERT INTO gratify_workspace_private.members(playlist_id,user_id,consent) VALUES(pid,me,p_args->>'kind'='blend') ON CONFLICT DO NOTHING;
  RETURN jsonb_build_object('id',pid);
 END IF;
 IF p_action IN ('folder_delete','folder_rename','move') THEN
  fid:=nullif(p_args->>'folder_id','')::uuid;
  IF fid IS NOT NULL THEN
   PERFORM 1 FROM gratify_workspace_private.folders WHERE id=fid AND user_id=me FOR UPDATE;
   IF NOT FOUND THEN RAISE EXCEPTION 'Folder unavailable' USING ERRCODE='42501'; END IF;
  ELSIF p_action<>'move' THEN RAISE EXCEPTION 'Folder unavailable'; END IF;
  IF p_action='folder_delete' THEN DELETE FROM gratify_workspace_private.folders WHERE id=fid AND user_id=me;
  ELSIF p_action='folder_rename' THEN UPDATE gratify_workspace_private.folders SET title=btrim(p_args->>'title') WHERE id=fid AND user_id=me;
  ELSE
   v_ref:=p_args->>'ref';
   IF v_ref LIKE 'workspace:%' THEN
    IF NOT EXISTS(SELECT 1 FROM gratify_workspace_private.members WHERE playlist_id=substring(v_ref FROM 11)::uuid AND user_id=me) THEN RAISE EXCEPTION 'Playlist unavailable' USING ERRCODE='42501'; END IF;
   ELSIF v_ref LIKE 'local:%' THEN
    IF NOT EXISTS(SELECT 1 FROM public.cloud_playlists WHERE user_id::text=me::text AND client_sync_id=substring(v_ref FROM 7)) THEN RAISE EXCEPTION 'Playlist not synchronized'; END IF;
   ELSIF v_ref LIKE 'shared:%' THEN
    IF NOT EXISTS(SELECT 1 FROM public.shared_playlist_saves WHERE user_id::text=me::text AND playlist_id::text=substring(v_ref FROM 8)) THEN RAISE EXCEPTION 'Saved playlist unavailable' USING ERRCODE='42501'; END IF;
   ELSE RAISE EXCEPTION 'Invalid reference'; END IF;
   IF fid IS NULL THEN DELETE FROM gratify_workspace_private.placements WHERE user_id=me AND placements.ref=v_ref;
   ELSE INSERT INTO gratify_workspace_private.placements(user_id,ref,folder_id) VALUES(me,v_ref,fid) ON CONFLICT(user_id,ref) DO UPDATE SET folder_id=excluded.folder_id; END IF;
  END IF;
  RETURN '{}'::jsonb;
 END IF;
 IF p_action IN ('preview','join','send_invite') THEN
  token:=p_args->>'token';
  IF token IS NULL OR token !~ '^[a-f0-9]{64}$' THEN RAISE EXCEPTION 'Invitation unavailable'; END IF;
  SELECT * INTO inv FROM gratify_workspace_private.invites WHERE token_hash=encode(sha256(convert_to(token,'UTF8')),'hex');
  IF inv.id IS NULL THEN RAISE EXCEPTION 'Invitation unavailable'; END IF;
  pid:=inv.playlist_id;
 ELSE pid:=(p_args->>'playlist_id')::uuid; END IF;
 SELECT * INTO h FROM gratify_workspace_private.playlists WHERE id=pid FOR UPDATE;
 IF h.id IS NULL THEN RAISE EXCEPTION 'Playlist unavailable' USING ERRCODE='42501'; END IF;
 role_owner:=h.owner_id=me;
 IF p_action IN ('preview','join','send_invite') THEN
  SELECT * INTO inv FROM gratify_workspace_private.invites WHERE id=inv.id FOR UPDATE;
  IF inv.revoked OR inv.expires_at<=clock_timestamp() OR (inv.uses>=inv.max_uses AND p_action<>'send_invite' AND NOT EXISTS(SELECT 1 FROM gratify_workspace_private.members WHERE playlist_id=pid AND user_id=me))
    OR (inv.target_id IS NOT NULL AND inv.target_id<>me AND NOT (p_action='send_invite' AND role_owner)) THEN RAISE EXCEPTION 'Invitation expired or unavailable'; END IF;
  IF p_action='preview' THEN
   RETURN jsonb_build_object('id',h.id,'title',h.title,'kind',h.kind,'owner_id',h.owner_id,
    'owner_name',(SELECT display_name FROM public.profiles WHERE id::text=h.owner_id::text),'expires_at',inv.expires_at,
    'already_member',EXISTS(SELECT 1 FROM gratify_workspace_private.members WHERE playlist_id=pid AND user_id=me));
  ELSIF p_action='join' THEN
   IF EXISTS(SELECT 1 FROM gratify_workspace_private.members WHERE playlist_id=pid AND user_id=me) THEN RETURN jsonb_build_object('id',pid); END IF;
   IF h.kind='blend' AND coalesce((p_args->>'consent')::boolean,false) IS NOT TRUE THEN RAISE EXCEPTION 'Consent required'; END IF;
   IF (SELECT count(*) FROM gratify_workspace_private.members WHERE playlist_id=pid)>=(CASE WHEN h.kind='blend' THEN 10 ELSE 50 END) THEN RAISE EXCEPTION 'Member limit reached'; END IF;
   INSERT INTO gratify_workspace_private.members(playlist_id,user_id,consent) VALUES(pid,me,h.kind='blend');
   UPDATE gratify_workspace_private.invites SET uses=uses+1 WHERE id=inv.id;
   UPDATE gratify_workspace_private.playlists SET generated_at=NULL WHERE id=pid;
   RETURN jsonb_build_object('id',pid);
  ELSE
   peer:=(p_args->>'peer_id')::uuid;
   IF NOT role_owner OR inv.target_id IS DISTINCT FROM peer THEN RAISE EXCEPTION 'Invitation target mismatch' USING ERRCODE='42501'; END IF;
   SELECT public.gratify_dm_open(peer) INTO conversation;
   SELECT id INTO message_id FROM public.gratify_dm_send(conversation,
    'Undangan '||h.title||E'\nhttps://gratify.web.id/app/invite?token='||token,(p_args->>'operation_id')::uuid);
   UPDATE public.direct_messages SET kind=CASE WHEN h.kind='blend' THEN 'blend_invite' ELSE 'playlist_invite' END,invitation_id=inv.id WHERE id=message_id AND sender_id=me;
   RETURN jsonb_build_object('id',message_id);
  END IF;
 END IF;
 IF p_action='detail' THEN
  IF NOT h.is_public AND NOT EXISTS(SELECT 1 FROM gratify_workspace_private.members WHERE playlist_id=pid AND user_id=me) THEN RAISE EXCEPTION 'Playlist unavailable' USING ERRCODE='42501'; END IF;
  RETURN to_jsonb(h)||jsonb_build_object('member',EXISTS(SELECT 1 FROM gratify_workspace_private.members WHERE playlist_id=pid AND user_id=me),
   'items',(SELECT coalesce(jsonb_agg(to_jsonb(i) ORDER BY position,id),'[]'::jsonb) FROM gratify_workspace_private.items i WHERE playlist_id=pid),
   'members',(SELECT coalesce(jsonb_agg(jsonb_build_object('user_id',m.user_id,'name',p.display_name,'avatar',p.avatar_url,'owner',m.user_id=h.owner_id) ORDER BY m.joined_at),'[]'::jsonb)
    FROM gratify_workspace_private.members m LEFT JOIN public.profiles p ON p.id::text=m.user_id::text WHERE m.playlist_id=pid));
 END IF;
 IF p_action IN ('invite','revoke_invites','metadata','delete','remove_member','restrict') AND NOT role_owner THEN RAISE EXCEPTION 'Owner required' USING ERRCODE='42501'; END IF;
 IF p_action='invite' THEN
  IF (SELECT count(*) FROM gratify_workspace_private.invites WHERE playlist_id=pid AND NOT revoked AND expires_at>now())>=20 THEN RAISE EXCEPTION 'Invitation limit reached'; END IF;
  token:=replace(gen_random_uuid()::text,'-','')||replace(gen_random_uuid()::text,'-','');
  peer:=nullif(p_args->>'target_id','')::uuid;
  INSERT INTO gratify_workspace_private.invites(token_hash,playlist_id,target_id,max_uses) VALUES(encode(sha256(convert_to(token,'UTF8')),'hex'),pid,peer,CASE WHEN peer IS NULL THEN 10 ELSE 1 END)
   RETURNING * INTO inv;
  RETURN jsonb_build_object('id',inv.id,'token',token,'expires_at',inv.expires_at);
 ELSIF p_action='revoke_invites' THEN UPDATE gratify_workspace_private.invites SET revoked=true WHERE playlist_id=pid;
 ELSIF p_action='restrict' THEN
  UPDATE gratify_workspace_private.invites SET revoked=true WHERE playlist_id=pid;
  DELETE FROM gratify_workspace_private.members WHERE playlist_id=pid AND user_id<>me;
  DELETE FROM gratify_workspace_private.placements WHERE placements.ref='workspace:'||pid AND user_id<>me;
  IF h.kind='blend' THEN DELETE FROM gratify_workspace_private.items WHERE playlist_id=pid; END IF;
  UPDATE gratify_workspace_private.playlists SET is_public=false,generated_at=NULL,revision=revision+1 WHERE id=pid;
 ELSIF p_action='metadata' THEN
  UPDATE gratify_workspace_private.playlists SET title=btrim(coalesce(p_args->>'title',title)),description=coalesce(p_args->>'description',description),
   is_public=coalesce((p_args->>'is_public')::boolean,is_public),revision=revision+1 WHERE id=pid;
 ELSIF p_action='delete' THEN
  DELETE FROM gratify_workspace_private.placements WHERE placements.ref='workspace:'||pid;
  DELETE FROM gratify_workspace_private.playlists WHERE id=pid;
 ELSIF p_action IN ('leave','remove_member') THEN
  IF NOT EXISTS(SELECT 1 FROM gratify_workspace_private.members WHERE playlist_id=pid AND user_id=me) THEN RAISE EXCEPTION 'Member required' USING ERRCODE='42501'; END IF;
  peer:=CASE WHEN p_action='leave' THEN me ELSE (p_args->>'user_id')::uuid END;
  IF peer=h.owner_id THEN RAISE EXCEPTION 'Owner must delete playlist instead'; END IF;
  DELETE FROM gratify_workspace_private.members WHERE playlist_id=pid AND user_id=peer;
  IF p_action='remove_member' THEN UPDATE gratify_workspace_private.invites SET revoked=true WHERE playlist_id=pid; END IF;
  DELETE FROM gratify_workspace_private.placements WHERE user_id=peer AND placements.ref='workspace:'||pid;
  IF h.kind='blend' THEN DELETE FROM gratify_workspace_private.items WHERE playlist_id=pid; END IF;
  UPDATE gratify_workspace_private.playlists SET generated_at=NULL,revision=revision+1 WHERE id=pid;
 ELSIF p_action IN ('add','remove','reorder') THEN
  IF h.kind<>'collaborative' OR NOT EXISTS(SELECT 1 FROM gratify_workspace_private.members WHERE playlist_id=pid AND user_id=me) THEN RAISE EXCEPTION 'Editor required' USING ERRCODE='42501'; END IF;
  op:=(p_args->>'operation_id')::uuid;
  IF op IS NULL THEN RAISE EXCEPTION 'Operation identity required'; END IF;
  SELECT payload INTO prior FROM gratify_workspace_private.operations WHERE user_id=me AND operation_id=op;
  IF FOUND THEN
   IF prior IS DISTINCT FROM jsonb_build_object('action',p_action,'args',p_args) THEN RAISE EXCEPTION 'Retry identity mismatch'; END IF;
   RETURN jsonb_build_object('revision',h.revision);
  END IF;
  IF p_action='add' THEN
   IF (SELECT count(*) FROM gratify_workspace_private.items WHERE playlist_id=pid)>=1000 THEN RAISE EXCEPTION 'Song limit reached'; END IF;
   INSERT INTO gratify_workspace_private.items(playlist_id,video_id,title,artist,thumbnail_url,duration,position,added_by)
    VALUES(pid,p_args->>'video_id',p_args->>'title',coalesce(p_args->>'artist',''),nullif(p_args->>'thumbnail_url',''),coalesce((p_args->>'duration')::integer,0),
     coalesce((SELECT max(position)+1 FROM gratify_workspace_private.items WHERE playlist_id=pid),0),me) ON CONFLICT(playlist_id,video_id) DO NOTHING;
  ELSIF p_action='remove' THEN DELETE FROM gratify_workspace_private.items WHERE playlist_id=pid AND id=(p_args->>'item_id')::uuid;
  ELSE
   IF (p_args->>'revision')::bigint IS DISTINCT FROM h.revision THEN RAISE EXCEPTION 'Revision conflict'; END IF;
   IF jsonb_typeof(p_args->'ids')<>'array' THEN RAISE EXCEPTION 'Invalid order'; END IF;
   n:=jsonb_array_length(p_args->'ids');
   IF n<>(SELECT count(*) FROM gratify_workspace_private.items WHERE playlist_id=pid)
    OR n<>(SELECT count(DISTINCT value) FROM jsonb_array_elements_text(p_args->'ids'))
    OR EXISTS(SELECT 1 FROM jsonb_array_elements_text(p_args->'ids') x WHERE NOT EXISTS(SELECT 1 FROM gratify_workspace_private.items WHERE playlist_id=pid AND id::text=x.value)) THEN RAISE EXCEPTION 'Invalid order'; END IF;
   UPDATE gratify_workspace_private.items i SET position=x.ordinality-1 FROM jsonb_array_elements_text(p_args->'ids') WITH ORDINALITY x(value,ordinality) WHERE i.playlist_id=pid AND i.id::text=x.value;
  END IF;
  INSERT INTO gratify_workspace_private.operations VALUES(me,op,pid,jsonb_build_object('action',p_action,'args',p_args));
  UPDATE gratify_workspace_private.playlists SET revision=revision+1 WHERE id=pid;
 ELSIF p_action='refresh_blend' THEN
  IF h.kind<>'blend' OR NOT EXISTS(SELECT 1 FROM gratify_workspace_private.members WHERE playlist_id=pid AND user_id=me AND consent) THEN RAISE EXCEPTION 'Blend member required' USING ERRCODE='42501'; END IF;
  IF h.generated_at IS NOT NULL AND h.generated_at>now()-interval '1 day' THEN RETURN '{}'::jsonb; END IF;
  DELETE FROM gratify_workspace_private.items WHERE playlist_id=pid;
  IF (SELECT count(*) FROM gratify_workspace_private.members WHERE playlist_id=pid AND consent)>=2 THEN
   -- Each consenting member contributes at most five songs per artist and twenty
   -- candidates. Round-robin ranks preserve representation before de-duplication.
   INSERT INTO gratify_workspace_private.items(playlist_id,video_id,title,artist,thumbnail_url,duration,position,added_by)
   WITH candidates AS (
    SELECT m.user_id,s.video_id,s.title,coalesce(s.artist_name,'') artist,s.thumbnail_url,coalesce(s.duration,0) duration,1000::bigint weight
     FROM gratify_workspace_private.members m JOIN public.user_liked_songs s ON s.user_id=m.user_id WHERE m.playlist_id=pid AND m.consent
    UNION ALL
    SELECT m.user_id,s.video_id,s.title,coalesce(s.artist_name,''),s.thumbnail_url,coalesce(s.duration,0),least(coalesce(s.listen_count,1),999)
     FROM gratify_workspace_private.members m JOIN public.user_play_history s ON s.user_id=m.user_id WHERE m.playlist_id=pid AND m.consent
   ), unique_user AS (SELECT DISTINCT ON(user_id,video_id) * FROM candidates WHERE title IS NOT NULL AND length(title) BETWEEN 1 AND 512 AND length(video_id) BETWEEN 1 AND 128 AND duration BETWEEN 0 AND 86400 ORDER BY user_id,video_id,weight DESC),
   artist_limited AS (SELECT *,row_number() OVER(PARTITION BY user_id,artist ORDER BY weight DESC,video_id) ar FROM unique_user),
   ranked AS (SELECT *,row_number() OVER(PARTITION BY user_id ORDER BY weight DESC,video_id) r FROM artist_limited WHERE ar<=5),
   chosen AS (SELECT DISTINCT ON(video_id) * FROM ranked WHERE r<=20 ORDER BY video_id,r,user_id),
   final AS (SELECT * FROM chosen ORDER BY r,user_id,video_id LIMIT 50)
   SELECT pid,video_id,title,artist,thumbnail_url,duration,row_number() OVER(ORDER BY r,user_id,video_id)-1,user_id FROM final;
  END IF;
  UPDATE gratify_workspace_private.playlists SET generated_at=now(),revision=revision+1 WHERE id=pid;
 ELSE RAISE EXCEPTION 'Unsupported action'; END IF;
 RETURN '{}'::jsonb;
END $fn$;
REVOKE ALL ON FUNCTION gratify_workspace_private.api(text,jsonb) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION gratify_workspace_private.api(text,jsonb) TO authenticated,anon;
CREATE FUNCTION public.gratify_workspace(p_action text,p_args jsonb DEFAULT '{}'::jsonb) RETURNS jsonb
LANGUAGE sql SECURITY INVOKER SET search_path='' AS $$ SELECT gratify_workspace_private.api(p_action,p_args) $$;
REVOKE ALL ON FUNCTION public.gratify_workspace(text,jsonb) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.gratify_workspace(text,jsonb) TO anon,authenticated;
