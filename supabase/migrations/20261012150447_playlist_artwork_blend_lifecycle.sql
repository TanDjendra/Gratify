-- Preserve the existing RPC and its owner/target/expiry checks. New collection
-- reads contain bounded artwork, and single-member Blends remain invitation drafts.
ALTER FUNCTION gratify_workspace_private.api(text,jsonb) RENAME TO presentation_api;
REVOKE ALL ON FUNCTION gratify_workspace_private.presentation_api(text,jsonb) FROM PUBLIC,anon,authenticated;

CREATE FUNCTION gratify_workspace_private.api(p_action text,p_args jsonb) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $fn$
DECLARE me uuid:=auth.uid(); answer jsonb; pid uuid; h gratify_workspace_private.playlists;
 prefix text; n integer:=1; titles jsonb; args jsonb:=p_args;
BEGIN
 IF p_args IS NULL OR jsonb_typeof(p_args)<>'object' OR octet_length(p_args::text)>65536 THEN RAISE EXCEPTION 'Invalid request'; END IF;
 IF p_action='shared_artwork' THEN
  IF jsonb_typeof(p_args->'ids') IS DISTINCT FROM 'array' OR jsonb_array_length(p_args->'ids')>100 THEN RAISE EXCEPTION 'Invalid artwork request'; END IF;
  RETURN (SELECT coalesce(jsonb_object_agg(requested.id,
    (SELECT coalesce(jsonb_agg('https://i.ytimg.com/vi/'||t.video_id||'/mqdefault.jpg' ORDER BY t.position,t.id),'[]'::jsonb)
     FROM (SELECT st.id,st.video_id,st.position FROM public.shared_playlist_tracks st WHERE st.playlist_id=sp.id ORDER BY st.position,st.id LIMIT 4) t)),'{}'::jsonb)
   FROM jsonb_array_elements_text(p_args->'ids') requested(id)
   JOIN public.shared_playlists sp ON sp.id::text=requested.id OR EXISTS(
    SELECT 1 FROM public.cloud_playlists cp WHERE cp.id::text=requested.id AND cp.user_id=sp.user_id AND cp.client_sync_id=sp.client_sync_id)
   WHERE (me=sp.user_id AND EXISTS(SELECT 1 FROM auth.users WHERE id=me)) OR EXISTS(
    SELECT 1 FROM public.cloud_playlists cp WHERE cp.user_id=sp.user_id AND cp.client_sync_id=sp.client_sync_id AND cp.is_public));
 END IF;
 IF p_action='public' THEN
  answer:=gratify_workspace_private.presentation_api(p_action,args);
  RETURN (SELECT coalesce(jsonb_agg(entry||jsonb_build_object('automatic_images',
    (SELECT coalesce(jsonb_agg(coalesce(t.thumbnail_url,'https://i.ytimg.com/vi/'||t.video_id||'/mqdefault.jpg') ORDER BY t.position,t.id),'[]'::jsonb)
     FROM (SELECT i.id,i.video_id,i.thumbnail_url,i.position FROM gratify_workspace_private.items i WHERE i.playlist_id=(entry->>'id')::uuid ORDER BY i.position,i.id LIMIT 4)t))),'[]'::jsonb)
   FROM jsonb_array_elements(answer) entry);
 END IF;
 IF me IS NULL OR NOT EXISTS(SELECT 1 FROM auth.users WHERE id=me) THEN RAISE EXCEPTION 'Authentication required' USING ERRCODE='42501'; END IF;
 IF p_action='next_name' THEN
  IF p_args->>'kind' NOT IN ('personal','collaborative') THEN RAISE EXCEPTION 'Invalid kind'; END IF;
  prefix:=CASE WHEN p_args->>'kind'='collaborative' THEN 'Playlist kami No.' ELSE 'Playlist-ku No.' END;
  titles:=coalesce(p_args->'titles','[]'::jsonb);
  IF jsonb_typeof(titles)<>'array' OR jsonb_array_length(titles)>300 THEN RAISE EXCEPTION 'Invalid names'; END IF;
  WHILE EXISTS(SELECT 1 FROM public.cloud_playlists WHERE user_id=me AND lower(btrim(title))=lower(prefix||n))
     OR EXISTS(SELECT 1 FROM gratify_workspace_private.playlists WHERE owner_id=me AND lower(btrim(title))=lower(prefix||n))
     OR EXISTS(SELECT 1 FROM jsonb_array_elements_text(titles)t(title) WHERE lower(btrim(t.title))=lower(prefix||n)) LOOP n:=n+1; END LOOP;
  RETURN jsonb_build_object('title',prefix||n);
 END IF;
 IF p_action='create' AND p_args->>'kind'='collaborative' AND coalesce((p_args->>'automatic_name')::boolean,false) THEN
  PERFORM pg_advisory_xact_lock(hashtextextended(me::text,20261012));
  -- A retry with the same ID returns its existing playlist without renaming it.
  IF NOT EXISTS(SELECT 1 FROM gratify_workspace_private.playlists WHERE id=(p_args->>'id')::uuid) THEN
   args:=args||gratify_workspace_private.api('next_name',jsonb_build_object('kind','collaborative'));
  END IF;
 END IF;
 IF p_action='library' THEN
  FOR pid IN SELECT p.id FROM gratify_workspace_private.playlists p JOIN gratify_workspace_private.members m ON m.playlist_id=p.id
    WHERE p.kind='blend' AND m.user_id=me AND m.consent AND (p.generated_at IS NULL OR p.generated_at<=now()-interval '1 day' OR NOT EXISTS(SELECT 1 FROM gratify_workspace_private.items i WHERE i.playlist_id=p.id))
    AND (SELECT count(*) FROM gratify_workspace_private.members bm WHERE bm.playlist_id=p.id AND bm.consent)>=2 LOOP
   PERFORM gratify_workspace_private.api('refresh_blend',jsonb_build_object('playlist_id',pid));
  END LOOP;
  answer:=gratify_workspace_private.presentation_api(p_action,args);
  RETURN answer||jsonb_build_object('playlists',(SELECT coalesce(jsonb_agg(entry||jsonb_build_object('items',
    (SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY t.position,t.id),'[]'::jsonb)
     FROM (SELECT i.* FROM gratify_workspace_private.items i WHERE i.playlist_id=(entry->>'id')::uuid ORDER BY i.position,i.id LIMIT 4)t))),'[]'::jsonb)
   FROM jsonb_array_elements(answer->'playlists') entry
   WHERE entry->>'kind'<>'blend' OR (SELECT count(*) FROM gratify_workspace_private.members m WHERE m.playlist_id=(entry->>'id')::uuid AND m.consent)>=2),
   'placements',(SELECT coalesce(jsonb_agg(entry),'[]'::jsonb) FROM jsonb_array_elements(answer->'placements')entry
     WHERE NOT EXISTS(SELECT 1 FROM gratify_workspace_private.playlists p WHERE entry->>'ref'='workspace:'||p.id AND p.kind='blend'
       AND (SELECT count(*) FROM gratify_workspace_private.members m WHERE m.playlist_id=p.id AND m.consent)<2)));
 END IF;
 IF p_action='refresh_blend' THEN
  -- An empty cold-start result must not prevent newly available taste data from
  -- forming the first mix for an entire day. Filled mixes keep the daily cache.
  UPDATE gratify_workspace_private.playlists p SET generated_at=NULL
   WHERE p.id=(p_args->>'playlist_id')::uuid AND p.kind='blend' AND p.generated_at IS NOT NULL
   AND EXISTS(SELECT 1 FROM gratify_workspace_private.members m WHERE m.playlist_id=p.id AND m.user_id=me AND consent)
   AND NOT EXISTS(SELECT 1 FROM gratify_workspace_private.items i WHERE i.playlist_id=p.id);
 END IF;
 answer:=gratify_workspace_private.presentation_api(p_action,args);
 IF p_action='join' THEN
  pid:=(answer->>'id')::uuid;
  SELECT * INTO h FROM gratify_workspace_private.playlists WHERE id=pid;
  IF h.kind='blend' THEN PERFORM gratify_workspace_private.presentation_api('refresh_blend',jsonb_build_object('playlist_id',pid)); END IF;
 END IF;
 RETURN answer;
