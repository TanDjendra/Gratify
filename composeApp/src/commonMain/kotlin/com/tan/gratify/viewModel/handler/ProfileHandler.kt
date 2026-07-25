package com.tan.gratify.viewModel.handler

import com.tan.domain.data.entities.NowPlayingUpdate
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.mediaservice.handler.NowPlayingTrackState
import com.tan.domain.repository.UserRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class ProfileHandler(
    private val scope: CoroutineScope,
    private val dataStoreManager: DataStoreManager,
    private val userRepository: UserRepository,
    private val supabase: SupabaseClient,
) {
    fun syncNowPlayingToProfile(state: NowPlayingTrackState) {
        scope.launch(Dispatchers.IO) {
            try {
                val currentUserId = supabase.auth.currentUserOrNull()?.id ?: return@launch
                val rawVideoId = state.songEntity?.videoId
                    ?: state.track?.videoId
                    ?: state.mediaItem.mediaId
                val videoId = rawVideoId.removePrefix("Video").takeIf { it.isNotEmpty() && it != "EMPTY" }
                if (videoId.isNullOrBlank()) return@launch

                val title = state.songEntity?.title
                    ?: state.track?.title
                    ?: state.mediaItem.metadata?.title
                val artist = state.songEntity?.artistName?.joinToString(", ")
                    ?: state.track?.artists?.joinToString(", ") { it.name }
                    ?: state.mediaItem.metadata?.artist

                val localName = dataStoreManager.getString("AppProfileName").firstOrNull()?.takeIf { it.isNotBlank() } ?: "Pengguna Gratify"
                val localAvatar = dataStoreManager.getString("AppProfileImage").firstOrNull()
                val update = NowPlayingUpdate(
                    displayName = localName,
                    avatarUrl = localAvatar,
                    nowPlayingVideoId = videoId,
                    nowPlayingTitle = title,
                    nowPlayingArtist = artist,
                    lastActiveAt = Clock.System.now().toEpochMilliseconds().toString()
                )
                userRepository.updateNowPlaying(currentUserId, update).collectLatest {}
            } catch (_: Exception) {
            }
        }
    }

    fun clearNowPlayingFromProfile() {
        scope.launch(Dispatchers.IO) {
            try {
                val currentUserId = supabase.auth.currentUserOrNull()?.id ?: return@launch
                val localName = dataStoreManager.getString("AppProfileName").firstOrNull()?.takeIf { it.isNotBlank() } ?: "Pengguna Gratify"
                val localAvatar = dataStoreManager.getString("AppProfileImage").firstOrNull()
                val update = NowPlayingUpdate(
                    displayName = localName,
                    avatarUrl = localAvatar,
                    nowPlayingVideoId = null,
                    nowPlayingTitle = null,
                    nowPlayingArtist = null,
                    lastActiveAt = Clock.System.now().toEpochMilliseconds().toString()
                )
                userRepository.updateNowPlaying(currentUserId, update).collectLatest {}
            } catch (_: Exception) {
            }
        }
    }
}
