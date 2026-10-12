ALTER TABLE gratify_workspace_private.playlists ADD COLUMN cover_path text;
ALTER TABLE gratify_workspace_private.folders ADD COLUMN parent_id uuid REFERENCES gratify_workspace_private.folders(id) ON DELETE SET NULL;
ALTER TABLE gratify_workspace_private.folders ADD CONSTRAINT folder_not_self CHECK(parent_id IS DISTINCT FROM id);
CREATE INDEX workspace_folder_parent ON gratify_workspace_private.folders(parent_id);

INSERT INTO storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
VALUES('workspace-covers','workspace-covers',false,5242880,ARRAY['image/jpeg'])
ON CONFLICT(id) DO UPDATE SET public=false,file_size_limit=5242880,allowed_mime_types=ARRAY['image/jpeg'];

CREATE FUNCTION gratify_workspace_private.cover_write(p_name text) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
 SELECT auth.uid() IS NOT NULL AND p_name ~ '^[a-f0-9-]{36}/[a-f0-9-]{36}/[a-f0-9-]{36}\.jpg$'
 AND split_part(p_name,'/',1)=auth.uid()::text
 AND EXISTS(SELECT 1 FROM gratify_workspace_private.playlists p WHERE p.id::text=split_part(p_name,'/',2) AND p.owner_id=auth.uid())
$$;
CREATE FUNCTION gratify_workspace_private.cover_read(p_name text) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
 SELECT (auth.uid() IS NOT NULL AND EXISTS(SELECT 1 FROM auth.users WHERE id=auth.uid()) AND split_part(p_name,'/',1)=auth.uid()::text)
 OR EXISTS(SELECT 1 FROM gratify_workspace_private.playlists p WHERE
 p.cover_path=p_name AND (p.is_public OR EXISTS(SELECT 1 FROM gratify_workspace_private.members m WHERE m.playlist_id=p.id AND m.user_id=auth.uid())))
$$;
CREATE FUNCTION gratify_workspace_private.cover_delete(p_name text) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
 SELECT auth.uid() IS NOT NULL AND EXISTS(SELECT 1 FROM auth.users WHERE id=auth.uid()) AND split_part(p_name,'/',1)=auth.uid()::text
 AND NOT EXISTS(SELECT 1 FROM gratify_workspace_private.playlists WHERE cover_path=p_name)
