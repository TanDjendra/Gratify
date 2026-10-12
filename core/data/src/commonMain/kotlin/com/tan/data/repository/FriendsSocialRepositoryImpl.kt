package com.tan.data.repository

import com.tan.domain.data.model.social.*
import com.tan.domain.repository.FriendsSocialRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.*

internal class FriendsSocialRepositoryImpl(private val supabase: SupabaseClient) : FriendsSocialRepository {
    override suspend fun story(id: String): MusicStory? = rpc("gratify_notification_story", buildJsonObject {
        put("p_story", id)
    }).decodeList<MusicStory>().firstOrNull()
    private suspend fun rpc(name: String, parameters: JsonObject) = supabase.postgrest.rpc(name, parameters)
    override suspend fun stories(publicFeed: Boolean, offset: Int) = rpc("gratify_music_stories", buildJsonObject {
        put("p_public", publicFeed); put("p_offset", offset)
    }).decodeList<MusicStory>()
    override suspend fun publishStory(draft: MusicStoryDraft): String {
        require(draft.isValid)
        return rpc("gratify_publish_music_story", buildJsonObject {
            put("p_video_id", draft.track.videoId); put("p_title", draft.track.title)
            put("p_artist", draft.track.artist); put("p_thumbnail_url", draft.track.thumbnailUrl?.let(::JsonPrimitive) ?: JsonNull)
            put("p_track_duration_ms", draft.track.durationMs); put("p_start_ms", draft.startMs)
            put("p_clip_seconds", draft.clipSeconds); put("p_caption", draft.caption.trim()); put("p_audience", draft.audience)
        }).decodeAs<JsonPrimitive>().content
    }
    override suspend fun deleteStory(id: String) {
        supabase.postgrest["music_stories"].delete { filter { eq("id", id) } }
    }
    override suspend fun inbox() = rpc("gratify_dm_inbox", buildJsonObject {}).decodeList<DirectConversation>()
    override suspend fun openConversation(peerId: String) = rpc("gratify_dm_open", buildJsonObject {
        put("p_peer", peerId)
    }).decodeAs<JsonPrimitive>().content
    override suspend fun messages(conversationId: String, beforeId: String?) = rpc("gratify_dm_messages", buildJsonObject {
        put("p_conversation", conversationId); put("p_before", beforeId?.let(::JsonPrimitive) ?: JsonNull)
    }).decodeList<DirectMessage>()
    override suspend fun sendMessage(conversationId: String, text: String, clientId: String) = rpc("gratify_dm_send", buildJsonObject {
        put("p_conversation", conversationId); put("p_body", text.trim()); put("p_client_id", clientId)
    }).decodeSingle<DirectMessage>()
    override suspend fun respondToRequest(conversationId: String, accept: Boolean) {
        rpc("gratify_dm_respond", buildJsonObject { put("p_conversation", conversationId); put("p_accept", accept) })
    }
    override suspend fun markRead(conversationId: String, lastMessageId: String) {
        rpc("gratify_dm_read", buildJsonObject { put("p_conversation", conversationId); put("p_message", lastMessageId) })
    }
}
