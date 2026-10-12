package com.tan.gratify.viewModel

import com.tan.domain.data.model.social.DirectMessage

internal const val CHAT_MESSAGE_WINDOW = 500
private val messageOrder = compareBy<DirectMessage> { it.createdAt }.thenBy { it.id }

/** Incoming versions replace old ones. While browsing history, polling preserves the cursor. */
internal fun mergeChatMessages(existing: List<DirectMessage>, incoming: List<DirectMessage>,
    preserveOlder: Boolean = false): List<DirectMessage> {
    if (preserveOlder) {
        val updates = incoming.associateBy(DirectMessage::id)
        return existing.take(CHAT_MESSAGE_WINDOW).map { updates[it.id] ?: it }
    }
    return (existing + incoming).asReversed().distinctBy(DirectMessage::id)
        .sortedWith(messageOrder).takeLast(CHAT_MESSAGE_WINDOW)
}

internal fun mergeOlderChatMessages(page: List<DirectMessage>, existing: List<DirectMessage>): List<DirectMessage> =
    (existing + page).asReversed().distinctBy(DirectMessage::id).sortedWith(messageOrder).take(CHAT_MESSAGE_WINDOW)
