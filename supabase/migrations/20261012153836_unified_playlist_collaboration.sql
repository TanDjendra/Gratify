-- Personal playlists and collaborative playlists share one canonical identity.
-- Cloud/shared rows below are read projections for existing profile/public links.
CREATE TABLE gratify_workspace_private.personal_links (
 playlist_id uuid PRIMARY KEY REFERENCES gratify_workspace_private.playlists(id) ON DELETE CASCADE,
 cloud_id uuid NOT NULL UNIQUE,
 owner_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
 sync_id text NOT NULL,
 thumbnail_url text,
 UNIQUE(owner_id,sync_id)
);
ALTER TABLE gratify_workspace_private.personal_links ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON gratify_workspace_private.personal_links FROM PUBLIC,anon,authenticated;
ALTER TABLE public.cloud_playlists ADD COLUMN canonical_playlist_id uuid;

-- Guard legacy snapshot writers, including direct table writes. Only the private
-- projection writer sets this transaction-local flag, and restores it on exit.
CREATE FUNCTION gratify_workspace_private.guard_projection() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE cid uuid;
BEGIN
 IF current_setting('gratify.projection_write',true)='on' THEN
  IF TG_OP='DELETE' THEN RETURN OLD; ELSE RETURN NEW; END IF;
 END IF;
 IF TG_TABLE_NAME='cloud_playlists' THEN
  IF TG_OP='UPDATE' AND (to_jsonb(NEW)-'is_profile_pinned'-'updated_at')=(to_jsonb(OLD)-'is_profile_pinned'-'updated_at') THEN RETURN NEW; END IF;
  IF TG_OP='DELETE' THEN cid:=OLD.id; ELSE cid:=NEW.id; END IF;
 ELSIF TG_TABLE_NAME='cloud_playlist_items' THEN
  IF TG_OP='DELETE' THEN cid:=OLD.playlist_id; ELSE cid:=NEW.playlist_id; END IF;
 ELSE
  IF TG_TABLE_NAME='shared_playlists' THEN
   IF TG_OP='UPDATE' AND (to_jsonb(NEW)-'add_count')=(to_jsonb(OLD)-'add_count') THEN RETURN NEW; END IF;
   SELECT cloud_id INTO cid FROM gratify_workspace_private.personal_links WHERE owner_id=OLD.user_id AND sync_id=OLD.client_sync_id;
  ELSE
   SELECT l.cloud_id INTO cid FROM gratify_workspace_private.personal_links l JOIN public.shared_playlists sp
    ON sp.user_id=l.owner_id AND sp.client_sync_id=l.sync_id WHERE sp.id=CASE WHEN TG_OP='DELETE' THEN OLD.playlist_id ELSE NEW.playlist_id END;
  END IF;
 END IF;
 IF EXISTS(SELECT 1 FROM gratify_workspace_private.personal_links WHERE cloud_id=cid) THEN
  RAISE EXCEPTION 'Canonical playlist requires member operations' USING ERRCODE='42501';
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; ELSE RETURN NEW; END IF;
END $$;
REVOKE ALL ON FUNCTION gratify_workspace_private.guard_projection() FROM PUBLIC,anon,authenticated;
CREATE TRIGGER protect_canonical_cloud BEFORE INSERT OR UPDATE OR DELETE ON public.cloud_playlists
 FOR EACH ROW EXECUTE FUNCTION gratify_workspace_private.guard_projection();
CREATE TRIGGER protect_canonical_items BEFORE INSERT OR UPDATE OR DELETE ON public.cloud_playlist_items
 FOR EACH ROW EXECUTE FUNCTION gratify_workspace_private.guard_projection();
CREATE TRIGGER protect_canonical_shared BEFORE UPDATE OR DELETE ON public.shared_playlists
 FOR EACH ROW EXECUTE FUNCTION gratify_workspace_private.guard_projection();
CREATE TRIGGER protect_canonical_shared_items BEFORE INSERT OR UPDATE OR DELETE ON public.shared_playlist_tracks
 FOR EACH ROW EXECUTE FUNCTION gratify_workspace_private.guard_projection();

CREATE FUNCTION gratify_workspace_private.project_playlist(p_id uuid) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE h gratify_workspace_private.playlists; l gratify_workspace_private.personal_links;
 old_flag text:=current_setting('gratify.projection_write',true);
 old_claim text:=current_setting('request.jwt.claim.sub',true); image text; tracks jsonb;
