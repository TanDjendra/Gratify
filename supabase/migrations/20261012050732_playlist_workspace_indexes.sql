-- Keep user deletion and playlist deletion bounded as collaboration grows.
CREATE INDEX workspace_invite_target ON gratify_workspace_private.invites(target_id);
CREATE INDEX workspace_item_author ON gratify_workspace_private.items(added_by);
CREATE INDEX workspace_operation_playlist ON gratify_workspace_private.operations(playlist_id);
