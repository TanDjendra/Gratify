package com.tan.gratify.viewModel

import androidx.lifecycle.SavedStateHandle
import com.tan.domain.data.model.social.MusicStoryDraft
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** State belongs to the Friends entry AND its local account, never the YouTube account. */
class FriendsNavigationState(val saved: SavedStateHandle) {
    val owner = saved.getStateFlow<String?>("friends_owner", null)
    val storyDraft = saved.getStateFlow<String?>("story_draft", null)
    fun bindOwner(id: String?) {
        if (owner.value != id) {
            saved.keys().filter { it.startsWith("chat_") }
                .forEach { saved.remove<Any?>(it) }
            // set also updates existing StateFlow observers; remove alone detaches them.
            saved["story_draft"] = null
            saved["inbox_tab"] = "ACTIVITY"
            saved["friends_owner"] = id
        }
    }
    fun readStory(json: String? = storyDraft.value): MusicStoryDraft? =
        json?.let { runCatching { Json.decodeFromString<MusicStoryDraft>(it) }.getOrNull() }
    fun saveStory(draft: MusicStoryDraft?) { saved["story_draft"] = draft?.let { Json.encodeToString(it) } }
    fun stageStory(draft: MusicStoryDraft, replaceExisting: Boolean = false): Boolean {
        val existing = readStory()
        if (!replaceExisting && existing != null && existing != draft) return false
        saveStory(draft)
        return true
    }
    fun showChat(peer: String) { saved["chat_peer"] = peer }
    fun closeChat() { saved["chat_peer"] = null }
}
