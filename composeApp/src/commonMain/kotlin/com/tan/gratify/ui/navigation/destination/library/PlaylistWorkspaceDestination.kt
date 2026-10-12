package com.tan.gratify.ui.navigation.destination.library

import kotlinx.serialization.Serializable

@Serializable
data class PlaylistWorkspaceDestination(val mode: String, val id: String = "", val token: String = "", val title: String = "", val parentId: String = "", val inviteOnOpen:Boolean=false)