END $fn$;
REVOKE ALL ON FUNCTION gratify_workspace_private.api(text,jsonb) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION gratify_workspace_private.api(text,jsonb) TO anon,authenticated;
CREATE OR REPLACE FUNCTION public.gratify_workspace(p_action text,p_args jsonb DEFAULT '{}'::jsonb) RETURNS jsonb
LANGUAGE sql SECURITY INVOKER SET search_path='' AS $$ SELECT gratify_workspace_private.api(p_action,p_args) $$;
REVOKE ALL ON FUNCTION public.gratify_workspace(text,jsonb) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.gratify_workspace(text,jsonb) TO anon,authenticated;

-- This entry point is only executable by the server job owner. No API role can
-- choose another user's identity. Restore the job's claim on every exit.
CREATE FUNCTION gratify_workspace_private.refresh_due_blends() RETURNS integer
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $fn$
DECLARE p record; updated integer:=0; old_claim text:=current_setting('request.jwt.claim.sub',true);
BEGIN
 FOR p IN SELECT id,owner_id FROM gratify_workspace_private.playlists w WHERE kind='blend'
  AND (generated_at IS NULL OR generated_at<=now()-interval '1 day' OR NOT EXISTS(SELECT 1 FROM gratify_workspace_private.items i WHERE i.playlist_id=w.id))
  AND (SELECT count(*) FROM gratify_workspace_private.members m WHERE m.playlist_id=w.id AND consent)>=2
  ORDER BY generated_at NULLS FIRST,id LIMIT 100 LOOP
  PERFORM set_config('request.jwt.claim.sub',p.owner_id::text,true);
  PERFORM gratify_workspace_private.api('refresh_blend',jsonb_build_object('playlist_id',p.id));
  updated:=updated+1;
 END LOOP;
 PERFORM set_config('request.jwt.claim.sub',coalesce(old_claim,''),true);
 RETURN updated;
EXCEPTION WHEN OTHERS THEN
 PERFORM set_config('request.jwt.claim.sub',coalesce(old_claim,''),true);
 RAISE;
END $fn$;
REVOKE ALL ON FUNCTION gratify_workspace_private.refresh_due_blends() FROM PUBLIC,anon,authenticated;
DO $cron$
BEGIN
 IF EXISTS(SELECT 1 FROM pg_extension WHERE extname='pg_cron') THEN
  PERFORM cron.schedule('gratify-refresh-due-blends','15 * * * *','select gratify_workspace_private.refresh_due_blends();');
 END IF;
END $cron$;
