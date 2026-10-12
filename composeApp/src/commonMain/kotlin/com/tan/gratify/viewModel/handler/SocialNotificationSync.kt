package com.tan.gratify.viewModel.handler

import com.tan.domain.data.entities.NotificationEntity
import com.tan.domain.repository.CommonRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
internal data class SocialNotificationEvent(val id: String, @SerialName("actor_id") val actorId: String,
    val kind: String, @SerialName("source_id") val sourceId: String,
    @SerialName("created_at") val createdAt: String, @SerialName("display_name") val name: String? = null,
    @SerialName("avatar_url") val avatar: String? = null, @SerialName("conversation_id") val conversationId: String? = null)

suspend fun fetchSocialNotifications(client: SupabaseClient, owner: String, eventId: String? = null): List<NotificationEntity> = withTimeout(15_000) {
    if (client.auth.currentUserOrNull()?.id != owner) return@withTimeout emptyList()
    val events = client.postgrest.rpc("gratify_social_notifications", buildJsonObject {
        put("p_event", eventId?.let(::JsonPrimitive) ?: JsonNull); put("p_limit", 100)
    }).decodeList<SocialNotificationEvent>()
    if (client.auth.currentUserOrNull()?.id != owner) return@withTimeout emptyList()
    events.map { event ->
        NotificationEntity(channelId = if (event.kind == "story") "social_story" else "social_message", thumbnail = event.avatar,
            name = event.name?.takeIf { it.isNotBlank() } ?: "User",
            single = listOf(mapOf("browseId" to event.id, "ownerId" to owner, "actorId" to event.actorId,
                "sourceId" to event.sourceId, "kind" to event.kind,
                "title" to when (event.kind) { "story" -> "membagikan story musik"; "message_request" -> "mengirim permintaan pesan"; else -> "mengirim pesan baru" })),
            time = Instant.parse(event.createdAt).toLocalDateTime(TimeZone.currentSystemDefault()))
    }
}

internal suspend fun syncSocialNotifications(client: SupabaseClient, repository: CommonRepository) {
    val owner = client.auth.currentUserOrNull()?.id ?: return
    if (repository.notificationAccountOwner() != owner) return
    fetchSocialNotifications(client, owner).forEach { repository.insertNotificationOnce(it) }
}
