package com.tan.domain.repository

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

const val YOUTUBE_READ_SCOPE = "https://www.googleapis.com/auth/youtube.readonly"

enum class YouTubeConnectionStatus { LOADING, CONNECTED, DISCONNECTED, RECONNECT, CONFIGURATION_REQUIRED, ERROR }
data class YouTubeConnectionState(val ownerId: String? = null,
    val status: YouTubeConnectionStatus = YouTubeConnectionStatus.LOADING)

@Serializable
data class YouTubeLibraryItem(val id: String, val title: String, val author: String = "YouTube",
    val thumbnail: String? = null, val trackCount: Int? = null, val durationSeconds: Int? = null)
@Serializable
data class YouTubeLibraryPage(val items: List<YouTubeLibraryItem> = emptyList(), val nextPageToken: String? = null)

interface YouTubeLibraryRepository {
    suspend fun isLoginConsentEnabled(): Boolean = false
    fun cancelConnection() {}
    val connection: StateFlow<YouTubeConnectionState>
    suspend fun refreshConnection()
    suspend fun beginConnection(): String
    suspend fun playlists(pageToken: String? = null): YouTubeLibraryPage
    suspend fun tracks(playlistId: String, pageToken: String? = null): YouTubeLibraryPage
    suspend fun disconnect()
}
