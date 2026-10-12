package com.tan.gratify.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.tan.domain.data.entities.UserProfile
import com.tan.domain.data.model.metadata.Lyrics
import com.tan.domain.data.model.social.*
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.repository.*
import com.tan.domain.utils.toLyrics
import com.tan.gratify.viewModel.handler.storyLyricsFlow
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

data class FriendsSocialState(
    val userId: String? = null,
    val stories: List<MusicStory> = emptyList(),
    val publicFeed: Boolean = false,
    val moreStories: Boolean = false,
    val conversations: List<DirectConversation> = emptyList(),
    val activeConversation: DirectConversation? = null,
    val messages: List<DirectMessage> = emptyList(),
    val moreMessages: Boolean = false,
    val browsingOlderMessages: Boolean = false,
    val people: List<UserProfile> = emptyList(),
    val tracks: List<StoryTrack> = emptyList(),
    val loading: Boolean = false,
    val busy: Boolean = false,
    val opening: Boolean = false,
    val searching: Boolean = false,
    val loadingOlder: Boolean = false,
    val chatLoading: Boolean = false,
    val chatError: String? = null,
    val error: String? = null,
    val backendUnavailable: Boolean = false,
)

class FriendsSocialViewModel(private val repository: FriendsSocialRepository,
    private val users: UserRepository, private val search: SearchRepository,
    private val streams: StreamRepository, private val settings: DataStoreManager,
    private val supabase: SupabaseClient, private val lyrics: LyricsCanvasRepository,
    savedStateHandle: SavedStateHandle = SavedStateHandle()) : ViewModel() {
    val navigation = FriendsNavigationState(savedStateHandle)
    private val _state = MutableStateFlow(FriendsSocialState())
    val state = _state.asStateFlow()
    private var pollJob: Job? = null
    private var refreshJob: Job? = null
    private var searchJob: Job? = null
    private var openJob: Job? = null
    private var olderJob: Job? = null
    private var storyOffset = 0
    private var pageGeneration = 0
    private var retrySend: Triple<String, String, String>? = null
    private fun account() = supabase.auth.currentUserOrNull()?.id
    private fun sameUser(id: String) = account() == id && _state.value.userId == id

    fun start() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            supabase.auth.sessionStatus.collectLatest {
                val id = account()
                refreshJob?.cancel(); openJob?.cancel(); searchJob?.cancel(); olderJob?.cancel()
                if (_state.value.userId != id) {
                    pageGeneration++; storyOffset = 0; retrySend = null
                    _state.value = FriendsSocialState(userId = id)
                }
                if (id != null) {
                    refresh()
                    if (navigation.owner.value == id && _state.value.activeConversation == null) {
                        navigation.saved.get<String>("chat_peer")?.let(::openPeer)
                    }
                    while (isActive && account() == id) {
                        delay(5000)
                        if (_state.value.backendUnavailable) continue
                        try {
                            val inbox = repository.inbox()
                            if (!sameUser(id)) continue
                            _state.update { s -> s.copy(conversations = inbox,
                                activeConversation = s.activeConversation?.let { c -> inbox.find { it.id == c.id } ?: c }) }
                            val active = _state.value.activeConversation
                            if (active != null) loadLatest(id, active.id)
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { if (sameUser(id)) _state.update { it.copy(chatError = if (it.activeConversation != null) "Koneksi terputus. Pesan akan dimuat kembali." else it.chatError) } }
                    }
                }
            }
        }
    }
    fun stop() { pollJob?.cancel(); pollJob = null; refreshJob?.cancel() }
    fun clearError() { _state.update { it.copy(error = null) } }
    fun refresh(publicFeed: Boolean = _state.value.publicFeed) {
        val id = account() ?: return
        refreshJob?.cancel()
        val generation = ++pageGeneration
        _state.update { it.copy(userId = id, loading = true, error = null, backendUnavailable = false,
            publicFeed = false, stories = emptyList(), moreStories = false) }
        refreshJob = viewModelScope.launch {
            try {
                val inbox = repository.inbox()
                if (sameUser(id) && generation == pageGeneration) {
                    _state.update { it.copy(conversations = inbox) }
                }
            } catch (e: CancellationException) { throw e }
            catch (failure: Exception) { if (sameUser(id)) _state.update { it.copy(
                backendUnavailable = friendsBackendMissing(failure), error = friendsFailureMessage(failure, "Pesan belum dapat dimuat. Coba lagi.")) } }
            finally { if (sameUser(id) && generation == pageGeneration) _state.update { it.copy(loading = false) } }
        }
    }
    fun moreStories() = Unit // Compatibility with callers from the removed story UI.
    fun publish(draft: MusicStoryDraft, onSuccess: () -> Unit) {
        val id = account() ?: return
        mutation { repository.publishStory(draft); if (sameUser(id)) { onSuccess(); refresh() } }
    }
    fun deleteStory(story: MusicStory, onSuccess: () -> Unit) {
        val id = account() ?: return
        mutation { repository.deleteStory(story.id); if (sameUser(id)) { onSuccess(); refresh() } }
    }
    fun searchPeople(query: String) {
        searchJob?.cancel()
        _state.update { it.copy(people = emptyList(), searching = query.trim().length >= 2) }
        if (query.trim().length < 2) return
        val id = account() ?: return
        searchJob = viewModelScope.launch {
            try {
                delay(350)
                val people = users.searchUsers(query.trim()).first()
                if (sameUser(id)) _state.update { it.copy(people = people.filter { p -> p.id != id }) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (sameUser(id)) _state.update { it.copy(error = "Pengguna belum dapat dicari.") } }
            finally { if (sameUser(id)) _state.update { it.copy(searching = false) } }
        }
    }
    fun searchTracks(query: String) {
        searchJob?.cancel()
        _state.update { it.copy(tracks = emptyList(), searching = query.trim().length >= 2) }
        if (query.trim().length < 2) return
        val id = account() ?: return
        searchJob = viewModelScope.launch {
            try {
                delay(400)
                val result = search.getSearchDataSong(query.trim()).first()
                if (result.data == null) error("Search unavailable")
                if (sameUser(id)) _state.update { it.copy(tracks = result.data.orEmpty().mapNotNull { s ->
                    val title = s.title?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                    StoryTrack(s.videoId, title, s.artists?.joinToString(", ") { a -> a.name.orEmpty() }.orEmpty(),
                        s.thumbnails?.lastOrNull()?.url, (s.durationSeconds ?: 0) * 1000L)
                }) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (sameUser(id)) _state.update { it.copy(error = "Lagu belum dapat dicari. Coba lagi.") } }
            finally { if (sameUser(id)) _state.update { it.copy(searching = false) } }
        }
    }
    suspend fun resolveClip(track: StoryTrack): String {
        // The normal streaming resolver supplies a fresh URL for each preview/viewer session.
        return withTimeout(30000) { streams.getStream(settings, track.videoId, false, false).first { !it.isNullOrBlank() }!! }
    }
    fun storyLyrics(track: StoryTrack): Flow<Lyrics> = flow {
        val preferred = settings.lyricsProvider.first()
        val spotifyAvailable = settings.spotifyLyrics.first() == DataStoreManager.TRUE && settings.spdc.first().isNotBlank()
        emitAll(storyLyricsFlow(preferred, spotifyAvailable,
            readCache = { lyrics.getSavedLyrics(track.videoId).firstOrNull()?.toLyrics() },
            fetch = { provider ->
                val duration = (track.durationMs / 1000).toInt().takeIf { it > 0 }
                when (provider) {
                    LyricsProvider.SIMPMUSIC -> lyrics.getSimpMusicLyrics(track.videoId).firstOrNull()?.data
                    LyricsProvider.BETTER_LYRICS -> lyrics.getBetterLyrics(track.artist, track.title, duration).firstOrNull()?.data
                    LyricsProvider.LRCLIB -> lyrics.getLrclibLyricsData(track.artist, track.title, duration).firstOrNull()?.data
                    LyricsProvider.GRATIFYMUSIC -> lyrics.getGratifyLyrics(track.videoId, track.title, track.artist, duration).firstOrNull()?.data
                    LyricsProvider.SPOTIFY -> lyrics.getSpotifyLyrics(settings, "${track.title} ${track.artist}", duration).firstOrNull()?.data
                    LyricsProvider.YOUTUBE -> lyrics.getYouTubeCaption(settings.youtubeSubtitleLanguage.first(), track.videoId).firstOrNull()?.data?.first
                    else -> null
                }
            }))
    }.flowOn(Dispatchers.IO)
    fun openPeer(peerId: String) {
        val id = account() ?: return
        navigation.showChat(peerId)
        openJob?.cancel(); olderJob?.cancel()
        _state.update { it.copy(browsingOlderMessages = false, opening = true, error = null, activeConversation = null, messages = emptyList(), loadingOlder = false, moreMessages = false, chatError = null) }
        openJob = viewModelScope.launch {
            try {
                val conversationId = repository.openConversation(peerId)
                val inbox = repository.inbox()
                if (!sameUser(id)) return@launch
                val conversation = inbox.first { it.id == conversationId }
                retrySend = null
                _state.update { it.copy(activeConversation = conversation, conversations = inbox, chatLoading = true) }
                loadLatest(id, conversationId, initial = true)
            } catch (e: CancellationException) { throw e }
            catch (failure: Exception) { if (sameUser(id)) _state.update { it.copy(error = friendsFailureMessage(failure, "Percakapan belum dapat dibuka.")) } }
            finally { if (sameUser(id)) _state.update { it.copy(opening = false) } }
        }
    }
    fun closeChat() {
        navigation.closeChat()
        openJob?.cancel(); olderJob?.cancel(); retrySend = null
        _state.update { it.copy(activeConversation = null, messages = emptyList(), chatError = null, moreMessages = false, opening = false, chatLoading = false) }
    }
    private suspend fun loadLatest(id: String, conversationId: String, initial: Boolean = false) {
        try {
            val messages = repository.messages(conversationId)
            if (!sameUser(id) || _state.value.activeConversation?.id != conversationId) return
            _state.update {
                it.copy(messages = if (initial) mergeChatMessages(emptyList(), messages) else
                    mergeChatMessages(it.messages, messages, preserveOlder = it.browsingOlderMessages),
                    browsingOlderMessages = if (initial) false else it.browsingOlderMessages,
                    moreMessages = if (initial) messages.size == 50 else it.moreMessages || it.messages.size >= CHAT_MESSAGE_WINDOW,
                    chatError = null)
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { if (sameUser(id) && _state.value.activeConversation?.id == conversationId) _state.update { it.copy(chatError = "Pesan belum dapat dimuat. Coba lagi.") } }
        finally { if (sameUser(id) && _state.value.activeConversation?.id == conversationId) _state.update { it.copy(chatLoading = false) } }
    }
    fun loadOlder() {
        val id = account() ?: return
        val s = _state.value
        val c = s.activeConversation ?: return
        val before = s.messages.firstOrNull()?.id ?: return
        if (!s.moreMessages || s.loadingOlder || s.chatLoading) return
        _state.update { it.copy(loadingOlder = true, browsingOlderMessages = true) }
        olderJob = viewModelScope.launch {
            try {
                val page = repository.messages(c.id, before)
                if (sameUser(id) && _state.value.activeConversation?.id == c.id) _state.update {
                    it.copy(messages = mergeOlderChatMessages(page, it.messages), moreMessages = page.size == 50)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (sameUser(id) && _state.value.activeConversation?.id == c.id) _state.update { it.copy(chatError = "Pesan sebelumnya belum dapat dimuat.") } }
            finally { if (sameUser(id) && _state.value.activeConversation?.id == c.id) _state.update { it.copy(loadingOlder = false) } }
        }
    }
    fun showLatestMessages() {
        val id = account() ?: return
        val conversation = _state.value.activeConversation ?: return
        olderJob?.cancel(); openJob?.cancel()
        _state.update { it.copy(chatLoading = true, loadingOlder = false) }
        openJob = viewModelScope.launch { loadLatest(id, conversation.id, initial = true) }
    }

    fun readVisible(message: DirectMessage) {
        val id = account() ?: return
        viewModelScope.launch {
            try { if (sameUser(id)) repository.markRead(message.conversationId, message.id) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Receipt retries after the next visible-page update. */ }
        }
    }
    fun send(text: String, onSuccess: () -> Unit) {
        val id = account() ?: return
        val c = _state.value.activeConversation ?: return
        val body = text.trim()
        if (body.isEmpty() || body.length > 2000 || _state.value.busy) return
        // Preserve the ID after a lost response, so retrying cannot create a duplicate message.
        val retry = retrySend?.takeIf { it.first == c.id && it.second == body }
            ?: Triple(c.id, body, UUID.randomUUID().toString()).also { retrySend = it }
        mutation(chat = true) {
            val message = repository.sendMessage(c.id, body, retry.third)
            if (sameUser(id) && _state.value.activeConversation?.id == c.id) {
                olderJob?.cancel()
                if (_state.value.browsingOlderMessages) loadLatest(id, c.id, initial = true)
                if (!sameUser(id) || _state.value.activeConversation?.id != c.id) return@mutation
                _state.update { it.copy(messages = mergeChatMessages(it.messages, listOf(message)), browsingOlderMessages = false, loadingOlder = false) }
                retrySend = null; onSuccess()
            }
        }
    }
    suspend fun completeTrack(track: StoryTrack): StoryTrack {
        if (track.durationMs > 0) return track
        val metadata = withTimeout(30000) { streams.getFullMetadata(track.videoId).first() }.data
            ?: error("Track metadata unavailable")
        return track.copy(durationMs = (metadata.durationSeconds ?: 0) * 1000L).also { require(it.durationMs >= 8000) }
    }
    fun respond(accept: Boolean) {
        val id = account() ?: return
        val c = _state.value.activeConversation ?: return
        mutation(chat = true) {
            repository.respondToRequest(c.id, accept)
            if (!sameUser(id) || _state.value.activeConversation?.id != c.id) return@mutation
            if (accept) _state.update { it.copy(activeConversation = it.activeConversation?.copy(accepted = true)) }
            else closeChat()
            refresh()
        }
    }
    private fun mutation(chat: Boolean = false, block: suspend () -> Unit) {
        val id = account() ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, chatError = null) }
        viewModelScope.launch {
            try { if (sameUser(id)) block() }
            catch (e: CancellationException) { throw e }
            catch (failure: Exception) { if (sameUser(id)) _state.update {
                if (chat) it.copy(chatError = friendsFailureMessage(failure, "Belum berhasil. Coba lagi; pesan tidak dikirim dua kali."))
                else it.copy(error = friendsFailureMessage(failure, "Belum berhasil disimpan. Periksa koneksi lalu coba lagi.")) } }
            finally { if (sameUser(id)) _state.update { it.copy(busy = false) } }
        }
    }
}

internal fun friendsBackendMissing(failure: Throwable): Boolean = generateSequence(failure) { it.cause }.any {
    val message = it.message.orEmpty()
    listOf("PGRST202", "PGRST205", "42P01", "Could not find the function", "Could not find the table").any(message::contains)
}
internal fun friendsFailureMessage(failure: Throwable, fallback: String) =
    if (friendsBackendMissing(failure)) "Story dan pesan belum aktif. Coba lagi setelah layanan diperbarui." else fallback