BEGIN
 SELECT * INTO h FROM gratify_workspace_private.playlists WHERE id=p_id;
 IF h.id IS NULL OR h.kind='blend' THEN RETURN; END IF;
 SELECT * INTO l FROM gratify_workspace_private.personal_links WHERE playlist_id=p_id;
 IF l.playlist_id IS NULL THEN
  INSERT INTO gratify_workspace_private.personal_links(playlist_id,cloud_id,owner_id,sync_id)
   VALUES(h.id,h.id,h.owner_id,h.id::text) RETURNING * INTO l;
 END IF;
 image:=CASE WHEN h.cover_path IS NOT NULL THEN
  'https://bnabldxsqpvkyqpjcsdv.supabase.co/storage/v1/object/public/workspace-covers/'||h.cover_path ELSE l.thumbnail_url END;
 PERFORM set_config('gratify.projection_write','on',true);
 INSERT INTO public.cloud_playlists(id,user_id,client_sync_id,title,thumbnail_url,is_public,canonical_playlist_id)
  VALUES(l.cloud_id,h.owner_id,l.sync_id,h.title,image,h.is_public,h.id)
  ON CONFLICT(id) DO UPDATE SET title=excluded.title,thumbnail_url=excluded.thumbnail_url,
   is_public=excluded.is_public,canonical_playlist_id=excluded.canonical_playlist_id;
 DELETE FROM public.cloud_playlist_items WHERE playlist_id=l.cloud_id;
 INSERT INTO public.cloud_playlist_items(playlist_id,video_id,title,artist,duration,thumbnail_url,position)
  SELECT l.cloud_id,video_id,title,artist,duration,thumbnail_url,position FROM gratify_workspace_private.items WHERE playlist_id=p_id;
 -- Keep the already-shared identity, saves, profile pin and links intact.
 IF h.is_public OR EXISTS(SELECT 1 FROM public.shared_playlists WHERE user_id=h.owner_id AND client_sync_id=l.sync_id) THEN
  SELECT coalesce(jsonb_agg(jsonb_build_object('video_id',video_id,'title',title,'artists',artist,'duration_seconds',duration)
   ORDER BY position,id),'[]'::jsonb) INTO tracks FROM gratify_workspace_private.items WHERE playlist_id=p_id;
  PERFORM set_config('request.jwt.claim.sub',h.owner_id::text,true);
  PERFORM public.gratify_replace_shared_playlist_v2(l.sync_id,h.title,image,
   (SELECT display_name FROM public.profiles WHERE id=h.owner_id),tracks);
 END IF;
 PERFORM set_config('request.jwt.claim.sub',coalesce(old_claim,''),true);
 PERFORM set_config('gratify.projection_write',coalesce(old_flag,''),true);
EXCEPTION WHEN OTHERS THEN
 PERFORM set_config('request.jwt.claim.sub',coalesce(old_claim,''),true);
 PERFORM set_config('gratify.projection_write',coalesce(old_flag,''),true);
 RAISE;
END $$;
REVOKE ALL ON FUNCTION gratify_workspace_private.project_playlist(uuid) FROM PUBLIC,anon,authenticated;

ALTER FUNCTION gratify_workspace_private.api(text,jsonb) RENAME TO lifecycle_api;
REVOKE ALL ON FUNCTION gratify_workspace_private.lifecycle_api(text,jsonb) FROM PUBLIC,anon,authenticated;
CREATE FUNCTION gratify_workspace_private.api(p_action text,p_args jsonb) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE me uuid:=auth.uid(); cp public.cloud_playlists; h gratify_workspace_private.playlists;
 l gratify_workspace_private.personal_links; pid uuid; answer jsonb; old_flag text; old_image text;
