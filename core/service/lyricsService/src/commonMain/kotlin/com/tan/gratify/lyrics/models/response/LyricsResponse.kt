package com.tan.gratify.lyrics.models.response

import kotlinx.serialization.Serializable

@Serializable
data class LyricsResponse(
    val id: String = "",
    val videoId: String = "",
    val songTitle: String = "",
    val artistName: String = "",
    val albumName: String = "",
    val durationSeconds: Int = 0,
    val plainLyric: String = "",
    val syncedLyrics: String? = null,
    val richSyncLyrics: String? = null,
    val trackType: String? = null,
    val vote: Int = 0,
    val contributor: String = "",
    val contributorEmail: String = "",
)