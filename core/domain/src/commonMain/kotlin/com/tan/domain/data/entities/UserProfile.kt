package com.tan.domain.data.entities

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    @SerialName("id")
    val id: String,
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("now_playing_video_id")
    val nowPlayingVideoId: String? = null,
    @SerialName("now_playing_title")
    val nowPlayingTitle: String? = null,
    @SerialName("now_playing_artist")
    val nowPlayingArtist: String? = null,
    @SerialName("last_active_at")
    val lastActiveAt: String? = null,
    @SerialName("top_artists")
    val topArtists: List<TopArtistDto>? = null,
    // Catatan singkat ala "Notes IG" untuk mengekspresikan lagu/mood, tampil ke teman.
    @SerialName("note")
    val note: String? = null,
    // Epoch millis (string) saat note terakhir diubah — dipakai untuk kedaluwarsa 24 jam.
    @SerialName("note_updated_at")
    val noteUpdatedAt: String? = null
)

@Serializable
data class NowPlayingUpdate(
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("now_playing_video_id")
    val nowPlayingVideoId: String? = null,
    @SerialName("now_playing_title")
    val nowPlayingTitle: String? = null,
    @SerialName("now_playing_artist")
    val nowPlayingArtist: String? = null,
    @SerialName("last_active_at")
    val lastActiveAt: String? = null
)
