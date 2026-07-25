package com.tan.domain.data.model.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CloudLikedSongDto(
    @SerialName("id") val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("video_id") val videoId: String,
    @SerialName("title") val title: String? = null,
    @SerialName("artist_name") val artistName: String? = null,
    @SerialName("artist_id") val artistId: String? = null,
    @SerialName("album_name") val albumName: String? = null,
    @SerialName("album_id") val albumId: String? = null,
    @SerialName("duration") val duration: Int? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("favorite_at") val favoriteAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CloudFollowedArtistDto(
    @SerialName("id") val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("channel_id") val channelId: String,
    @SerialName("name") val name: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("followed_at") val followedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CloudSavedAlbumDto(
    @SerialName("id") val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("browse_id") val browseId: String,
    @SerialName("title") val title: String? = null,
    @SerialName("artist_name") val artistName: String? = null,
    @SerialName("artist_id") val artistId: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("track_count") val trackCount: Int? = null,
    @SerialName("favorite_at") val favoriteAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CloudPlayHistoryDto(
    @SerialName("id") val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("video_id") val videoId: String,
    @SerialName("title") val title: String? = null,
    @SerialName("artist_name") val artistName: String? = null,
    @SerialName("duration") val duration: Int? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("played_at") val playedAt: String? = null,
)

@Serializable
data class CloudQueueItemDto(
    @SerialName("id") val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("video_id") val videoId: String,
    @SerialName("title") val title: String? = null,
    @SerialName("artist_name") val artistName: String? = null,
    @SerialName("duration") val duration: Int? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("position") val position: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CloudUserSettingsDto(
    @SerialName("user_id") val userId: String,
    @SerialName("settings_json") val settingsJson: String,
    @SerialName("updated_at") val updatedAt: String? = null,
)

data class SyncReport(
    val likedSongs: Int = 0,
    val artists: Int = 0,
    val albums: Int = 0,
    val history: Int = 0,
    val queue: Int = 0,
    val settingsSynced: Boolean = false,
)
