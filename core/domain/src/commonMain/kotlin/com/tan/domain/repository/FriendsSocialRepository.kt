package com.tan.domain.repository

import com.tan.domain.data.model.social.*

interface FriendsSocialRepository {
    suspend fun stories(publicFeed: Boolean, offset: Int = 0): List<MusicStory>
    suspend fun story(id: String): MusicStory? = stories(false).firstOrNull { it.id == id }
    suspend fun publishStory(draft: MusicStoryDraft): String
    suspend fun deleteStory(id: String)
    suspend fun inbox(): List<DirectConversation>
    suspend fun openConversation(peerId: String): String
    suspend fun messages(conversationId: String, beforeId: String? = null): List<DirectMessage>
    suspend fun sendMessage(conversationId: String, text: String, clientId: String): DirectMessage
    suspend fun respondToRequest(conversationId: String, accept: Boolean)
    suspend fun markRead(conversationId: String, lastMessageId: String)
}
