package com.tan.gratify.viewModel

import com.tan.domain.data.model.social.DirectMessage
import kotlin.test.*

class ChatMessageWindowTest {
    private fun message(i: Int) = DirectMessage("m-$i", "chat", "peer", "c-$i", "text-$i", i.toString().padStart(8, '0'))

    @Test fun fiftyThousandIncomingMessagesStayBoundedAndKeepTheLatestVersion() {
        var window = emptyList<DirectMessage>()
        for (start in 0 until 50_000 step 50) window = mergeChatMessages(window, (start until start+50).map(::message))
        assertEquals(CHAT_MESSAGE_WINDOW, window.size)
        assertEquals("m-49500", window.first().id)
        assertEquals("m-49999", window.last().id)
        window = mergeChatMessages(window, listOf(message(49999).copy(body = "updated")))
        assertEquals("updated", window.last().body)
        assertEquals(CHAT_MESSAGE_WINDOW, window.map { it.id }.distinct().size)
    }

    @Test fun olderPagesCanReachTheBeginningWithoutPollingMovingTheCursor() {
        var window = (950 until 1000).map(::message)
        for (start in 900 downTo 0 step 50) {
            window = mergeOlderChatMessages((start until start+50).map(::message), window)
            assertTrue(window.size <= CHAT_MESSAGE_WINDOW)
            val before = window.map { it.id }
            window = mergeChatMessages(window, (1000 until 1050).map(::message), preserveOlder = true)
            assertEquals(before, window.map { it.id })
        }
        assertEquals("m-0", window.first().id)
        assertEquals("m-499", window.last().id)
        val latest = mergeChatMessages(emptyList(), (1000 until 1050).map(::message))
        assertEquals("m-1049", latest.last().id)
    }
}