BEGIN
 IF p_args IS NULL OR jsonb_typeof(p_args)<>'object' OR octet_length(p_args::text)>65536 THEN RAISE EXCEPTION 'Invalid request'; END IF;
 IF p_action NOT IN ('public','shared_artwork') AND (me IS NULL OR NOT EXISTS(SELECT 1 FROM auth.users WHERE id=me)) THEN
  RAISE EXCEPTION 'Authentication required' USING ERRCODE='42501';
 END IF;
 IF p_action='resolve_personal' THEN
  SELECT playlist_id INTO pid FROM gratify_workspace_private.personal_links WHERE owner_id=me AND sync_id=p_args->>'sync_id';
  RETURN jsonb_build_object('id',pid);
 END IF;
 IF p_action='promote' THEN
  IF length(p_args->>'sync_id') NOT BETWEEN 16 AND 128 THEN RAISE EXCEPTION 'Invalid identity'; END IF;
  PERFORM pg_advisory_xact_lock(hashtextextended(me::text||':playlist:'||(p_args->>'sync_id'),0));
  SELECT * INTO cp FROM public.cloud_playlists WHERE user_id=me AND client_sync_id=p_args->>'sync_id' FOR UPDATE;
  IF cp.id IS NULL THEN RAISE EXCEPTION 'Playlist not synchronized'; END IF;
  SELECT * INTO l FROM gratify_workspace_private.personal_links WHERE cloud_id=cp.id;
  IF l.playlist_id IS NOT NULL THEN RETURN jsonb_build_object('id',l.playlist_id); END IF;
  IF EXISTS(SELECT 1 FROM gratify_workspace_private.playlists WHERE id=cp.id) THEN RAISE EXCEPTION 'Identity conflict'; END IF;
  -- Import once before handing canonical writes to member operations. Repeated
  -- invitations never reimport an owner's stale snapshot over collaborators.
  IF length(btrim(cp.title)) NOT BETWEEN 1 AND 100 THEN RAISE EXCEPTION 'Title too long'; END IF;
  INSERT INTO gratify_workspace_private.playlists(id,owner_id,kind,title,is_public)
   VALUES(cp.id,me,'collaborative',cp.title,cp.is_public);
  INSERT INTO gratify_workspace_private.members(playlist_id,user_id) VALUES(cp.id,me);
  INSERT INTO gratify_workspace_private.items(playlist_id,video_id,title,artist,thumbnail_url,duration,position,added_by)
   SELECT cp.id,video_id,title,coalesce(artist,''),thumbnail_url,coalesce(duration,0),position,me
   FROM public.cloud_playlist_items WHERE playlist_id=cp.id ORDER BY position,id;
  INSERT INTO gratify_workspace_private.personal_links(playlist_id,cloud_id,owner_id,sync_id,thumbnail_url)
   VALUES(cp.id,cp.id,me,cp.client_sync_id,CASE WHEN cp.thumbnail_url LIKE '%i.ytimg.com/%' THEN NULL ELSE cp.thumbnail_url END);
  UPDATE gratify_workspace_private.placements SET ref='workspace:'||cp.id
   WHERE user_id=me AND ref='local:'||cp.client_sync_id;
  PERFORM gratify_workspace_private.project_playlist(cp.id);
  RETURN jsonb_build_object('id',cp.id);
 END IF;
 IF p_action='resolve_shared' THEN
  SELECT pl.playlist_id INTO pid FROM gratify_workspace_private.personal_links pl
   JOIN public.shared_playlists sp ON sp.user_id=pl.owner_id AND sp.client_sync_id=pl.sync_id
   JOIN gratify_workspace_private.playlists wp ON wp.id=pl.playlist_id
   WHERE sp.id::text=p_args->>'shared_id' AND (wp.is_public OR EXISTS(
    SELECT 1 FROM gratify_workspace_private.members WHERE playlist_id=wp.id AND user_id=me));
  RETURN jsonb_build_object('id',pid);
 END IF;
 pid:=nullif(p_args->>'playlist_id','')::uuid;
 SELECT * INTO l FROM gratify_workspace_private.personal_links WHERE playlist_id=pid;
 IF p_action='metadata' AND p_args ? 'cover_path' AND l.playlist_id IS NOT NULL THEN
  IF NOT EXISTS(SELECT 1 FROM gratify_workspace_private.playlists WHERE id=pid AND owner_id=me) THEN RAISE EXCEPTION 'Owner required'; END IF;
  UPDATE gratify_workspace_private.personal_links SET thumbnail_url=NULL WHERE playlist_id=pid;
 END IF;
 IF p_action='create' AND p_args->>'kind'='personal' THEN
  PERFORM pg_advisory_xact_lock(hashtextextended(me::text,20261012));
  IF coalesce((p_args->>'automatic_name')::boolean,false) AND NOT EXISTS(SELECT 1 FROM gratify_workspace_private.playlists WHERE id=(p_args->>'id')::uuid) THEN
   p_args:=p_args||gratify_workspace_private.lifecycle_api('next_name',jsonb_build_object('kind','personal'));
  END IF;
  p_args:=p_args||jsonb_build_object('kind','collaborative','automatic_name',false);
 END IF;
 answer:=gratify_workspace_private.lifecycle_api(p_action,p_args);
 IF p_action='create' THEN pid:=(answer->>'id')::uuid; END IF;
 IF p_action IN ('create','metadata','add','remove','reorder','restrict') THEN
  PERFORM gratify_workspace_private.project_playlist(pid);
 ELSIF p_action='delete' AND l.playlist_id IS NOT NULL THEN
  -- Owner authorization was checked by the delegated mutation before cleanup.
  INSERT INTO public.playlist_sync_tombstones(user_id,client_sync_id) VALUES(l.owner_id::text,l.sync_id) ON CONFLICT DO NOTHING;
  DELETE FROM public.cloud_playlists WHERE id=l.cloud_id;
  DELETE FROM public.shared_playlists WHERE user_id=l.owner_id AND client_sync_id=l.sync_id;
 END IF;
 IF p_action='detail' THEN
  SELECT * INTO l FROM gratify_workspace_private.personal_links WHERE playlist_id=pid;
  answer:=answer||jsonb_build_object('client_sync_id',l.sync_id,'legacy_thumbnail_url',l.thumbnail_url);
 ELSIF p_action='library' THEN
  answer:=answer||jsonb_build_object('playlists',(SELECT coalesce(jsonb_agg(entry||jsonb_build_object(
   'client_sync_id',link.sync_id,'legacy_thumbnail_url',link.thumbnail_url)),'[]'::jsonb)
   FROM jsonb_array_elements(answer->'playlists')entry LEFT JOIN gratify_workspace_private.personal_links link ON link.playlist_id=(entry->>'id')::uuid));
 ELSIF p_action='public' THEN
  -- Linked playlists are already in the public cloud projection. One card only.
  answer:=(SELECT coalesce(jsonb_agg(entry),'[]'::jsonb) FROM jsonb_array_elements(answer)entry
   WHERE NOT EXISTS(SELECT 1 FROM gratify_workspace_private.personal_links WHERE playlist_id=(entry->>'id')::uuid));
 END IF;
 RETURN answer;
