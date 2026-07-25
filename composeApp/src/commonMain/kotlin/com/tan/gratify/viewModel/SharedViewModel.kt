package com.tan.gratify.viewModel

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.viewModelScope
import com.tan.common.LibraryChipType
import com.tan.common.SELECTED_LANGUAGE
import com.tan.common.STATUS_DONE
import com.tan.domain.data.entities.LyricsEntity
import com.tan.domain.data.entities.NewFormatEntity
import com.tan.domain.repository.UserRepository
import io.github.jan.supabase.SupabaseClient
import org.koin.core.component.inject
import com.tan.domain.data.model.browse.album.Track
import com.tan.domain.data.model.canvas.CanvasResult
import com.tan.domain.data.model.download.DownloadProgress
import com.tan.domain.data.model.intent.GenericIntent
import com.tan.domain.data.model.streams.TimeLine
import com.tan.domain.data.model.update.UpdateData
import com.tan.domain.extension.isSong
import com.tan.domain.extension.isVideo
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.manager.DataStoreManager.Values.TRUE
import com.tan.domain.mediaservice.handler.ControlState
import com.tan.domain.mediaservice.handler.DownloadHandler
import com.tan.domain.mediaservice.handler.NowPlayingTrackState
import com.tan.domain.mediaservice.handler.RepeatState
import com.tan.domain.mediaservice.handler.SimpleMediaState
import com.tan.domain.mediaservice.handler.SleepTimerState
import com.tan.domain.repository.AlbumRepository
import com.tan.domain.repository.CacheRepository
import com.tan.domain.repository.LocalPlaylistRepository
import com.tan.domain.repository.LyricsCanvasRepository
import com.tan.domain.repository.PlaylistRepository
import com.tan.domain.repository.SongRepository
import com.tan.domain.repository.StreamRepository
import com.tan.domain.repository.UpdateRepository
import com.tan.logger.Logger
import com.tan.gratify.utils.VersionManager
import com.tan.gratify.viewModel.base.BaseViewModel
import com.tan.gratify.viewModel.handler.DownloadHandler as ViewModelDownloadHandler
import com.tan.gratify.viewModel.handler.CanvasHandler
import com.tan.gratify.viewModel.handler.LyricsHandler
import com.tan.gratify.viewModel.handler.PlaybackHandler
import com.tan.gratify.viewModel.handler.ProfileHandler
import com.tan.gratify.viewModel.handler.UpdateHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.MutableSharedFlow
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.delay
import com.tan.domain.data.entities.NotificationEntity
import com.tan.domain.repository.CommonRepository

