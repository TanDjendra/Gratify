package com.tan.data.mediaservice

import com.tan.domain.data.entities.NowPlayingUpdate
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.mediaservice.handler.MediaPlayerHandler
import com.tan.domain.mediaservice.handler.NowPlayingTrackState
import com.tan.domain.repository.UserRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

object ProfileSyncManager {
    private var syncJob: kotlinx.coroutines.Job? = null
    fun startSync(
        mediaPlayerHandler: MediaPlayerHandler,
        userRepository: UserRepository,
        supabase: SupabaseClient,
        dataStoreManager: DataStoreManager,
        scope: CoroutineScope
    ) {
        syncJob?.cancel()
        val job = kotlinx.coroutines.SupervisorJob(scope.coroutineContext[kotlinx.coroutines.Job])
        syncJob = job
        val heartbeatScope = CoroutineScope(scope.coroutineContext + job)
        // Observe Now Playing track changes
        heartbeatScope.launch {
            mediaPlayerHandler.nowPlayingState
                .distinctUntilChangedBy {
                    val rawId = it.songEntity?.videoId ?: it.track?.videoId ?: it.mediaItem.mediaId
                    rawId.removePrefix("Video")
                }.collectLatest { state ->
                    if (mediaPlayerHandler.controlState.value.isPlaying) {
                        syncToProfile(state, userRepository, supabase, dataStoreManager)
                    } else {
                        delay(600)
                        if (mediaPlayerHandler.controlState.value.isPlaying) {
                            syncToProfile(mediaPlayerHandler.nowPlayingState.value, userRepository, supabase, dataStoreManager)
                        }
                    }
                }
        }

        // Observe Play/Pause state changes
        heartbeatScope.launch {
            mediaPlayerHandler.controlState
                .map { it.isPlaying }
                .distinctUntilChanged()
                .collectLatest { isPlaying ->
                    if (isPlaying) {
                        syncToProfile(mediaPlayerHandler.nowPlayingState.value, userRepository, supabase, dataStoreManager)
                    } else {
                        delay(1500)
                        if (!mediaPlayerHandler.controlState.value.isPlaying) {
                            clearFromProfile(userRepository, supabase, dataStoreManager)
                        }
                    }
                }
        }

        // 2-minute Heartbeat loop while playing
        heartbeatScope.launch {
            while (isActive) {
                delay(120_000)
                if (mediaPlayerHandler.controlState.value.isPlaying) {
                    syncToProfile(mediaPlayerHandler.nowPlayingState.value, userRepository, supabase, dataStoreManager)
                }
            }
        }
    }

    private suspend fun syncToProfile(
        state: NowPlayingTrackState,
        userRepository: UserRepository,
        supabase: SupabaseClient,
        dataStoreManager: DataStoreManager
    ) {
        try {
            val currentUserId = supabase.auth.currentUserOrNull()?.id ?: return
            if (dataStoreManager.getString("last_synced_user_id").firstOrNull() != currentUserId) return
            val rawVideoId = state.songEntity?.videoId
                ?: state.track?.videoId
                ?: state.mediaItem.mediaId
            val videoId = rawVideoId.removePrefix("Video").takeIf { it.isNotEmpty() && it != "EMPTY" }
            if (videoId.isNullOrBlank()) return

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
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // Silent catch
        }
    }

    private suspend fun clearFromProfile(
        userRepository: UserRepository,
        supabase: SupabaseClient,
        dataStoreManager: DataStoreManager
    ) {
        try {
            val currentUserId = supabase.auth.currentUserOrNull()?.id ?: return
            if (dataStoreManager.getString("last_synced_user_id").firstOrNull() != currentUserId) return
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
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // Silent catch
        }
    }
}
