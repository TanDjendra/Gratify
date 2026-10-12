package com.tan.domain.data.model.social

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MusicStory(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("owner_name") val ownerName: String? = null,
    @SerialName("owner_avatar") val ownerAvatar: String? = null,
    @SerialName("video_id") val videoId: String,
    val title: String,
    val artist: String,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("track_duration_ms") val trackDurationMs: Long,
    @SerialName("start_ms") val startMs: Long,
    @SerialName("clip_seconds") val clipSeconds: Int,
    val caption: String = "",
    val audience: String = "friends",
    @SerialName("created_at") val createdAt: String,
    @SerialName("expires_at") val expiresAt: String,
)

@Serializable
data class DirectConversation(
    val id: String,
    @SerialName("peer_id") val peerId: String,
    @SerialName("peer_name") val peerName: String? = null,
    @SerialName("peer_avatar") val peerAvatar: String? = null,
    val accepted: Boolean,
    @SerialName("request_from") val requestFrom: String,
    @SerialName("last_body") val lastBody: String? = null,
    @SerialName("last_sender_id") val lastSenderId: String? = null,
    @SerialName("last_at") val lastAt: String? = null,
    @SerialName("peer_read_at") val peerReadAt: String? = null,
    @SerialName("unread_count") val unreadCount: Int = 0,
)

@Serializable
data class DirectMessage(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("client_id") val clientId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String,
    val kind: String = "text",
    @SerialName("invitation_id") val invitationId: String? = null,
)

@Serializable
data class StoryTrack(val videoId: String, val title: String, val artist: String,
    val thumbnailUrl: String?, val durationMs: Long)

/** Bounds are shared by the editor and native playback; the server independently enforces them. */
@Serializable
data class MusicStoryDraft(val track: StoryTrack, val startMs: Long = 0,
    val clipSeconds: Int = 15, val caption: String = "", val audience: String = "friends") {
    val maxStartMs: Long get() = (track.durationMs - clipSeconds * 1000L).coerceAtLeast(0)
    val isValid: Boolean get() = track.videoId.isNotBlank() && track.title.isNotBlank() &&
        clipSeconds in listOf(8, 15, 30) && track.durationMs >= clipSeconds * 1000L &&
        track.durationMs <= 86_400_000L && startMs in 0..maxStartMs &&
        caption.length <= 200 && audience in listOf("friends", "public")
}
