package com.tan.gratify.viewModel

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
import com.tan.domain.manager.DataStoreManager
import kotlinx.coroutines.flow.firstOrNull
import com.tan.domain.data.model.browse.album.Track

data class UserProfileState(
    val isLoading: Boolean = false,
    val profile: UserProfile? = null,
    val isFollowing: Boolean = false,
    val isOwnProfile: Boolean = false,
    val publicPlaylists: List<SharedPlaylist> = emptyList(),
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

    fun loadProfile(userId: String) {
        viewModelScope.launch {
            val cachedFollowers = dataStoreManager.getString("FollowersCount_$userId").firstOrNull()?.toIntOrNull()
            val cachedFollowing = dataStoreManager.getString("FollowingCount_$userId").firstOrNull()?.toIntOrNull()
            
            val currentUserId = supabase.auth.currentUserOrNull()?.id
            val isOwn = currentUserId == userId

            _state.update {
                it.copy(
                    isLoading = true,
                    isOwnProfile = isOwn,
                    followersCount = cachedFollowers ?: it.followersCount,
                    followingCount = cachedFollowing ?: it.followingCount
                )
            }
            
            // 2. Ambil profil user secara paralel
            launch {
                userRepository.getUserProfile(userId).collectLatest { fetchedProfile ->
                    val profile = if (fetchedProfile != null) {
                        if (isOwn) {
                            fetchedProfile.displayName?.takeIf { it.isNotBlank() }?.let {
                                dataStoreManager.putString("AppProfileName", it)
                                dataStoreManager.putString("AccountName", it)
                            }
                            fetchedProfile.avatarUrl?.takeIf { it.isNotBlank() }?.let {
                                dataStoreManager.putString("AppProfileImage", it)
                            }
                        }
                        fetchedProfile
                    } else if (isOwn) {
                        val localName = dataStoreManager.getString("AppProfileName").firstOrNull()?.takeIf { it.isNotBlank() }
                            ?: dataStoreManager.getString("AccountName").firstOrNull()?.takeIf { it.isNotBlank() }
                            ?: "Pengguna Gratify"
                        val localAvatar = dataStoreManager.getString("AppProfileImage").firstOrNull()?.takeIf { it.isNotBlank() }
                        UserProfile(
                            id = userId,
                            displayName = localName,
                            avatarUrl = localAvatar
                        )
                    } else {
                        UserProfile(
                            id = userId,
                            displayName = "Teman Gratify",
                            avatarUrl = null
                        )
                    }
                    
                    if (isOwn && currentUserId != null) {
                        launch {
                            userRepository.upsertUserProfile(profile).collectLatest {}
                        }
                    }
                    _state.update { it.copy(profile = profile) }
                }
            }

            // 3. Cek status isFollowing secara paralel
            if (currentUserId != null && currentUserId != userId) {
                launch {
                    userRepository.checkIsFollowing(currentUserId, userId).collectLatest { isFollowing ->
                        _state.update { it.copy(isFollowing = isFollowing) }
                    }
                }
            }
            
            // 4. Ambil data followers secara paralel dan simpan ke cache
            launch {
                userRepository.getFollowers(userId).collectLatest { followers ->
                    _state.update { it.copy(followersCount = followers.size) }
                    dataStoreManager.putString("FollowersCount_$userId", followers.size.toString())
                }
            }
            
            // 5. Ambil data following secara paralel dan simpan ke cache
            launch {
                userRepository.getFollowing(userId).collectLatest { following ->
                    _state.update { it.copy(followingCount = following.size) }
                    dataStoreManager.putString("FollowingCount_$userId", following.size.toString())
                }
            }
            
            // 6. Ambil playlist publik dan hindari duplikasi judul
            launch {
                sharedPlaylistRepository.getSharedPlaylists().collectLatest { result ->
                    val playlists = result.getOrNull() ?: emptyList()
                    val userPlaylists = playlists.filter { it.userId == userId }
                    val existingTitles = userPlaylists.map { it.title.trim().lowercase() }.toSet()
                    
                    socialRepository.getUserPublicPlaylists(userId).collectLatest { cloudRes ->
                        val cloudPlaylists = cloudRes.getOrNull() ?: emptyList()
                        val currentProfileName = _state.value.profile?.displayName ?: "User"
                        val mappedCloud = cloudPlaylists.mapNotNull { dto ->
                            if (dto.title.trim().lowercase() in existingTitles) null
                            else SharedPlaylist(
                                id = dto.id?.toString(),
                                userId = dto.userId ?: userId,
                                title = dto.title,
                                thumbnailUrl = dto.thumbnailUrl,
                                creatorName = currentProfileName
                            )
                        }
                        val combined = (userPlaylists + mappedCloud).distinctBy { it.title.trim().lowercase() }
                        _state.update { it.copy(publicPlaylists = combined, isLoading = false) }
                    }
                }
            }

            // 7. Cek aktivitas terakhir / artis yg diputar
            launch {
                userRepository.getUserProfile(userId).collectLatest { fetched ->
                    val npTitle = fetched?.nowPlayingTitle
                    val npVideoId = fetched?.nowPlayingVideoId
                    val npArtist = fetched?.nowPlayingArtist
                    val lastActiveMillis = fetched?.lastActiveAt?.toLongOrNull() ?: 0L
                    val nowMillis = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                    val isExpired = (nowMillis - lastActiveMillis) > 10 * 60 * 1000L

                    if (!npVideoId.isNullOrBlank() && !npTitle.isNullOrBlank() && !isExpired) {
                        _state.update {
                            it.copy(
                                recentActivityTitle = npTitle,
                                recentActivityArtist = npArtist ?: "-",
                                recentActivityVideoId = npVideoId,
                                isOnlinePlaying = true
                            )
                        }
                    } else {
                        try {
                            val playlistsRes = socialRepository.getUserPublicPlaylists(userId).firstOrNull()
                            val playlists = playlistsRes?.getOrNull() ?: emptyList()
                            val playlistId = playlists.firstOrNull()?.id
                            if (playlistId != null) {
                                val itemsRes = socialRepository.getCloudPlaylistItems(playlistId).firstOrNull()
                                val items = itemsRes?.getOrNull() ?: emptyList()
                                val latestItem = items.firstOrNull()
                                if (latestItem != null) {
                                    _state.update {
                                        it.copy(
                                            recentActivityTitle = latestItem.title,
                                            recentActivityArtist = latestItem.artist,
                                            recentActivityVideoId = latestItem.videoId,
                                            isOnlinePlaying = false
                                        )
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }
            }
        }
    }

    fun toggleFollow(userId: String) {
        viewModelScope.launch {
            val currentUserId = supabase.auth.currentUserOrNull()?.id ?: return@launch
            val isCurrentlyFollowing = _state.value.isFollowing
            val myCachedFollowing = dataStoreManager.getString("FollowingCount_$currentUserId").firstOrNull()?.toIntOrNull() ?: 0
            
            if (isCurrentlyFollowing) {
                val newFollowers = maxOf(0, _state.value.followersCount - 1)
                val newMyFollowing = maxOf(0, myCachedFollowing - 1)
                _state.update { it.copy(isFollowing = false, followersCount = newFollowers) }
                dataStoreManager.putString("FollowersCount_$userId", newFollowers.toString())
                dataStoreManager.putString("FollowingCount_$currentUserId", newMyFollowing.toString())

                userRepository.unfollowUser(currentUserId, userId).collectLatest { result ->
                    if (result.isSuccess) {
                        loadProfile(userId)
                    }
                }
            } else {
                val newFollowers = _state.value.followersCount + 1
                val newMyFollowing = myCachedFollowing + 1
                _state.update { it.copy(isFollowing = true, followersCount = newFollowers) }
                dataStoreManager.putString("FollowersCount_$userId", newFollowers.toString())
                dataStoreManager.putString("FollowingCount_$currentUserId", newMyFollowing.toString())

                userRepository.followUser(currentUserId, userId).collectLatest { result ->
                    if (result.isSuccess) {
                        loadProfile(userId)
                    }
                }
            }
        }
    }

    fun syncTopArtists(artists: List<com.tan.domain.data.entities.ArtistEntity>) {
        viewModelScope.launch {
            val currentUserId = supabase.auth.currentUserOrNull()?.id ?: return@launch
            val topArtists = artists.map {
                com.tan.domain.data.entities.TopArtistDto(
                    channelId = it.channelId,
                    name = it.name,
                    thumbnails = it.thumbnails
                )
            }
            userRepository.updateTopArtists(currentUserId, topArtists).collectLatest {}
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
        viewModelScope.launch {
            try {
                sharedPlaylistRepository.getSharedPlaylistTracks(playlistId).collectLatest { result ->
                    val tracksResult = result.getOrNull()
                    if (tracksResult != null) {
                        val tracks = tracksResult.map {
                            com.tan.domain.data.model.browse.album.Track(
                                album = null,
                                artists = listOf(com.tan.domain.data.model.searchResult.songs.Artist(name = it.artists ?: "", id = null)),
                                duration = null,
                                durationSeconds = it.durationSeconds ?: 0,
                                isAvailable = true,
                                isExplicit = false,
                                likeStatus = null,
                                thumbnails = null,
                                title = it.title,
                                videoId = it.videoId,
                                videoType = null,
                                category = null,
                                feedbackTokens = null,
                                resultType = null
                            )
                        }
                        val newLocalId = localPlaylistRepository.saveSharedPlaylistToLibrary(
                            sharedPlaylistId = playlistId,
                            title = title,
                            thumbnail = thumbnailUrl,
                            tracks = tracks,
                            creatorName = creatorName
                        )
                        if (newLocalId == -1L) {
                            kotlinx.coroutines.Dispatchers.Main.let {
                                onError("Playlist sudah tersimpan di Pustaka")
                            }
                            return@collectLatest
                        }
                        // Catat "save" agar hitungan "ditambahkan X kali" naik (unik per user).
                        val currentUserId = supabase.auth.currentUserOrNull()?.id
                        if (currentUserId != null) {
                            sharedPlaylistRepository.recordPlaylistSave(playlistId, currentUserId)
                        }
                        kotlinx.coroutines.Dispatchers.Main.let {
                            onSuccess()
                        }
                    } else {
                        kotlinx.coroutines.Dispatchers.Main.let {
                            onError("Gagal mengambil lagu.")
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.Dispatchers.Main.let {
                    onError("Terjadi kesalahan: ${e.message}")
                }
            }
        }
    }
}
