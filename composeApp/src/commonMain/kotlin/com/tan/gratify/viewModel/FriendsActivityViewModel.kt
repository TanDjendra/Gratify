package com.tan.gratify.viewModel

import androidx.lifecycle.viewModelScope
import com.tan.domain.data.entities.UserProfile
import com.tan.domain.repository.SocialRepository
import com.tan.domain.repository.UserRepository
import com.tan.gratify.viewModel.base.BaseViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock

data class FriendActivityItem(
    val userProfile: UserProfile,
    val isOnline: Boolean,
    val songTitle: String,
    val artistName: String,
    val videoId: String,
    val lastActiveText: String,
    val artworkUrl: String? = null,
)

data class FriendsActivityState(
    val isLoading: Boolean = false,
    val currentUserId: String? = null,
    val friendsList: List<FriendActivityItem> = emptyList(),
    val myNote: String? = null,
    val myNoteUpdatedAt: String? = null,
    val error: String? = null,
    val noteSaving: Boolean = false,
    val noteError: String? = null,
)

/** Only shared, fresh playback is activity; a public playlist is not listening history. */
internal fun friendActivityAt(profile: UserProfile, now: Long): FriendActivityItem {
    val age = profile.lastActiveAt?.toLongOrNull()?.let { now - it }
    val playing = profile.showRecentArtists && age != null && age in 0..600_000 &&
        !profile.nowPlayingVideoId.isNullOrBlank() && !profile.nowPlayingTitle.isNullOrBlank()
    return FriendActivityItem(profile, playing,
        profile.nowPlayingTitle.takeIf { playing }.orEmpty(), profile.nowPlayingArtist.takeIf { playing }.orEmpty(),
        profile.nowPlayingVideoId.takeIf { playing }.orEmpty(),
        if (playing) "Sedang mendengarkan" else "Belum ada aktivitas terbaru")
}

class FriendsActivityViewModel(
    private val userRepository: UserRepository,
    private val supabase: SupabaseClient,
    @Suppress("UNUSED_PARAMETER") socialRepository: SocialRepository,
) : BaseViewModel() {
    private val _state = MutableStateFlow(FriendsActivityState())
    val state: StateFlow<FriendsActivityState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var pollJob: Job? = null
    private var noteJob: Job? = null
    private var noteRevision = 0L
    private fun account() = supabase.auth.currentUserOrNull()?.id
    private fun sameAccount(id: String) = account() == id && _state.value.currentUserId == id

    fun start() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            supabase.auth.sessionStatus.collectLatest {
                loadJob?.cancel()
                val id = account()
                if (_state.value.currentUserId != id) { noteJob?.cancel(); _state.value = FriendsActivityState(currentUserId = id) }
                do {
                    loadFriendsActivity()
                    delay(60_000)
                } while (isActive && id != null && account() == id)
            }
        }
    }
    fun stop() { pollJob?.cancel(); pollJob = null; loadJob?.cancel() }

    private suspend fun allProfiles(fetch: (Int) -> Flow<Result<List<UserProfile>>>): List<UserProfile> {
        val profiles = mutableListOf<UserProfile>()
        var offset = 0
        while (true) {
            val page = fetch(offset).first().getOrThrow()
            profiles.addAll(page)
            if (page.size < 40) return profiles.distinctBy(UserProfile::id)
            offset += page.size
        }
    }

    fun loadFriendsActivity() {
        loadJob?.cancel()
        val id = account()
        if (_state.value.currentUserId != id) { noteJob?.cancel(); _state.value = FriendsActivityState(currentUserId = id) }
        if (id == null) { _state.value = FriendsActivityState(); return }
        _state.update { it.copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                withTimeout(20_000) {
                    val following = async { allProfiles { userRepository.fetchFollowing(id, it, 40) } }
                    val followers = async { allProfiles { userRepository.fetchFollowers(id, it, 40) }.map(UserProfile::id).toSet() }
                    val mutualIds = followers.await()
                    val now = Clock.System.now().toEpochMilliseconds()
                    val friends = following.await().filter { it.id != id && it.id in mutualIds }
                        .map { friendActivityAt(it, now) }
                        .sortedWith(compareByDescending<FriendActivityItem> { it.isOnline }
                            .thenBy { it.userProfile.displayName.orEmpty().lowercase() })
                    if (sameAccount(id)) _state.update { it.copy(friendsList = friends, isLoading = false) }
                    // A profile read failing must not erase an otherwise loaded friend list.
                    val readRevision = noteRevision
                    val ownProfile = runCatching { userRepository.fetchUserProfile(id).first().getOrThrow() }
                        .getOrElse { if (it is CancellationException) throw it; null }
                    if (ownProfile?.id == id && sameAccount(id) && !_state.value.noteSaving && readRevision == noteRevision) _state.update {
                        it.copy(myNote = ownProfile.note, myNoteUpdatedAt = ownProfile.noteUpdatedAt)
                    }
                }
            } catch (cancelled: CancellationException) {
                if (cancelled !is TimeoutCancellationException) throw cancelled
                if (sameAccount(id)) _state.update { it.copy(isLoading = false, error = "Aktivitas belum dapat dimuat. Coba lagi.") }
            } catch (_: Exception) {
                if (sameAccount(id)) _state.update { it.copy(isLoading = false, error = "Aktivitas belum dapat dimuat. Coba lagi.") }
            }
        }
    }

    fun clearNoteError() { _state.update { it.copy(noteError = null) } }

    fun updateMyNote(note: String, onSuccess: () -> Unit = {}) {
        val id = account() ?: return
        if (!sameAccount(id) || _state.value.noteSaving) return
        val cleaned = note.trim().take(100).takeIf(String::isNotBlank)
        val saveRevision = ++noteRevision
        _state.update { it.copy(noteSaving = true, noteError = null) }
        noteJob = viewModelScope.launch {
            try {
                withTimeout(15_000) { userRepository.updateNote(id, cleaned).first().getOrThrow() }
                if (sameAccount(id) && saveRevision == noteRevision) {
                    _state.update { it.copy(myNote = cleaned,
                        myNoteUpdatedAt = cleaned?.let { Clock.System.now().toEpochMilliseconds().toString() }, noteSaving = false) }
                    onSuccess()
                }
            } catch (cancelled: CancellationException) {
                if (cancelled !is TimeoutCancellationException) throw cancelled
                if (sameAccount(id) && saveRevision == noteRevision) _state.update { it.copy(noteError = "Catatan belum tersimpan. Coba lagi.") }
            } catch (_: Exception) {
                if (sameAccount(id) && saveRevision == noteRevision) _state.update { it.copy(noteError = "Catatan belum tersimpan. Coba lagi.") }
            } finally { if (sameAccount(id) && saveRevision == noteRevision) _state.update { it.copy(noteSaving = false) } }
        }
    }
}
