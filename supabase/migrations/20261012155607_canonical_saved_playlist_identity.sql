-- A listener who already saved this public playlist joins the same local cache,
-- instead of gaining a second collection card for its member access.
DO $migration$
DECLARE source text; needle text:=$needle$answer:=answer||jsonb_build_object('client_sync_id',l.sync_id,'legacy_thumbnail_url',l.thumbnail_url);$needle$;
BEGIN
 SELECT pg_get_functiondef('gratify_workspace_private.api(text,jsonb)'::regprocedure) INTO source;
 IF position(needle IN source)=0 THEN RAISE EXCEPTION 'Canonical detail implementation changed'; END IF;
 source:=replace(source,needle,$replacement$answer:=answer||jsonb_build_object('client_sync_id',l.sync_id,'legacy_thumbnail_url',l.thumbnail_url,
  'source_shared_id',(SELECT sp.id FROM public.shared_playlists sp WHERE sp.user_id=l.owner_id AND sp.client_sync_id=l.sync_id));$replacement$);
 needle:=$needle$IF p_action='create' THEN pid:=(answer->>'id')::uuid; END IF;$needle$;
 IF position(needle IN source)=0 THEN RAISE EXCEPTION 'Canonical join implementation changed'; END IF;
 source:=replace(source,needle,$replacement$IF p_action='create' THEN pid:=(answer->>'id')::uuid; END IF;
 IF p_action='join' THEN
  pid:=(answer->>'id')::uuid;
  DELETE FROM gratify_workspace_private.placements x USING gratify_workspace_private.personal_links link,public.shared_playlists sp
   WHERE x.user_id=me AND link.playlist_id=pid AND sp.user_id=link.owner_id AND sp.client_sync_id=link.sync_id
    AND x.ref='shared:'||sp.id AND EXISTS(SELECT 1 FROM gratify_workspace_private.placements existing WHERE existing.user_id=me AND existing.ref='workspace:'||pid);
  UPDATE gratify_workspace_private.placements x SET ref='workspace:'||pid FROM gratify_workspace_private.personal_links link,public.shared_playlists sp
   WHERE x.user_id=me AND link.playlist_id=pid AND sp.user_id=link.owner_id AND sp.client_sync_id=link.sync_id AND x.ref='shared:'||sp.id;
 END IF;$replacement$);
 EXECUTE source;
END $migration$;
