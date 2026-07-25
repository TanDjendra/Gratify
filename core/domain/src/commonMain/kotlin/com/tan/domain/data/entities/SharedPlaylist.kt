package com.tan.domain.data.entities

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SharedPlaylist(
    @SerialName("id")
    val id: String? = null,
    @SerialName("user_id")
    val userId: String,
    @SerialName("title")
    val title: String,
    @SerialName("thumbnail_url")
    val thumbnailUrl: String? = null,
    @SerialName("creator_name")
    val creatorName: String? = null,
    @SerialName("add_count")
    val addCount: Int = 0,
    @SerialName("created_at")
    val createdAt: String? = null
)

@Serializable
data class SharedPlaylistTrack(
    @SerialName("id")
    val id: String? = null,
    @SerialName("playlist_id")
    val playlistId: String,
    @SerialName("video_id")
    val videoId: String,
    @SerialName("title")
    val title: String,
    @SerialName("artists")
    val artists: String,
    @SerialName("duration_seconds")
    val durationSeconds: Int? = null,
    @SerialName("created_at")
    val createdAt: String? = null
)
