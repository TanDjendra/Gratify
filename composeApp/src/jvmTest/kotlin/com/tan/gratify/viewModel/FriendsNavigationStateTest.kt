package com.tan.gratify.viewModel

import androidx.lifecycle.SavedStateHandle
import com.tan.domain.data.model.social.MusicStoryDraft
import com.tan.domain.data.model.social.StoryTrack
import kotlin.test.*

class FriendsNavigationStateTest {
    private val draft = MusicStoryDraft(StoryTrack("video", "Lagu", "Artis", "https://example.com/cover", 180000),
        43000, 30, "Baris pertama\nBaris kedua", "public")
    private fun snapshot(saved: SavedStateHandle) = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })

    @Test fun draftAndInboxSurviveRecreationForSameAccount() {
        val state = FriendsNavigationState(SavedStateHandle())
        state.bindOwner("one"); state.saveStory(draft)
        state.saved["inbox_tab"] = "REQUESTS"
        val restored = FriendsNavigationState(snapshot(state.saved))
        restored.bindOwner("one")
        assertEquals(draft, restored.readStory())
        assertEquals("REQUESTS", restored.saved.get<String>("inbox_tab"))
    }

    @Test fun accountSwitchClearsPrivateDraftsAndChatContextAndNotifiesObservers() {
        val state = FriendsNavigationState(SavedStateHandle())
        val observedDraft = state.storyDraft
        state.bindOwner("one"); state.saveStory(draft)
        state.showChat("peer")
        state.saved["chat_draft_conversation"] = "Belum dikirim"
        state.saved["chat_index_conversation"] = 27
        state.bindOwner("two")
        assertNull(observedDraft.value)
        assertNull(state.readStory())
        assertFalse(state.saved.keys().any { it.startsWith("chat_") })
        assertEquals("two", state.owner.value)
        assertEquals("ACTIVITY", state.saved.get<String>("inbox_tab"))
    }

    @Test fun signOutClearsPrivateDraftsAndReturnsToActivity() {
        val state = FriendsNavigationState(SavedStateHandle())
        state.bindOwner("one"); state.saveStory(draft); state.showChat("peer")
        state.saved["inbox_tab"] = "MAIN"
        state.bindOwner(null)
        assertNull(state.owner.value)
        assertNull(state.storyDraft.value)
        assertFalse(state.saved.keys().any { it.startsWith("chat_") })
        assertEquals("ACTIVITY", state.saved.get<String>("inbox_tab"))
    }

    @Test fun profileVisitKeepsConversationMessageDraftAndPosition() {
        val state = FriendsNavigationState(SavedStateHandle())
        state.bindOwner("one"); state.showChat("peer")
        state.saved["chat_draft_conversation"] = "Pesan yang belum selesai"
        state.saved["chat_index_conversation"] = 15
        state.saved["chat_offset_conversation"] = 52
        val restored = FriendsNavigationState(snapshot(state.saved))
        restored.bindOwner("one")
        assertEquals("peer", restored.saved.get<String>("chat_peer"))
        assertEquals("Pesan yang belum selesai", restored.saved.get<String>("chat_draft_conversation"))
        assertEquals(15, restored.saved.get<Int>("chat_index_conversation"))
        assertEquals(52, restored.saved.get<Int>("chat_offset_conversation"))
    }

    @Test fun publishedOrDiscardedDraftStaysClearedAfterRecreation() {
        val state = FriendsNavigationState(SavedStateHandle())
        state.bindOwner("one"); state.saveStory(draft); state.saveStory(null)
        val restored = FriendsNavigationState(snapshot(state.saved))
        restored.bindOwner("one")
        assertNull(restored.readStory())
    }

    @Test fun corruptedDraftDoesNotCrashPage() {
        val state = FriendsNavigationState(SavedStateHandle())
        state.bindOwner("one"); state.saved["story_draft"] = "invalid"
        assertNull(state.readStory())
    }

    @Test fun incomingLyricsCannotSilentlyOverwriteExistingDraft() {
        val state = FriendsNavigationState(SavedStateHandle())
        state.bindOwner("one"); state.saveStory(draft)
        val incoming = draft.copy(caption = "Pilihan lirik baru", startMs = 70000)
        assertFalse(state.stageStory(incoming))
        assertEquals(draft, state.readStory())
        assertTrue(state.stageStory(incoming, replaceExisting = true))
        assertEquals(incoming, state.readStory())
    }
}
