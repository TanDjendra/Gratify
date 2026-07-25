package com.tan.domain.data.model.social

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfileDto(
    @SerialName("id") val id: String,
    @SerialName("username") val username: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("bio") val bio: String? = null,
    @SerialName("now_playing_video_id") val nowPlayingVideoId: String? = null,
    @SerialName("now_playing_title") val nowPlayingTitle: String? = null,
    @SerialName("now_playing_artist") val nowPlayingArtist: String? = null,
    @SerialName("last_active_at") val lastActiveAt: String? = null,
)

@Serializable
data class CloudPlaylistDto(
    @SerialName("id") val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("local_playlist_id") val localPlaylistId: Long? = null,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("is_public") val isPublic: Boolean = false,
)

@Serializable
data class CloudPlaylistItemDto(
    @SerialName("id") val id: String? = null,
    @SerialName("playlist_id") val playlistId: String? = null,
    @SerialName("video_id") val videoId: String,
    @SerialName("title") val title: String,
    @SerialName("artist") val artist: String,
    @SerialName("duration") val duration: Int = 0,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("position") val position: Int = 0,
)