@OptIn(ExperimentalCoroutinesApi::class)
class SharedViewModel(
    private val dataStoreManager: DataStoreManager,
    private val streamRepository: StreamRepository,
    private val updateRepository: UpdateRepository,
    private val songRepository: SongRepository,
    private val albumRepository: AlbumRepository,
    private val localPlaylistRepository: LocalPlaylistRepository,
    private val playlistRepository: PlaylistRepository,
    private val lyricsCanvasRepository: LyricsCanvasRepository,
    private val cacheRepository: CacheRepository,
    private val commonRepository: CommonRepository,
) : BaseViewModel() {
    private val supabase: SupabaseClient by inject()
    private val userRepository: UserRepository by inject()

    var isFirstLiked: Boolean = false
    var isFirstMiniplayer: Boolean = false
    var isFirstSuggestions: Boolean = false
    var showedUpdateDialog: Boolean
        get() = updateHandler.showedUpdateDialog
        set(value) { updateHandler.showedUpdateDialog = value }

    val isCheckingUpdate: StateFlow<Boolean> get() = updateHandler.isCheckingUpdate

    private var _liked: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val liked: SharedFlow<Boolean> = _liked.asSharedFlow()

    var isServiceRunning: Boolean = false

    private var _sleepTimerState = MutableStateFlow(SleepTimerState(false, 0))
    val sleepTimerState: StateFlow<SleepTimerState> = _sleepTimerState

    private var regionCode: String? = null
    private var language: String? = null
    private var quality: String? = null

    private var _format: MutableStateFlow<NewFormatEntity?> = MutableStateFlow(null)
    val format: SharedFlow<NewFormatEntity?> = _format.asSharedFlow()

    val canvas: StateFlow<CanvasResult?> get() = canvasHandler.canvas

    private val _intent: MutableStateFlow<GenericIntent?> = MutableStateFlow(null)
    val intent: StateFlow<GenericIntent?> = _intent

    private val _showNotificationPermissionDialog = MutableStateFlow(false)
    val showNotificationPermissionDialog: StateFlow<Boolean> = _showNotificationPermissionDialog

    var playlistId: MutableStateFlow<String?> = MutableStateFlow(null)

    var isFullScreen: Boolean = false

    private var _nowPlayingState = MutableStateFlow<NowPlayingTrackState?>(null)
    val nowPlayingState: StateFlow<NowPlayingTrackState?> = _nowPlayingState

    fun getQueueDataState() = mediaPlayerHandler.queueData

    val blurBg: StateFlow<Boolean> =
        dataStoreManager.blurPlayerBackground
            .map { it == TRUE }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(500L),
                initialValue = false,
            )

    private var _controllerState =
        MutableStateFlow<ControlState>(
            ControlState(
                isPlaying = false,
                isShuffle = false,
                repeatState = RepeatState.None,
                isLiked = false,
                isNextAvailable = false,
                isPreviousAvailable = false,
                isCrossfading = false,
                volume = 1f,
            ),
        )
    val controllerState: StateFlow<ControlState> = _controllerState
    private val _getVideo: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val getVideo: StateFlow<Boolean> = _getVideo

    private var _timeline =
        MutableStateFlow<TimeLine>(
            TimeLine(
                current = -1L,
                total = -1L,
                bufferedPercent = 0,
                loading = true,
            ),
        )
    val timeline: StateFlow<TimeLine> = _timeline

    private var _nowPlayingScreenData =
        MutableStateFlow<NowPlayingScreenData>(
            NowPlayingScreenData.initial(),
        )
    val nowPlayingScreenData: StateFlow<NowPlayingScreenData> = _nowPlayingScreenData

    private var _likeStatus = MutableStateFlow<Boolean>(false)
    val likeStatus: StateFlow<Boolean> = _likeStatus

    val openAppTime: StateFlow<Int> = dataStoreManager.openAppTime.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    private val lyricsHandler = LyricsHandler(
        scope = viewModelScope,
        dataStoreManager = dataStoreManager,
        lyricsCanvasRepository = lyricsCanvasRepository,
        mediaPlayerHandler = mediaPlayerHandler,
        nowPlayingScreenData = _nowPlayingScreenData,
        nowPlayingState = nowPlayingState,
        timeline = _timeline,
        makeToast = { makeToast(it) },
        getString = { getString(it) },
    )

    val shareSavedLyrics: StateFlow<Boolean> get() = lyricsHandler.shareSavedLyrics
    val translatedVoteState: StateFlow<VoteData?> get() = lyricsHandler.translatedVoteState
    val lyricsVoteState: StateFlow<VoteData?> get() = lyricsHandler.lyricsVoteState

    private val playbackHandler = PlaybackHandler(
        scope = viewModelScope,
        dataStoreManager = dataStoreManager,
        songRepository = songRepository,
        streamRepository = streamRepository,
        mediaPlayerHandler = mediaPlayerHandler,
        nowPlayingScreenData = _nowPlayingScreenData,
        nowPlayingState = _nowPlayingState,
        liked = _liked,
        likeStatus = _likeStatus,
        format = _format,
        makeToast = { makeToast(it) },
        getString = { getString(it) },
        clearNowPlayingFromProfile = { clearNowPlayingFromProfile() },
    )

    private val downloadHandler = ViewModelDownloadHandler(
        scope = viewModelScope,
        songRepository = songRepository,
        albumRepository = albumRepository,
        playlistRepository = playlistRepository,
        localPlaylistRepository = localPlaylistRepository,
        cacheRepository = cacheRepository,
        nowPlayingScreenData = _nowPlayingScreenData,
        nowPlayingState = _nowPlayingState,
        getString = { getString(it) },
    )

    private val updateHandler = UpdateHandler(
        scope = viewModelScope,
        dataStoreManager = dataStoreManager,
        updateRepository = updateRepository,
        makeToast = { makeToast(it) },
        log = { msg, level -> log(msg, level) },
    )

    private val profileHandler = ProfileHandler(
        scope = viewModelScope,
        dataStoreManager = dataStoreManager,
        userRepository = userRepository,
        supabase = supabase,
    )

    private val canvasHandler = CanvasHandler(
        scope = viewModelScope,
        dataStoreManager = dataStoreManager,
        lyricsCanvasRepository = lyricsCanvasRepository,
        nowPlayingScreenData = _nowPlayingScreenData,
        nowPlayingMediaId = { nowPlayingState.value?.mediaItem?.mediaId },
        nowPlayingCanvasUrl = { nowPlayingState.value?.songEntity?.canvasUrl },
        log = { msg, level -> log(msg, level) },
    )

    private val _navigateToLibraryFilter = MutableStateFlow<LibraryChipType?>(null)
    val navigateToLibraryFilter: StateFlow<LibraryChipType?> = _navigateToLibraryFilter.asStateFlow()

    fun setNavigateToLibraryFilter(filter: LibraryChipType?) {
        _navigateToLibraryFilter.value = filter
    }

    init {
        initGuideFlags()
        observeTimelineAndLyrics()
        observeNowPlayingState()
        observeMediaState()
        lyricsHandler.initJobs()
        downloadHandler.checkAllDownloadingSongs()
        downloadHandler.checkAllDownloadingPlaylists()
        downloadHandler.checkAllDownloadingLocalPlaylists()
        startGlobalFollowerSync()
    }

    private fun initGuideFlags() {
        runBlocking {
            dataStoreManager.getString("miniplayer_guide").first().let {
                isFirstMiniplayer = it != STATUS_DONE
            }
            dataStoreManager.getString("suggest_guide").first().let {
                isFirstSuggestions = it != STATUS_DONE
            }
            dataStoreManager.getString("liked_guide").first().let {
                isFirstLiked = it != STATUS_DONE
            }
        }
    }

    private fun observeTimelineAndLyrics() {
        viewModelScope.launch {
            log("SharedViewModel init")
            Logger.d("PLAYER_DEBUG", "SharedViewModel init: hash=${this@SharedViewModel.hashCode()}")
            Logger.d("PLAYER_DEBUG", "Queue StateFlow hashCode: ${mediaPlayerHandler.queueData.hashCode()}")
            Logger.d("PLAYER_DEBUG", "Control StateFlow hashCode: ${controllerState.hashCode()}")
            Logger.d("PLAYER_DEBUG", "NowPlaying StateFlow hashCode: ${nowPlayingState.hashCode()}")
            if (dataStoreManager.appVersion.first() != VersionManager.getVersionName()) {
                dataStoreManager.resetOpenAppTime()
                dataStoreManager.setAppVersion(
                    VersionManager.getVersionName(),
                )
            }
            dataStoreManager.openApp()
            val timeLineJob =
                launch {
                    nowPlayingState
                        .filterNotNull()
                        .flatMapLatest { nowPlayingState ->
                            timeline.map { timeLine ->
                                Pair(timeLine, nowPlayingState)
                            }
                        }.distinctUntilChanged { old, new ->
                            old.second.songEntity?.videoId == new.second.songEntity?.videoId &&
                                old.first.total == new.first.total
                        }.collectLatest {
                            Logger.d("PLAYER_DEBUG", "SharedViewModel Timeline collectLatest begins")
                            log("Timeline job ${(it.first.total.toString() + it.second.songEntity?.videoId).hashCode()}")
                            val nowPlaying = it.second
                            val timeline = it.first
                            if (timeline.total > 0 && nowPlaying.songEntity != null) {
                                if (nowPlaying.mediaItem.isSong() && nowPlayingScreenData.value.canvasData == null) {
                                    Logger.w(tag, "Duration is ${timeline.total}")
                                    Logger.w(tag, "MediaId is ${nowPlaying.mediaItem.mediaId}")
                                    getCanvas(nowPlaying.mediaItem.mediaId, (timeline.total / 1000).toInt())
                                }
                                nowPlaying.songEntity?.let { song ->
                                    if (nowPlayingScreenData.value.lyricsData == null) {
                                        Logger.w(tag, "Get lyrics from format")
                                        lyricsHandler.getLyricsFromFormat(nowPlaying.mediaItem.isVideo(), song, (timeline.total / 1000).toInt())
                                    }
                                }
                            }
                        }
                }
            val checkGetVideoJob =
                launch {
                    dataStoreManager.watchVideoInsteadOfPlayingAudio.collectLatest {
                        Logger.w(tag, "GetVideo is $it")
                        _getVideo.value = it == TRUE
                    }
                }
            timeLineJob.join()
            checkGetVideoJob.join()
        }
    }

    private fun observeNowPlayingState() {
        viewModelScope.launch {
            mediaPlayerHandler.nowPlayingState
                .distinctUntilChangedBy {
                    val rawId = it.songEntity?.videoId ?: it.track?.videoId ?: it.mediaItem.mediaId
                    val isPopulated = it.songEntity != null
                    "${rawId.removePrefix("Video")}_$isPopulated"
                }.collectLatest { state ->
                    Logger.d("PLAYER_DEBUG", "SharedViewModel NowPlayingState collectLatest begins, state=${state.mediaItem.mediaId}")
                    Logger.w(tag, "NowPlayingState is $state")
                    canvasHandler.cancelCanvas()
                    _nowPlayingState.value = state
                    syncNowPlayingToProfile(state)

                    state.songEntity?.let { track ->
                        _nowPlayingScreenData.value =
                            NowPlayingScreenData(
                                nowPlayingTitle = track.title,
                                artistName =
                                    track
                                        .artistName
                                        ?.joinToString(", ") ?: "",
                                isVideo = false,
                                thumbnailURL = null,
                                canvasData = null,
                                lyricsData = null,
                                songInfoData = null,
                                playlistName =
                                    mediaPlayerHandler.queueData.value
                                        ?.data
                                        ?.playlistName ?: "",
                            )
                    } ?: run {
                        _nowPlayingScreenData.value =
                            NowPlayingScreenData(
                                nowPlayingTitle = state.mediaItem.metadata.title ?: "Unknown Title",
                                artistName = state.mediaItem.metadata.artist ?: "Unknown Artist",
                                isVideo = false,
                                thumbnailURL = null,
                                canvasData = null,
                                lyricsData = null,
                                songInfoData = null,
                                playlistName =
                                    mediaPlayerHandler.queueData.value
                                        ?.data
                                        ?.playlistName ?: "",
                            )
                    }
                    state.mediaItem.let { now ->
                        canvasHandler.clearCanvas()
                        getLikeStatus(now.mediaId)
                        getSongInfo(now.mediaId)
                        getFormat(now.mediaId)
                        _nowPlayingScreenData.update {
                            it.copy(
                                thumbnailURL = now.metadata.artworkUri,
                                isVideo = now.isVideo(),
                            )
                        }
                    }
                    state.songEntity?.let { song ->
                        _liked.value = song.liked == true
                        _nowPlayingScreenData.update {
                            it.copy(
                                isExplicit = song.isExplicit,
                            )
                        }
                        
                        val durationToUse = song.durationSeconds
                        if (durationToUse > 0) {
                            if (state.mediaItem.isSong() && _nowPlayingScreenData.value.canvasData == null) {
                                getCanvas(state.mediaItem.mediaId, durationToUse)
                            }
                            if (_nowPlayingScreenData.value.lyricsData == null) {
                                lyricsHandler.getLyricsFromFormat(state.mediaItem.isVideo(), song, durationToUse)
                            }
                        }
                    }
                }
        }
    }

    private fun observeMediaState() {
        viewModelScope.launch {
            val job1 =
                launch {
                    mediaPlayerHandler.simpleMediaState.collect { mediaState ->
                        when (mediaState) {
                            is SimpleMediaState.Buffering -> {
                                _timeline.update {
                                    it.copy(
                                        loading = true,
                                    )
                                }
                            }

                            SimpleMediaState.Initial -> {
                                _timeline.update { it.copy(loading = true) }
                            }

                            SimpleMediaState.Ended -> {
                                _timeline.update {
                                    it.copy(
                                        current = 0L,
                                        bufferedPercent = 0,
                                        loading = false,
                                    )
                                }
                            }

                            is SimpleMediaState.Progress -> {
                                if (mediaState.progress >= 0L && mediaState.progress != _timeline.value.current) {
                                    if (_timeline.value.total > 0L) {
                                        _timeline.update {
                                            it.copy(
                                                total = mediaPlayerHandler.getPlayerDuration(),
                                                current = mediaState.progress,
                                                loading = false,
                                            )
                                        }
                                    } else {
                                        _timeline.update {
                                            it.copy(
                                                current = mediaState.progress,
                                                loading = true,
                                                total = mediaPlayerHandler.getPlayerDuration(),
                                            )
                                        }
                                    }
                                }
                                // When progress hasn't changed (same value polled again) or is negative,
                                // don't modify loading state. The loading flag is already managed by
                                // Buffering/Ready/Loading state events. Setting loading=true here would
                                // cause rapid flickering because the progress poll interval (100ms) is
                                // shorter than the adapter's position cache update interval (200ms),
                                // resulting in duplicate position values that incorrectly triggered loading.
                            }

                            is SimpleMediaState.Loading -> {
                                _timeline.update {
                                    it.copy(
                                        bufferedPercent = mediaState.bufferedPercentage,
                                        total = mediaState.duration,
                                        loading = true,
                                    )
                                }
                            }

                            is SimpleMediaState.Ready -> {
                                _timeline.update {
                                    it.copy(
                                        current = mediaPlayerHandler.getProgress(),
                                        loading = false,
                                        total = mediaState.duration,
                                    )
                                }
                            }
                        }
                    }
                }
            val controllerJob =
                launch {
                    Logger.w(tag, "ControllerJob is running")
                    mediaPlayerHandler.controlState.collectLatest {
                        Logger.d("PLAYER_DEBUG", "SharedViewModel ControlState collectLatest begins: isPlaying=${it.isPlaying}")
                        Logger.w(tag, "ControlState is $it")
                        _controllerState.value = it
                        // Propagate crossfade state to timeline so UI can react
                        _timeline.update { timeline ->
                            timeline.copy(isCrossfading = it.isCrossfading)
                        }
                    }
                }
            val sleepTimerJob =
                launch {
                    mediaPlayerHandler.sleepTimerState.collectLatest {
                        _sleepTimerState.value = it
                    }
                }
            val playlistNameJob =
                launch {
                    mediaPlayerHandler.queueData.collectLatest {
                        _nowPlayingScreenData.update {
                            it.copy(playlistName = it.playlistName)
                        }
                    }
                }
            job1.join()
            controllerJob.join()
            sleepTimerJob.join()
            playlistNameJob.join()
        }
    }

    private fun syncNowPlayingToProfile(state: NowPlayingTrackState) = profileHandler.syncNowPlayingToProfile(state)

    private fun clearNowPlayingFromProfile() = profileHandler.clearNowPlayingFromProfile()

    fun setIntent(intent: GenericIntent?) {
        _intent.value = intent
    }

    fun showNotificationPermissionDialog() {
        _showNotificationPermissionDialog.value = true
    }

    fun dismissNotificationPermissionDialog(doNotShowAgain: Boolean) {
        _showNotificationPermissionDialog.value = false
        if (doNotShowAgain) {
            putString("notification_permission_do_not_ask", "true")
        }
    }

    fun blurFullscreenLyrics(): Boolean = runBlocking { dataStoreManager.blurFullscreenLyrics.first() == TRUE }

    private fun getLikeStatus(videoId: String?) = playbackHandler.getLikeStatus(videoId)

    private fun getCanvas(videoId: String, duration: Int) = canvasHandler.getCanvas(videoId, duration)

    fun getString(key: String): String? = runBlocking { dataStoreManager.getString(key).first() }

    fun putString(
        key: String,
        value: String,
    ) {
        runBlocking { dataStoreManager.putString(key, value) }
    }

    fun setSleepTimer(minutes: Int) {
        mediaPlayerHandler.sleepStart(minutes)
    }

    fun stopSleepTimer() {
        mediaPlayerHandler.sleepStop()
    }

    private var _downloadState: MutableStateFlow<DownloadHandler.Download?> = MutableStateFlow(null)
    var downloadState: StateFlow<DownloadHandler.Download?> = _downloadState.asStateFlow()

    fun checkIsRestoring() = downloadHandler.checkIsRestoring()

    fun insertLyrics(lyrics: LyricsEntity) = lyricsHandler.insertLyrics(lyrics)

    fun loadSharedMediaItem(videoId: String) = playbackHandler.loadSharedMediaItem(videoId)

    fun loadMediaItemFromTrack(
        track: Track,
        type: String,
        index: Int? = null,
    ) = playbackHandler.loadMediaItemFromTrack(track, type, index)

    fun onUIEvent(uiEvent: UIEvent) = playbackHandler.onUIEvent(uiEvent)

    override fun onCleared() {
        Logger.w("Check onCleared", "onCleared")
    }

    fun getLocation() {
        regionCode = runBlocking { dataStoreManager.location.first() }
        quality = runBlocking { dataStoreManager.quality.first() }
        language = runBlocking { dataStoreManager.getString(SELECTED_LANGUAGE).first() }
    }

    private fun getFormat(mediaId: String?) = playbackHandler.getFormat(mediaId)

    fun getSongInfo(mediaId: String?) = playbackHandler.getSongInfo(mediaId)

    val updateResponse: StateFlow<UpdateData?> get() = updateHandler.updateResponse

    fun checkForUpdate(isManual: Boolean = false) = updateHandler.checkForUpdate(isManual)

    fun stopPlayer() = playbackHandler.stopPlayer()

    private var _recreateActivity: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val recreateActivity: StateFlow<Boolean> = _recreateActivity

    fun activityRecreate() {
        _recreateActivity.value = true
    }

    fun activityRecreateDone() {
        _recreateActivity.value = false
    }

    fun addListToQueue(listTrack: ArrayList<Track>) = playbackHandler.addListToQueue(listTrack)

    fun addToYouTubeLiked() = playbackHandler.addToYouTubeLiked()

    fun getTranslucentBottomBar() = dataStoreManager.translucentBottomBar

    fun getEnableLiquidGlass() = dataStoreManager.enableLiquidGlass

    private val _reloadDestination: MutableStateFlow<KClass<*>?> = MutableStateFlow(null)
    val reloadDestination: StateFlow<KClass<*>?> = _reloadDestination.asStateFlow()

    fun reloadDestination(destination: KClass<*>) {
        _reloadDestination.value = destination
    }

    fun reloadDestinationDone() {
        _reloadDestination.value = null
    }

    fun shouldCheckForUpdate(): Boolean = runBlocking { dataStoreManager.autoCheckForUpdates.first() == TRUE }

    val downloadFileProgress: StateFlow<DownloadProgress> get() = downloadHandler.downloadFileProgress

    fun downloadFile(bitmap: ImageBitmap) = downloadHandler.downloadFile(bitmap)

    fun downloadFileDone() = downloadHandler.downloadFileDone()

    fun onDoneReview(isDismissOnly: Boolean = true) {
        viewModelScope.launch {
            if (!isDismissOnly) {
                dataStoreManager.doneOpenAppTime()
            } else {
                dataStoreManager.openApp()
            }
        }
    }

    fun onDoneRequestingShareLyrics(contributor: Pair<String, String>? = null) {
        viewModelScope.launch {
            dataStoreManager.setHelpBuildLyricsDatabase(true)
            dataStoreManager.setContributorLyricsDatabase(
                contributor,
            )
        }
    }

    fun setBitmap(bitmap: ImageBitmap?) {
        _nowPlayingScreenData.update {
            it.copy(bitmap = bitmap)
        }
    }

    fun voteLyrics(upvote: Boolean) = lyricsHandler.voteLyrics(upvote)

    fun voteTranslatedLyrics(upvote: Boolean) = lyricsHandler.voteTranslatedLyrics(upvote)

    fun shouldStopMusicService(): Boolean = runBlocking { dataStoreManager.killServiceOnExit.first() == TRUE }

    fun isUserLoggedIn(): Boolean = runBlocking { dataStoreManager.cookie.first().isNotEmpty() }

    fun isCombineFavoriteAndYTLiked(): Boolean = runBlocking { dataStoreManager.combineLocalAndYouTubeLiked.first() == TRUE }

    private val _snackbarEvent = MutableSharedFlow<SnackbarEvent>()
    val snackbarEvent = _snackbarEvent.asSharedFlow()

    private fun startGlobalFollowerSync() {
        viewModelScope.launch {
            while (true) {
                try {
                    val currentUserId = supabase.auth.currentUserOrNull()?.id
                    if (currentUserId != null) {
                        userRepository.getFollowers(currentUserId).firstOrNull()?.let { followers ->
                            val localNotifications = commonRepository.getAllNotifications().firstOrNull() ?: emptyList()
                            var newlyFollowed = false
                            
                            for (follower in followers) {
                                val isDuplicateFollower = localNotifications.any { local ->
                                    local.channelId == "follower_activity" &&
                                            local.single.firstOrNull()?.get("browseId") == follower.id
                                }
                                if (!isDuplicateFollower) {
                                    val entity = NotificationEntity(
                                        channelId = "follower_activity",
                                        thumbnail = follower.avatarUrl ?: "",
                                        name = follower.displayName.takeIf { !it.isNullOrBlank() } ?: "User",
                                        single = listOf(
                                            mapOf(
                                                "title" to "mulai mengikuti Anda",
                                                "browseId" to follower.id
                                            )
                                        ),
                                        album = emptyList(),
                                        time = com.tan.domain.extension.now()
                                    )
                                    commonRepository.insertNotification(entity)
                                    newlyFollowed = true
                                    
                                    // Trigger snackbar
                                    _snackbarEvent.emit(
                                        SnackbarEvent(
                                            message = "${entity.name} mulai mengikuti Anda",
                                            actionLabel = "Lihat",
                                            actionId = "view_notification"
                                        )
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    com.tan.logger.Logger.e(tag, "Failed to sync followers globally: ${e.message}")
                }
                delay(60000) // check every 60 seconds
            }
        }
    }
}