package com.tan.gratify.viewModel.handler

import com.tan.common.Config.ALBUM_CLICK
import com.tan.common.Config.PLAYLIST_CLICK
import com.tan.common.Config.RECOVER_TRACK_QUEUE
import com.tan.common.Config.SHARE
import com.tan.common.Config.SONG_CLICK
import com.tan.common.Config.VIDEO_CLICK
import com.tan.domain.data.entities.NewFormatEntity
import com.tan.domain.data.model.browse.album.Track
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.manager.DataStoreManager.Values.TRUE
import com.tan.domain.mediaservice.handler.MediaPlayerHandler
import com.tan.domain.mediaservice.handler.NowPlayingTrackState
import com.tan.domain.mediaservice.handler.PlayerEvent
import com.tan.domain.mediaservice.handler.PlaylistType
import com.tan.domain.mediaservice.handler.QueueData
import com.tan.domain.repository.SongRepository
import com.tan.domain.repository.StreamRepository
import com.tan.domain.extension.toGenericMediaItem
import com.tan.domain.utils.Resource
import com.tan.domain.utils.toSongEntity
import com.tan.domain.utils.toTrack
import com.tan.gratify.viewModel.NowPlayingScreenData
import com.tan.gratify.viewModel.UIEvent
import com.tan.logger.LogLevel
import com.tan.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.added_to_queue
import gratify.composeapp.generated.resources.added_to_youtube_liked
import gratify.composeapp.generated.resources.error
import gratify.composeapp.generated.resources.play_next
import gratify.composeapp.generated.resources.removed_from_youtube_liked
import gratify.composeapp.generated.resources.shared