$$;
REVOKE ALL ON FUNCTION gratify_workspace_private.cover_write(text),gratify_workspace_private.cover_read(text),gratify_workspace_private.cover_delete(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION gratify_workspace_private.cover_read(text) TO anon,authenticated;
GRANT EXECUTE ON FUNCTION gratify_workspace_private.cover_write(text),gratify_workspace_private.cover_delete(text) TO authenticated;
CREATE POLICY workspace_cover_insert ON storage.objects FOR INSERT TO authenticated
WITH CHECK(bucket_id='workspace-covers' AND gratify_workspace_private.cover_write(name));
CREATE POLICY workspace_cover_read ON storage.objects FOR SELECT TO anon,authenticated
USING(bucket_id='workspace-covers' AND gratify_workspace_private.cover_read(name));
CREATE POLICY workspace_cover_delete ON storage.objects FOR DELETE TO authenticated
USING(bucket_id='workspace-covers' AND gratify_workspace_private.cover_delete(name));

-- Keep the previously verified playlist and invitation operations unchanged.
ALTER FUNCTION gratify_workspace_private.api(text,jsonb) RENAME TO base_api;
REVOKE ALL ON FUNCTION gratify_workspace_private.base_api(text,jsonb) FROM PUBLIC,anon,authenticated;
CREATE FUNCTION gratify_workspace_private.api(p_action text,p_args jsonb) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $fn$
DECLARE me uuid:=auth.uid(); pid uuid; fid uuid; parent uuid; previous_parent uuid;
 h gratify_workspace_private.playlists; path text; answer jsonb; ancestor_depth integer; subtree_depth integer; has_cycle boolean;
BEGIN
 IF p_args IS NULL OR jsonb_typeof(p_args)<>'object' OR octet_length(p_args::text)>65536 THEN RAISE EXCEPTION 'Invalid request'; END IF;
 IF p_action='public' THEN
  RETURN (SELECT coalesce(jsonb_agg(to_jsonb(x)),'[]'::jsonb) FROM
   (SELECT id,owner_id,kind,title,description,is_public,revision,created_at,cover_path FROM gratify_workspace_private.playlists
    WHERE is_public AND kind='collaborative' ORDER BY created_at DESC,id LIMIT 100) x);
 END IF;
 IF me IS NULL OR NOT EXISTS(SELECT 1 FROM auth.users WHERE id=me) THEN RAISE EXCEPTION 'Authentication required' USING ERRCODE='42501'; END IF;
 IF p_action IN ('folder_create','folder_move','folder_delete','create') THEN
  PERFORM pg_advisory_xact_lock(hashtextextended(me::text,20261012));
  fid:=CASE WHEN p_action IN ('create','folder_create') THEN (p_args->>'id')::uuid ELSE (p_args->>'folder_id')::uuid END;
  parent:=nullif(p_args->>'parent_id','')::uuid;
  IF parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM gratify_workspace_private.folders WHERE id=parent AND user_id=me) THEN RAISE EXCEPTION 'Folder unavailable' USING ERRCODE='42501'; END IF;
  IF p_action IN ('folder_move','folder_delete') THEN
   SELECT parent_id INTO previous_parent FROM gratify_workspace_private.folders WHERE id=fid AND user_id=me;
   IF NOT FOUND THEN RAISE EXCEPTION 'Folder unavailable' USING ERRCODE='42501'; END IF;
  END IF;
  IF p_action='folder_delete' THEN
   UPDATE gratify_workspace_private.folders SET parent_id=previous_parent WHERE parent_id=fid AND user_id=me;
   IF previous_parent IS NOT NULL THEN
    UPDATE gratify_workspace_private.placements SET folder_id=previous_parent WHERE folder_id=fid AND user_id=me;
   ELSE DELETE FROM gratify_workspace_private.placements WHERE folder_id=fid AND user_id=me; END IF;
   DELETE FROM gratify_workspace_private.folders WHERE id=fid AND user_id=me;
   RETURN '{}'::jsonb;
  END IF;
  IF p_action='create' THEN
   answer:=gratify_workspace_private.base_api(p_action,p_args);
   IF parent IS NOT NULL THEN
    INSERT INTO gratify_workspace_private.placements(user_id,ref,folder_id) VALUES(me,'workspace:'||fid,parent)
     ON CONFLICT(user_id,ref) DO UPDATE SET folder_id=excluded.folder_id;
   END IF;
   RETURN answer;
  END IF;
  -- Locking per owner prevents two concurrent moves from making a cycle.
  IF fid=parent THEN RAISE EXCEPTION 'Folder cycle'; END IF;
  WITH RECURSIVE ancestors AS (
   SELECT id,parent_id,1 d FROM gratify_workspace_private.folders WHERE id=parent AND user_id=me
   UNION ALL SELECT f.id,f.parent_id,a.d+1 FROM gratify_workspace_private.folders f JOIN ancestors a ON f.id=a.parent_id WHERE f.user_id=me AND a.d<101
  ) SELECT coalesce(max(d),0) INTO ancestor_depth FROM ancestors;
  WITH RECURSIVE descendants AS (
   SELECT id,1 d FROM gratify_workspace_private.folders WHERE id=fid AND user_id=me
   UNION ALL SELECT f.id,s.d+1 FROM gratify_workspace_private.folders f JOIN descendants s ON f.parent_id=s.id WHERE f.user_id=me AND s.d<101
  ) SELECT coalesce(max(d),1),coalesce(bool_or(id=parent),false) INTO subtree_depth,has_cycle FROM descendants;
  IF has_cycle THEN RAISE EXCEPTION 'Folder cycle'; END IF;
  IF ancestor_depth+subtree_depth>5 THEN RAISE EXCEPTION 'Folder depth limit'; END IF;
  IF p_action='folder_create' THEN
   IF EXISTS(SELECT 1 FROM gratify_workspace_private.folders WHERE id=fid) THEN
    IF NOT EXISTS(SELECT 1 FROM gratify_workspace_private.folders WHERE id=fid AND user_id=me) THEN RAISE EXCEPTION 'Folder unavailable' USING ERRCODE='42501'; END IF;
    RETURN jsonb_build_object('id',fid);
   END IF;
   answer:=gratify_workspace_private.base_api(p_action,p_args);
   UPDATE gratify_workspace_private.folders SET parent_id=parent WHERE id=fid AND user_id=me;
   RETURN answer;
  END IF;
  UPDATE gratify_workspace_private.folders SET parent_id=parent WHERE id=fid AND user_id=me;
  RETURN '{}'::jsonb;
 END IF;
 IF p_action='metadata' AND p_args ? 'cover_path' THEN
  pid:=(p_args->>'playlist_id')::uuid;
  SELECT * INTO h FROM gratify_workspace_private.playlists WHERE id=pid FOR UPDATE;
  IF h.id IS NULL OR h.owner_id<>me THEN RAISE EXCEPTION 'Owner required' USING ERRCODE='42501'; END IF;
  path:=nullif(p_args->>'cover_path','');
  IF path IS NOT NULL AND (NOT gratify_workspace_private.cover_write(path) OR split_part(path,'/',2)<>pid::text
   OR NOT EXISTS(SELECT 1 FROM storage.objects WHERE bucket_id='workspace-covers' AND name=path)) THEN RAISE EXCEPTION 'Cover unavailable'; END IF;
  answer:=gratify_workspace_private.base_api(p_action,p_args);
  UPDATE gratify_workspace_private.playlists SET cover_path=path WHERE id=pid;
  RETURN answer;
 END IF;
 RETURN gratify_workspace_private.base_api(p_action,p_args);
END $fn$;
REVOKE ALL ON FUNCTION gratify_workspace_private.api(text,jsonb) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION gratify_workspace_private.api(text,jsonb) TO anon,authenticated;
CREATE OR REPLACE FUNCTION public.gratify_workspace(p_action text,p_args jsonb DEFAULT '{}'::jsonb) RETURNS jsonb
LANGUAGE sql SECURITY INVOKER SET search_path='' AS $$ SELECT gratify_workspace_private.api(p_action,p_args) $$;
REVOKE ALL ON FUNCTION public.gratify_workspace(text,jsonb) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.gratify_workspace(text,jsonb) TO anon,authenticated;
