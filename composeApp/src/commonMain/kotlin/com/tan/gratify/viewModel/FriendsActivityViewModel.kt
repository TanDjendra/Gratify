package com.tan.gratify.viewModel

import androidx.lifecycle.viewModelScope
import com.tan.domain.data.entities.UserProfile
import com.tan.domain.repository.SocialRepository
import com.tan.domain.repository.UserRepository
import com.tan.gratify.viewModel.base.BaseViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

data class FriendActivityItem(
    val userProfile: UserProfile,
    val isOnline: Boolean,
    val songTitle: String,
    val artistName: String,
    val videoId: String,
    val lastActiveText: String
)

data class FriendsActivityState(
    val isLoading: Boolean = false,
    val currentUserId: String? = null,
    val friendsList: List<FriendActivityItem> = emptyList(),
    val myNote: String? = null,
    val myNoteUpdatedAt: String? = null,
    val error: String? = null
)

class FriendsActivityViewModel(
    private val userRepository: UserRepository,
    private val supabase: SupabaseClient,
    private val socialRepository: SocialRepository
) : BaseViewModel() {

    private val _state = MutableStateFlow(FriendsActivityState())
    val state: StateFlow<FriendsActivityState> = _state.asStateFlow()

    init {
        loadFriendsActivity()
    }

    fun loadFriendsActivity() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val currentUserId = supabase.auth.currentUserOrNull()?.id
                _state.update { it.copy(currentUserId = currentUserId) }

                if (currentUserId != null) {
                    // Muat catatan milik sendiri (ditampilkan & bisa diedit di atas daftar teman).
                    launch {
                        val myProfile = userRepository.getUserProfile(currentUserId).firstOrNull()
                        _state.update { it.copy(myNote = myProfile?.note, myNoteUpdatedAt = myProfile?.noteUpdatedAt) }
                    }
                    userRepository.getFollowing(currentUserId).collectLatest { followingList ->
                        userRepository.getFollowers(currentUserId).collectLatest { followersList ->
                            // Berteman = mutual follow (saling mengikuti)
                            val mutualFriends = followingList.filter { following ->
                                followersList.any { follower -> follower.id == following.id }
                            }

                            if (mutualFriends.isEmpty()) {
                                _state.update { it.copy(friendsList = emptyList(), isLoading = false) }
                            } else {
                                val mappedList = mutualFriends.map { friend ->
                                    val lastActiveMillis = friend.lastActiveAt?.toLongOrNull() ?: 0L
                                    val nowMillis = Clock.System.now().toEpochMilliseconds()
                                    val isExpired = (nowMillis - lastActiveMillis) > 10 * 60 * 1000L

                                    val npTitle = friend.nowPlayingTitle
                                    val npVideoId = friend.nowPlayingVideoId
                                    val isPlaying = !npVideoId.isNullOrBlank() && !npTitle.isNullOrBlank() && !isExpired

                                    var songTitle = "Belum memutar lagu"
                                    var artistName = "-"
                                    var videoId = ""

                                    if (isPlaying && npTitle != null && npVideoId != null) {
                                        songTitle = npTitle
                                        artistName = friend.nowPlayingArtist ?: "-"
                                        videoId = npVideoId
                                    } else {
                                        try {
                                            val playlistsRes = socialRepository.getUserPublicPlaylists(friend.id).firstOrNull()
                                            val playlists = playlistsRes?.getOrNull() ?: emptyList()
                                            val playlistId = playlists.firstOrNull()?.id
                                            if (playlistId != null) {
                                                val itemsRes = socialRepository.getCloudPlaylistItems(playlistId).firstOrNull()
                                                val items = itemsRes?.getOrNull() ?: emptyList()
                                                val latestItem = items.firstOrNull()
                                                if (latestItem != null) {
                                                    songTitle = latestItem.title
                                                    artistName = latestItem.artist
                                                    videoId = latestItem.videoId
                                                }
                                            }
                                        } catch (e: Exception) {
                                            // Abaikan error jaringan individual
                                        }
                                    }

                                    FriendActivityItem(
                                        userProfile = friend,
                                        isOnline = isPlaying,
                                        songTitle = songTitle,
                                        artistName = artistName,
                                        videoId = videoId,
                                        lastActiveText = if (isPlaying) "Sedang memutar" else "Offline"
                                    )
                                }
                                val sortedList = mappedList.sortedWith(
                                    compareByDescending<FriendActivityItem> { it.isOnline }
                                        .thenByDescending { it.userProfile.lastActiveAt?.toLongOrNull() ?: 0L }
                                )
                                _state.update { it.copy(friendsList = sortedList, isLoading = false) }
                            }
                        }
                    }
                } else {
                    _state.update { it.copy(friendsList = emptyList(), isLoading = false) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /** Simpan/ubah/hapus catatan ("Notes IG") milik user. Kirim teks kosong untuk menghapus. */
    fun updateMyNote(note: String) {
        viewModelScope.launch {
            val userId = supabase.auth.currentUserOrNull()?.id ?: return@launch
            val cleaned = note.trim().takeIf { it.isNotBlank() }
            val nowMillis = if (cleaned != null) Clock.System.now().toEpochMilliseconds().toString() else null
            _state.update { it.copy(myNote = cleaned, myNoteUpdatedAt = nowMillis) } // optimistic
            try {
                userRepository.updateNote(userId, cleaned).collectLatest {}
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
