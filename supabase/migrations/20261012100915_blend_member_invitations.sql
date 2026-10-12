-- Extend invitation rights only to consenting Blend members. Other owner rights,
-- token target/expiry checks, member limits and the private function ACL stay intact.
DO $migration$
DECLARE source text;
BEGIN
 SELECT pg_get_functiondef('gratify_workspace_private.base_api(text,jsonb)'::regprocedure) INTO source;
 IF position('can_invite boolean' IN source)>0 THEN RETURN; END IF;
 IF position('role_owner boolean;' IN source)=0 OR
    position('role_owner:=h.owner_id=me;' IN source)=0 OR
    position('AND NOT (p_action=''send_invite'' AND role_owner)' IN source)=0 OR
    position('IF NOT role_owner OR inv.target_id IS DISTINCT FROM peer' IN source)=0 OR
    position('IF p_action IN (''invite'',''revoke_invites'',''metadata'',''delete'',''remove_member'',''restrict'') AND NOT role_owner' IN source)=0
 THEN RAISE EXCEPTION 'Workspace invitation implementation changed; review migration'; END IF;
 source:=replace(source,'role_owner boolean;','role_owner boolean; can_invite boolean;');
 source:=replace(source,'role_owner:=h.owner_id=me;',
  'role_owner:=h.owner_id=me; can_invite:=role_owner OR (h.kind=''blend'' AND EXISTS(SELECT 1 FROM gratify_workspace_private.members WHERE playlist_id=pid AND user_id=me AND consent));');
 source:=replace(source,'AND NOT (p_action=''send_invite'' AND role_owner)','AND NOT (p_action=''send_invite'' AND can_invite)');
 source:=replace(source,'IF NOT role_owner OR inv.target_id IS DISTINCT FROM peer','IF NOT can_invite OR inv.target_id IS DISTINCT FROM peer');
 source:=replace(source,'IF p_action IN (''invite'',''revoke_invites'',''metadata'',''delete'',''remove_member'',''restrict'') AND NOT role_owner',
  'IF p_action=''invite'' AND NOT can_invite THEN RAISE EXCEPTION ''Invitation permission required'' USING ERRCODE=''42501''; END IF; IF p_action IN (''revoke_invites'',''metadata'',''delete'',''remove_member'',''restrict'') AND NOT role_owner');
 EXECUTE source;
END $migration$;
