package com.tan.gratify.viewModel

import com.tan.domain.utils.uploadedProfileAvatar

import androidx.lifecycle.viewModelScope
import com.tan.domain.data.entities.SharedPlaylist
import com.tan.domain.data.entities.UserProfile
import com.tan.domain.repository.SharedPlaylistRepository
import com.tan.domain.repository.SocialRepository
import com.tan.domain.repository.UserRepository
import com.tan.gratify.viewModel.base.BaseViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import com.tan.domain.manager.DataStoreManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import com.tan.domain.data.model.browse.album.Track

data class UserProfileState(
    val isLoading: Boolean = true,
    val requestedUserId: String? = null,
    val profileError: String? = null,
    val playlistLoading: Boolean = false,
    val countsLoading: Boolean = true,
    val countsError: String? = null,
    val followReady: Boolean = false,
    val followError: String? = null,
    val isFollowBusy: Boolean = false,
    val privacyReady: Boolean = false,
    val privacySaving: Boolean = false,
    val privacyError: String? = null,
    val savingPlaylist: Boolean = false,
    val profile: UserProfile? = null,
    val isFollowing: Boolean = false,
    val isOwnProfile: Boolean = false,
    val publicPlaylists: List<SharedPlaylist> = emptyList(),
    val playlistError: String? = null,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val recentActivityTitle: String? = null,
    val recentActivityArtist: String? = null,
    val recentActivityVideoId: String? = null,
    val isOnlinePlaying: Boolean = false,
)