END $$;
REVOKE ALL ON FUNCTION gratify_workspace_private.api(text,jsonb) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION gratify_workspace_private.api(text,jsonb) TO anon,authenticated;

-- Existing uploaded personal covers remain readable to explicitly joined members.
CREATE FUNCTION gratify_workspace_private.can_read_personal_cover(p_name text) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
 SELECT auth.uid() IS NOT NULL AND EXISTS(SELECT 1 FROM gratify_workspace_private.personal_links l
  JOIN gratify_workspace_private.members m ON m.playlist_id=l.playlist_id
  WHERE m.user_id=auth.uid() AND split_part(l.thumbnail_url,'/storage/v1/object/public/playlist-covers/',2)=p_name)
$$;
REVOKE ALL ON FUNCTION gratify_workspace_private.can_read_personal_cover(text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION gratify_workspace_private.can_read_personal_cover(text) TO authenticated;
CREATE POLICY canonical_member_cover ON storage.objects FOR SELECT TO authenticated
 USING(bucket_id='playlist-covers' AND gratify_workspace_private.can_read_personal_cover(name));

-- Profile pins are separate from title/track snapshots and remain owner-only.
-- Preserve old clients' pin operation while disallowing their stale replacements.
ALTER FUNCTION public.gratify_replace_cloud_playlist_v2(text,text,text,boolean,jsonb) SET SCHEMA gratify_workspace_private;
REVOKE ALL ON FUNCTION gratify_workspace_private.gratify_replace_cloud_playlist_v2(text,text,text,boolean,jsonb) FROM PUBLIC,anon,authenticated;
CREATE FUNCTION public.gratify_replace_cloud_playlist_v2(p_sync_id text,p_title text,p_thumbnail_url text,p_is_public boolean,p_tracks jsonb)
 RETURNS TABLE(playlist_id text) LANGUAGE plpgsql SECURITY INVOKER SET search_path='' AS $$
BEGIN
 RETURN QUERY SELECT * FROM gratify_workspace_private.replace_personal_snapshot(p_sync_id,p_title,p_thumbnail_url,p_is_public,p_tracks);
END $$;
CREATE FUNCTION gratify_workspace_private.replace_personal_snapshot(p_sync_id text,p_title text,p_thumbnail_url text,p_is_public boolean,p_tracks jsonb)
 RETURNS TABLE(playlist_id text) LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
BEGIN
 IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Authentication required'; END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended(auth.uid()::text||':playlist:'||p_sync_id,0));
 IF EXISTS(SELECT 1 FROM gratify_workspace_private.personal_links WHERE owner_id=auth.uid() AND sync_id=p_sync_id)
 THEN RAISE EXCEPTION 'Canonical playlist requires member operations' USING ERRCODE='42501'; END IF;
 RETURN QUERY SELECT * FROM gratify_workspace_private.gratify_replace_cloud_playlist_v2(p_sync_id,p_title,p_thumbnail_url,p_is_public,p_tracks);
END $$;
REVOKE ALL ON FUNCTION public.gratify_replace_cloud_playlist_v2(text,text,text,boolean,jsonb) FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION gratify_workspace_private.replace_personal_snapshot(text,text,text,boolean,jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.gratify_replace_cloud_playlist_v2(text,text,text,boolean,jsonb) TO authenticated;
GRANT EXECUTE ON FUNCTION gratify_workspace_private.replace_personal_snapshot(text,text,text,boolean,jsonb) TO authenticated;

-- Existing collaborative records also gain a personal/public projection without
-- changing their IDs, members, titles, covers or contents.
DO $$ DECLARE p record; BEGIN
 FOR p IN SELECT id FROM gratify_workspace_private.playlists WHERE kind='collaborative' LOOP
  PERFORM gratify_workspace_private.project_playlist(p.id);
 END LOOP;
END $$;
NOTIFY pgrst,'reload schema';
