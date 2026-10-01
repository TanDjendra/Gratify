package com.tan.data.repository

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
internal data class PlaylistRpcResult(@SerialName("playlist_id") val playlistId: String)
