package com.tan.domain.utils

/** Archived until their flows are ready. Keep source/data so development can resume. */
object PlaylistFeaturePolicy {
    const val experimentalEnabled = false
    const val archivedMessage = "Fitur ini belum tersedia pada versi ini."

    fun allowsWorkspaceMode(mode: String, enabled: Boolean = experimentalEnabled): Boolean =
        enabled || mode == "detail"

    fun allowsAction(action: String, kind: String? = null, enabled: Boolean = experimentalEnabled): Boolean =
        enabled || (action !in setOf("promote", "invite", "join", "send_invite", "revoke_invite", "revoke_invites", "refresh_blend",
            "remove_member", "restrict", "folder_create", "folder_rename", "folder_move", "folder_delete", "move") &&
            (action != "create" || kind == "personal"))
}