class UserProfileViewModel(
    private val userRepository: UserRepository,
    private val sharedPlaylistRepository: SharedPlaylistRepository,
    private val supabase: SupabaseClient,
    private val socialRepository: SocialRepository,
    private val dataStoreManager: DataStoreManager,
) : BaseViewModel() {

    private val _state = MutableStateFlow(UserProfileState())
    val state: StateFlow<UserProfileState> = _state.asStateFlow()
    private var profileJob: kotlinx.coroutines.Job? = null

    private var profileGeneration = 0
    private var artistsJob: kotlinx.coroutines.Job? = null
    private var playlistSavePending = false

    fun clearProfile() {
        profileJob?.cancel()
        profileGeneration++
        _state.value = UserProfileState(isLoading = false, countsLoading = false)
    }

    suspend fun setProfilePrivacy(localKey: String, visible: Boolean) {
        if (_state.value.privacySaving || !_state.value.privacyReady || !_state.value.isOwnProfile) return
        val setting = when (localKey) {
            "privacy_show_followers" -> "show_followers"
            "privacy_show_playlist" -> "show_playlists"
            "privacy_show_recent_artists" -> "show_recent_artists"
            else -> return
        }
        val owner = supabase.auth.currentUserOrNull()?.id ?: return
        val generation = profileGeneration
        _state.update { it.copy(privacySaving = true, privacyError = null) }
        try {
            val result = withTimeout(20_000) { userRepository.setProfilePrivacy(setting, visible) }
            if (supabase.auth.currentUserOrNull()?.id != owner || generation != profileGeneration) return
            if (result.isSuccess) {
                _state.update { state -> state.copy(profile = state.profile?.let { profile -> when (setting) {
                    "show_followers" -> profile.copy(showFollowers = visible)
                    "show_playlists" -> profile.copy(showPlaylists = visible)
                    else -> profile.copy(showRecentArtists = visible)
                } }) }
                try { dataStoreManager.putString(localKey, if (visible) "TRUE" else "FALSE") }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { }
            } else _state.update { it.copy(privacyError = "Privasi belum tersimpan. Periksa koneksi dan coba lagi.") }
        } catch (cancelled: CancellationException) {
            if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
            if (generation == profileGeneration) _state.update { it.copy(privacyError = "Privasi belum tersimpan. Coba lagi.") }
        } catch (_: Exception) {
            if (generation == profileGeneration) _state.update { it.copy(privacyError = "Privasi belum tersimpan. Coba lagi.") }
        }
        finally { if (generation == profileGeneration) _state.update { it.copy(privacySaving = false) } }
    }

    private data class SavedIdentity(val name: String, val avatar: String?)

    fun onProfileSaved(owner: String, name: String, avatar: String) {
        if (supabase.auth.currentUserOrNull()?.id != owner) return
        loadProfile(owner, SavedIdentity(name, avatar.uploadedProfileAvatar()))
    }

    fun loadProfile(userId: String) = loadProfile(userId, null)

    private fun loadProfile(userId: String, saved: SavedIdentity?) {
        profileJob?.cancel()
        val generation = ++profileGeneration
        val viewer = supabase.auth.currentUserOrNull()?.id
        val own = viewer == userId
        fun active() = generation == profileGeneration && supabase.auth.currentUserOrNull()?.id == viewer
        val confirmed = saved?.let { identity ->
            (_state.value.profile?.takeIf { it.id == userId } ?: UserProfile(userId)).copy(
                displayName = identity.name, avatarUrl = identity.avatar)
        }
        // A successful edit is already authoritative; reloading must not flash the old cached avatar.
        _state.value = UserProfileState(isLoading = true, requestedUserId = userId, isOwnProfile = own,
            savingPlaylist = playlistSavePending, profile = confirmed)
        profileJob = viewModelScope.launch {
            val profile = try {
                withTimeout(20_000) { userRepository.fetchUserProfile(userId).first().getOrThrow() }?.let { loaded ->
                    if (saved == null) loaded else loaded.copy(displayName = saved.name, avatarUrl = saved.avatar)
                } ?: confirmed
            } catch (cancelled: CancellationException) {
                if (cancelled is kotlinx.coroutines.TimeoutCancellationException && active()) {
                    _state.update { it.copy(isLoading = false, profileError = "Profil belum dapat dimuat. Coba lagi.") }
                    return@launch
                }
                throw cancelled
            } catch (_: Exception) {
                if (active()) _state.update { it.copy(isLoading = false, profileError = "Profil belum dapat dimuat. Periksa koneksi dan coba lagi.") }
                return@launch
            }
            if (!active()) return@launch
            if (profile == null) {
                _state.update { it.copy(isLoading = false, countsLoading = false) }
                return@launch
            }
            val age = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - (profile.lastActiveAt?.toLongOrNull() ?: 0)
            val playing = (own || profile.showRecentArtists) && !profile.nowPlayingVideoId.isNullOrBlank() &&
                !profile.nowPlayingTitle.isNullOrBlank() && age in 0..600_000
            _state.update { it.copy(profile = profile, isLoading = false, playlistLoading = true,
                recentActivityTitle = profile.nowPlayingTitle.takeIf { playing },
                recentActivityArtist = profile.nowPlayingArtist.takeIf { playing },
                recentActivityVideoId = profile.nowPlayingVideoId.takeIf { playing }, isOnlinePlaying = playing,
                followReady = own || viewer == null) }
            // Reading never upserts a snapshot back to the server.
            if (own) {
                profile.displayName?.takeIf(String::isNotBlank)?.let {
                    dataStoreManager.putString("AppProfileName", it)
                    dataStoreManager.putString("AccountName", it)
                }
                if (!active()) return@launch
                dataStoreManager.putString("AppProfileImage", profile.avatarUrl.uploadedProfileAvatar().orEmpty())
                for ((key, visible) in listOf("privacy_show_followers" to profile.showFollowers,
                    "privacy_show_playlist" to profile.showPlaylists, "privacy_show_recent_artists" to profile.showRecentArtists)) {
                    if (!active()) return@launch
                    dataStoreManager.putString(key, if (visible) "TRUE" else "FALSE")
                }
                if (active()) _state.update { it.copy(privacyReady = true) }
            }
            if (viewer != null && !own) launch {
                try {
                    val follows = withTimeout(20_000) { userRepository.fetchIsFollowing(viewer, userId).first().getOrThrow() }
                    if (active()) _state.update { it.copy(isFollowing = follows, followReady = true) }
                } catch (cancelled: CancellationException) { if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
                    if (active()) _state.update { it.copy(followError = "Status mengikuti belum dapat dimuat.") } }
                catch (_: Exception) { if (active()) _state.update { it.copy(followError = "Status mengikuti belum dapat dimuat.") } }
            }
            if (own || profile.showFollowers) launch {
                try {
                    val counts = withTimeout(20_000) { userRepository.getVisibleFollowCounts(userId).first().getOrThrow() }
                    if (active()) _state.update { it.copy(followersCount = counts.followers, followingCount = counts.following, countsLoading = false) }
                } catch (cancelled: CancellationException) { if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
                    if (active()) _state.update { it.copy(countsLoading = false, countsError = "Jumlah akun belum dapat dimuat.") } }
                catch (_: Exception) { if (active()) _state.update { it.copy(countsLoading = false, countsError = "Jumlah akun belum dapat dimuat.") } }
            } else _state.update { it.copy(countsLoading = false) }
            if (own || profile.showPlaylists) launch {
                try {
                    val playlists = withTimeout(20_000) { socialRepository.getUserProfilePlaylists(userId).first().getOrThrow() }
                    val displayed = playlists.filter { own || it.isPublic }.map { dto ->
                        val id = dto.sharedPlaylistId
                        SharedPlaylist(id = id, userId = dto.userId ?: userId, title = dto.title,
                            thumbnailUrl = dto.thumbnailUrl, creatorName = profile.displayName, clientSyncId = dto.clientSyncId, addCount = dto.addCount, isPublic = dto.isPublic)
                    }
                    if (active()) _state.update { it.copy(publicPlaylists = displayed, playlistLoading = false) }
                } catch (cancelled: CancellationException) { if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
                    if (active()) _state.update { it.copy(playlistLoading = false, playlistError = "Playlist belum dapat dimuat. Coba lagi.") } }
                catch (_: Exception) { if (active()) _state.update { it.copy(playlistLoading = false, playlistError = "Playlist belum dapat dimuat. Coba lagi.") } }
            } else _state.update { it.copy(playlistLoading = false) }
        }
    }

    fun toggleFollow(userId: String, onLoginRequired: () -> Unit = { makeToast("Masuk untuk mengikuti akun.") }) {
        if (_state.value.isFollowBusy) return
        val viewer = supabase.auth.currentUserOrNull()?.id ?: return onLoginRequired()
        if (_state.value.requestedUserId != userId || !_state.value.followReady || viewer == userId) return
        val generation = profileGeneration
        _state.update { it.copy(isFollowBusy = true, followError = null) }
        viewModelScope.launch {
            try {
                val following = _state.value.isFollowing
                withTimeout(20_000) {
                    (if (following) userRepository.unfollowUser(viewer, userId) else userRepository.followUser(viewer, userId)).first().getOrThrow()
                }
                if (supabase.auth.currentUserOrNull()?.id == viewer && generation == profileGeneration) loadProfile(userId)
            } catch (cancelled: CancellationException) { if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
                if (generation == profileGeneration) _state.update { it.copy(followError = "Perubahan belum tersimpan. Coba lagi.") } }
            catch (_: Exception) { if (generation == profileGeneration) _state.update { it.copy(followError = "Perubahan belum tersimpan. Periksa koneksi dan coba lagi.") } }
            finally { if (generation == profileGeneration) _state.update { it.copy(isFollowBusy = false) } }
        }
    }

    fun syncTopArtists(artists: List<com.tan.domain.data.entities.ArtistEntity>) {
        artistsJob?.cancel()
        val currentUserId = supabase.auth.currentUserOrNull()?.id ?: return
        artistsJob = viewModelScope.launch {
            val topArtists = artists.map {
                com.tan.domain.data.entities.TopArtistDto(
                    channelId = it.channelId,
                    name = it.name,
                    thumbnails = it.thumbnails
                )
            }
            try {
                if (dataStoreManager.getString("last_synced_user_id").firstOrNull() != currentUserId) return@launch
                if (supabase.auth.currentUserOrNull()?.id == currentUserId)
                    withTimeout(20_000) { userRepository.updateTopArtists(currentUserId, topArtists).first() }
            } catch (cancelled: CancellationException) { if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled }
            catch (_: Exception) { }
        }
    }

    fun addSharedPlaylistToLibrary(
        playlistId: String,
        title: String,
        thumbnailUrl: String?,
        localPlaylistRepository: com.tan.domain.repository.LocalPlaylistRepository,
        creatorName: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (playlistSavePending) return
        playlistSavePending = true
        _state.update { it.copy(savingPlaylist = true) }
        viewModelScope.launch {
            try {
                val tracksResult = withTimeout(20_000) {
                    sharedPlaylistRepository.getSharedPlaylistTracks(playlistId).first().getOrThrow()
                }
                val tracks = tracksResult.map {
                    Track(album = null,
                        artists = listOf(com.tan.domain.data.model.searchResult.songs.Artist(name = it.artists.orEmpty(), id = null)),
                        duration = null, durationSeconds = it.durationSeconds ?: 0, isAvailable = true,
                        isExplicit = false, likeStatus = null, thumbnails = null, title = it.title,
                        videoId = it.videoId, videoType = null, category = null, feedbackTokens = null, resultType = null)
                }
                val savedId = localPlaylistRepository.saveSharedPlaylistToLibrary(
                    sharedPlaylistId = playlistId, title = title, thumbnail = thumbnailUrl,
                    tracks = tracks, creatorName = creatorName)
                if (savedId == -1L) {
                    onError("Playlist sudah tersimpan di Pustaka.")
                } else {
                    onSuccess()
                    makeToast("Playlist tersimpan di Pustaka.")
                    sharedPlaylistRepository.getSharedPlaylist(playlistId).getOrNull()?.let { updated ->
                        _state.update { state -> state.copy(publicPlaylists = state.publicPlaylists.map { if (it.id == playlistId) updated else it }) }
                    }
                }
            } catch (cancelled: CancellationException) {
                if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
                onError("Playlist belum dapat disimpan. Coba lagi.")
            } catch (_: Exception) {
                onError("Playlist belum dapat disimpan. Periksa koneksi dan coba lagi.")
            } finally {
                playlistSavePending = false
                _state.update { it.copy(savingPlaylist = false) }
            }
        }
    }
}
