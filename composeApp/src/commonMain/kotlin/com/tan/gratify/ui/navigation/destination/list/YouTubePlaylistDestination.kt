package com.tan.gratify.ui.navigation.destination.list
import kotlinx.serialization.Serializable
@Serializable
data class YouTubePlaylistDestination(val ownerId: String, val playlistId: String,
    val title: String, val thumbnail: String? = null)