class PlaybackHandler(
    private val scope: CoroutineScope,
    private val dataStoreManager: DataStoreManager,
    private val songRepository: SongRepository,
    private val streamRepository: StreamRepository,
    private val mediaPlayerHandler: MediaPlayerHandler,
    private val nowPlayingScreenData: MutableStateFlow<NowPlayingScreenData>,
    private val nowPlayingState: MutableStateFlow<NowPlayingTrackState?>,
    private val liked: MutableStateFlow<Boolean>,
    private val likeStatus: MutableStateFlow<Boolean>,
    private val format: MutableStateFlow<NewFormatEntity?>,
    private val makeToast: (String?) -> Unit,
    private val getString: (StringResource) -> String,
    private val clearNowPlayingFromProfile: () -> Unit,
) {
    private val tag = "PlaybackHandler"

    private var getFormatFlowJob: Job? = null
    private var songInfoJob: Job? = null

    fun loadSharedMediaItem(videoId: String) {
        scope.launch {
            val localSong = songRepository.getSongById(videoId).firstOrNull()
            if (localSong != null) {
                val track = localSong.toTrack()
                mediaPlayerHandler.setQueueData(
                    QueueData.Data(
                        listTracks = arrayListOf(track),
                        firstPlayedTrack = track,
                        playlistId = "RDAMVM$videoId",
                        playlistName = getString(Res.string.shared),
                        playlistType = PlaylistType.RADIO,
                        continuation = null,
                    ),
                )
                loadMediaItemFromTrack(track, SONG_CLICK)
            } else {
                streamRepository.getFullMetadata(videoId).collectLatest { response ->
                    val track = response.data
                    when (response) {
                        is Resource.Success if (track != null) -> {
                            mediaPlayerHandler.setQueueData(
                                QueueData.Data(
                                    listTracks = arrayListOf(track),
                                    firstPlayedTrack = track,
                                    playlistId = "RDAMVM$videoId",
                                    playlistName = getString(Res.string.shared),
                                    playlistType = PlaylistType.RADIO,
                                    continuation = null,
                                ),
                            )
                            loadMediaItemFromTrack(track, SONG_CLICK)
                        }

                        else -> {
                            log("Load shared media item error: ${response.message}", LogLevel.WARN)
                            makeToast("${getString(Res.string.error)}: ${response.message}")
                        }
                    }
                }
            }
        }
    }

    fun loadMediaItemFromTrack(
        track: Track,
        type: String,
        index: Int? = null,
    ) {
        scope.launch {
            Logger.d("PLAYER_DEBUG", "PlaybackHandler loadMediaItemFromTrack starts, videoId=${track.videoId}")
            val quality = dataStoreManager.quality.first()
            mediaPlayerHandler.clearMediaItems()
            songRepository.insertSong(track.toSongEntity()).lastOrNull()?.let {
                println("insertSong: $it")
                launch {
                    val songEntity = songRepository.getSongById(track.videoId).firstOrNull()
                    if (songEntity != null) {
                        Logger.w("Check like", "loadMediaItemFromTrack ${songEntity.liked}")
                        liked.value = songEntity.liked
                    }
                }
            }
            track.durationSeconds?.let {
                songRepository.updateDurationSeconds(
                    it,
                    track.videoId,
                )
            }
            withContext(Dispatchers.Main) {
                mediaPlayerHandler.addMediaItem(track.toGenericMediaItem(), playWhenReady = type != RECOVER_TRACK_QUEUE)
            }

            when (type) {
                SONG_CLICK -> {
                    mediaPlayerHandler.getRelated(track.videoId)
                }

                VIDEO_CLICK -> {
                    mediaPlayerHandler.getRelated(track.videoId)
                }

                SHARE -> {
                    mediaPlayerHandler.getRelated(track.videoId)
                }

                PLAYLIST_CLICK -> {
                    if (index == null) {
                        loadPlaylistOrAlbum(index = 0)
                    } else {
                        loadPlaylistOrAlbum(index = index)
                    }
                }

                ALBUM_CLICK -> {
                    if (index == null) {
                        loadPlaylistOrAlbum(index = 0)
                    } else {
                        loadPlaylistOrAlbum(index = index)
                    }
                }
            }
        }
    }

    fun onUIEvent(uiEvent: UIEvent) =
        scope.launch {
            when (uiEvent) {
                UIEvent.Backward -> {
                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.Backward)
                }

                UIEvent.Forward -> {
                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.Forward)
                }

                UIEvent.PlayPause -> {
                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.PlayPause)
                }

                UIEvent.Next -> {
                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.Next)
                }

                UIEvent.Previous -> {
                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.Previous)
                }

                UIEvent.SkipToPrevious -> {
                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.SkipToPrevious)
                }

                UIEvent.Stop -> {
                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.Stop)
                    clearNowPlayingFromProfile()
                }

                is UIEvent.UpdateProgress -> {
                    mediaPlayerHandler.onPlayerEvent(
                        PlayerEvent.UpdateProgress(uiEvent.newProgress),
                    )
                }

                UIEvent.Repeat -> {
                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.Repeat)
                }

                UIEvent.Shuffle -> {
                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.Shuffle)
                }

                UIEvent.ToggleLike -> {
                    Logger.w(tag, "ToggleLike")
                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.ToggleLike)
                }

                is UIEvent.UpdateVolume -> {
                    val newVolume = uiEvent.newVolume
                    dataStoreManager.setPlayerVolume(newVolume)
                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.UpdateVolume(newVolume))
                }
            }
        }

    fun addListToQueue(listTrack: ArrayList<Track>) {
        scope.launch {
            if (listTrack.size == 1 && dataStoreManager.endlessQueue.first() == TRUE) {
                mediaPlayerHandler.playNext(listTrack.first())
                makeToast(getString(Res.string.play_next))
            } else {
                mediaPlayerHandler.loadMoreCatalog(listTrack)
                makeToast(getString(Res.string.added_to_queue))
            }
        }
    }

    fun addToYouTubeLiked() {
        scope.launch {
            val videoId = mediaPlayerHandler.nowPlaying.first()?.mediaId
            if (videoId != null) {
                val like = likeStatus.value
                if (!like) {
                    songRepository
                        .addToYouTubeLiked(
                            mediaPlayerHandler.nowPlaying.first()?.mediaId,
                        ).collect { response ->
                            if (response == 200) {
                                makeToast(getString(Res.string.added_to_youtube_liked))
                                getLikeStatus(videoId)
                            } else {
                                makeToast(getString(Res.string.error))
                            }
                        }
                } else {
                    songRepository
                        .removeFromYouTubeLiked(
                            mediaPlayerHandler.nowPlaying.first()?.mediaId,
                        ).collect {
                            if (it == 200) {
                                makeToast(getString(Res.string.removed_from_youtube_liked))
                                getLikeStatus(videoId)
                            } else {
                                makeToast(getString(Res.string.error))
                            }
                        }
                }
            }
        }
    }

    fun getLikeStatus(videoId: String?) {
        scope.launch {
            if (videoId != null) {
                likeStatus.value = false
                songRepository.getLikeStatus(videoId).collectLatest { status ->
                    likeStatus.value = status
                }
            }
        }
    }

    fun getFormat(mediaId: String?) {
        if (mediaId != format.value?.videoId && !mediaId.isNullOrEmpty()) {
            format.value = null
            getFormatFlowJob?.cancel()
            getFormatFlowJob =
                scope.launch {
                    streamRepository.getFormatFlow(mediaId).cancellable().collectLatest { f ->
                        Logger.w(tag, "Get format for $mediaId: $f")
                        if (f != null) {
                            format.emit(f)
                        } else {
                            format.emit(null)
                        }
                    }
                }
        }
    }

    fun getSongInfo(mediaId: String?) {
        songInfoJob?.cancel()
        songInfoJob =
            scope.launch {
                if (mediaId != null) {
                    songRepository.getSongInfo(mediaId).collect { song ->
                        nowPlayingScreenData.update {
                            it.copy(
                                songInfoData = song,
                            )
                        }
                    }
                }
            }
    }

    private fun loadPlaylistOrAlbum(index: Int? = null) {
        mediaPlayerHandler.loadPlaylistOrAlbum(index)
    }

    fun stopPlayer() {
        nowPlayingScreenData.value = NowPlayingScreenData.initial()
        nowPlayingState.value = null
        mediaPlayerHandler.resetSongAndQueue()
        onUIEvent(UIEvent.Stop)
    }

    private fun log(
        message: String,
        logType: LogLevel = LogLevel.WARN,
    ) {
        when (logType) {
            LogLevel.DEBUG -> Logger.d(tag, message)
            LogLevel.INFO -> Logger.i(tag, message)
            LogLevel.WARN -> Logger.w(tag, message)
            LogLevel.ERROR -> Logger.e(tag, message)
        }
    }
}
