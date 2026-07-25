package com.tan.gratify.viewModel.handler

import com.tan.domain.data.entities.LyricsEntity
import com.tan.domain.data.entities.SongEntity
import com.tan.domain.data.entities.TranslatedLyricsEntity
import com.tan.domain.data.model.browse.album.Track
import com.tan.domain.data.model.metadata.Lyrics
import com.tan.domain.data.model.streams.TimeLine
import com.tan.domain.extension.decodeHtmlEntities
import com.tan.domain.extension.isVideo
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.manager.DataStoreManager.Values.FALSE
import com.tan.domain.manager.DataStoreManager.Values.TRUE
import com.tan.domain.mediaservice.handler.MediaPlayerHandler
import com.tan.domain.mediaservice.handler.NowPlayingTrackState
import com.tan.domain.repository.LyricsCanvasRepository
import com.tan.domain.utils.Resource
import com.tan.domain.utils.toListName
import com.tan.domain.utils.toLyrics
import com.tan.domain.utils.toLyricsEntity
import com.tan.domain.utils.toSongEntity
import com.tan.domain.utils.toSyncedLyrics
import com.tan.domain.utils.toTrack
import com.tan.gratify.viewModel.LyricsProvider
import com.tan.gratify.viewModel.NowPlayingScreenData
import com.tan.gratify.viewModel.VoteData
import com.tan.gratify.viewModel.VoteState
import com.tan.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.vote_submitted
import kotlin.math.abs

class LyricsHandler(
    private val scope: CoroutineScope,
    private val dataStoreManager: DataStoreManager,
    private val lyricsCanvasRepository: LyricsCanvasRepository,
    private val mediaPlayerHandler: MediaPlayerHandler,
    private val nowPlayingScreenData: MutableStateFlow<NowPlayingScreenData>,
    private val nowPlayingState: StateFlow<NowPlayingTrackState?>,
    private val timeline: StateFlow<TimeLine>,
    private val makeToast: (String?) -> Unit,
    private val getString: (StringResource) -> String,
) {
    private val tag = "LyricsHandler"

    private val _translatedVoteState = MutableStateFlow<VoteData?>(null)
    val translatedVoteState: StateFlow<VoteData?> = _translatedVoteState.asStateFlow()

    private val _lyricsVoteState = MutableStateFlow<VoteData?>(null)
    val lyricsVoteState: StateFlow<VoteData?> = _lyricsVoteState.asStateFlow()

    private val _shareSavedLyrics: MutableStateFlow<Boolean> = MutableStateFlow(true)
    val shareSavedLyrics: StateFlow<Boolean> get() = _shareSavedLyrics

    fun initJobs() {
        scope.launch {
            dataStoreManager.lyricsProvider.distinctUntilChanged().collectLatest {
                setLyricsProvider()
            }
        }
        scope.launch {
            dataStoreManager.helpBuildLyricsDatabase.distinctUntilChanged().collectLatest {
                _shareSavedLyrics.value = it == TRUE
            }
        }
    }

    fun getLyricsFromFormat(
        isVideo: Boolean,
        song: SongEntity,
        duration: Int,
    ) {
        scope.launch {
            val videoId = song.videoId
            Logger.w(tag, "Get Lyrics From Format for $videoId")
            val artistName = song.artistName
            val artist =
                if (artistName?.firstOrNull() != null &&
                    artistName.firstOrNull()?.contains("Various Artists") == false
                ) {
                    artistName.firstOrNull()
                } else {
                    mediaPlayerHandler.nowPlaying.first()?.metadata?.artist ?: ""
                }
            resetLyricsVoteState()
            val lyricsProvider = dataStoreManager.lyricsProvider.first()
            when (lyricsProvider) {
                DataStoreManager.GRATIFYMUSIC -> {
                    getGratifyLyrics(videoId, song, (artist ?: ""), duration)
                }
                DataStoreManager.LRCLIB -> {
                    getLrclibLyrics(song, (artist ?: ""), duration)
                }
                DataStoreManager.YOUTUBE -> {
                    getYouTubeCaption(videoId, song, (artist ?: ""), duration)
                }
                DataStoreManager.BETTER_LYRICS -> {
                    getBetterLyrics(song, (artist ?: "").toString(), duration)
                }
            }
        }
    }

    private suspend fun getGratifyLyrics(
        videoId: String,
        song: SongEntity,
        artist: String?,
        duration: Int,
    ) {
        lyricsCanvasRepository.getGratifyLyrics(videoId).collectLatest {
            Logger.w(tag, "Get Gratify Lyrics for $videoId: $it")
            val data = it.data
            if (it is Resource.Success && data != null) {
                Logger.d(tag, "Get Gratify Lyrics Success")
                updateLyrics(videoId, duration, data, false, LyricsProvider.GRATIFYMUSIC)
                insertLyrics(data.toLyricsEntity(videoId))
                getGratifyTranslatedLyrics(videoId, data)
            } else if (dataStoreManager.spotifyLyrics.first() == TRUE) {
                getSpotifyLyrics(
                    song.toTrack().copy(durationSeconds = duration),
                    "${song.title} $artist",
                    duration,
                )
            } else {
                getLrclibLyrics(song, (artist ?: ""), duration)
            }
        }
    }

    private suspend fun getYouTubeCaption(
        videoId: String,
        song: SongEntity,
        artist: String?,
        duration: Int,
    ) {
        lyricsCanvasRepository
            .getYouTubeCaption(dataStoreManager.youtubeSubtitleLanguage.first(), videoId)
            .cancellable()
            .collect { response ->
                val data = response.data
                when (response) {
                    is Resource.Success if (data != null) -> {
                        val lyrics = data.first
                        val translatedLyrics = data.second
                        insertLyrics(lyrics.toLyricsEntity(videoId))
                        updateLyrics(videoId, duration, lyrics, false, LyricsProvider.YOUTUBE)
                        if (translatedLyrics != null) {
                            updateLyrics(videoId, duration, translatedLyrics, true, LyricsProvider.YOUTUBE)
                        } else {
                            getAITranslationLyrics(videoId, lyrics)
                        }
                    }
                    else -> {
                        getGratifyLyrics(videoId, song, (artist ?: ""), duration)
                    }
                }
            }
    }

    private fun getLrclibLyrics(
        song: SongEntity,
        artist: String,
        duration: Int,
    ) {
        scope.launch {
            lyricsCanvasRepository
                .getLrclibLyricsData(artist, song.title, duration)
                .collectLatest { res ->
                    val data = res.data
                    when (res) {
                        is Resource.Success if (data != null) -> {
                            Logger.d(tag, "Get Lyrics Data Success")
                            updateLyrics(song.videoId, duration, res.data, false, LyricsProvider.LRCLIB)
                            insertLyrics(res.data?.toLyricsEntity(song.videoId) ?: return@collectLatest)
                            getAITranslationLyrics(song.videoId, data)
                        }
                        else -> {
                            getSavedLyrics(song.toTrack().copy(durationSeconds = duration))
                        }
                    }
                }
        }
    }

    private fun getBetterLyrics(
        song: SongEntity,
        artist: String,
        duration: Int,
    ) {
        scope.launch {
            lyricsCanvasRepository
                .getBetterLyrics(artist, song.title, duration)
                .collectLatest { res ->
                    val data = res.data
                    when (res) {
                        is Resource.Success if (data != null) -> {
                            Logger.d(tag, "Get BetterLyrics Success")
                            updateLyrics(song.videoId, duration, data, false, LyricsProvider.BETTER_LYRICS)
                            insertLyrics(data.toLyricsEntity(song.videoId))
                            getAITranslationLyrics(song.videoId, data)
                        }
                        else -> {
                            Logger.w(tag, "Get BetterLyrics Error: ${res.message}")
                            getGratifyLyrics(song.videoId, song, artist, duration)
                        }
                    }
                }
        }
    }

    private suspend fun getGratifyTranslatedLyrics(
        videoId: String,
        lyrics: Lyrics,
    ) {
        val translationLanguage = dataStoreManager.translationLanguage.first()
        lyricsCanvasRepository.getGratifyTranslatedLyrics(videoId, translationLanguage).collectLatest { response ->
            val data = response.data
            when (response) {
                is Resource.Success if (data != null) -> {
                    if (data.syncType == "RICH_SYNCED") {
                        Logger.w(tag, "Gratify translated lyrics are RICH_SYNCED, downvoting and falling back to AI")
                        val gratifyMusicLyricsId = data.gratifyMusicLyrics?.id
                        if (!gratifyMusicLyricsId.isNullOrEmpty()) {
                            scope.launch {
                                lyricsCanvasRepository
                                    .voteGratifyTranslatedLyrics(gratifyMusicLyricsId, false)
                                    .collectLatest { voteResult ->
                                        when (voteResult) {
                                            is Resource.Error -> Logger.w(tag, "Downvote RICH_SYNCED translated lyrics error: ${voteResult.message}")
                                            is Resource.Success -> Logger.d(tag, "Downvote RICH_SYNCED translated lyrics success")
                                        }
                                    }
                            }
                        }
                        getAITranslationLyrics(videoId, lyrics)
                    } else {
                        Logger.d(tag, "Get Gratify Translated Lyrics Success")
                        updateLyrics(videoId, 0, data, true, LyricsProvider.GRATIFYMUSIC)
                    }
                }
                else -> {
                    Logger.w(tag, "Get Gratify Translated Lyrics Error: ${response.message}")
                    getAITranslationLyrics(videoId, lyrics)
                }
            }
        }
    }

    private suspend fun getAITranslationLyrics(
        videoId: String,
        lyrics: Lyrics,
    ) {
        Logger.d(tag, "Get AI Translation Lyrics for $videoId")
        if (dataStoreManager.useAITranslation.first() == TRUE &&
            dataStoreManager.aiApiKey.first().isNotEmpty() &&
            dataStoreManager.enableTranslateLyric.first() == FALSE
        ) {
            val savedTranslatedLyrics =
                lyricsCanvasRepository
                    .getSavedTranslatedLyrics(videoId, dataStoreManager.translationLanguage.first())
                    .firstOrNull()
            if (savedTranslatedLyrics != null) {
                Logger.d(tag, "Get Saved Translated Lyrics")
                updateLyrics(videoId, 0, savedTranslatedLyrics.toLyrics(), true, LyricsProvider.AI)
            } else {
                val lyricsForAi =
                    if (lyrics.syncType == "RICH_SYNCED") {
                        lyrics.toSyncedLyrics()
                    } else {
                        lyrics
                    }
                lyricsCanvasRepository
                    .getAITranslationLyrics(lyricsForAi, dataStoreManager.translationLanguage.first())
                    .cancellable()
                    .collectLatest {
                        val data = it.data
                        when (it) {
                            is Resource.Success if (data != null) -> {
                                Logger.d(tag, "Get AI Translate Lyrics Success")
                                lyricsCanvasRepository.insertTranslatedLyrics(
                                    TranslatedLyricsEntity(
                                        videoId = videoId,
                                        language = dataStoreManager.translationLanguage.first(),
                                        error = false,
                                        lines = data.lines,
                                        syncType = data.syncType,
                                    ),
                                )
                                updateLyrics(videoId, 0, data, true, LyricsProvider.AI)
                            }
                            else -> {
                                Logger.w(tag, "Get AI Translate Lyrics Error: ${it.message}")
                            }
                        }
                    }
            }
        }
    }

    private fun getSpotifyLyrics(
        track: Track,
        query: String,
        duration: Int? = null,
    ) {
        scope.launch {
            Logger.d("Check SpotifyLyrics", "SpotifyLyrics $query")
            lyricsCanvasRepository.getSpotifyLyrics(dataStoreManager, query, duration).cancellable().collect { response ->
                Logger.d("Check SpotifyLyrics", response.toString())
                val data = response.data
                when (response) {
                    is Resource.Success -> {
                        if (data != null) {
                            insertLyrics(data.toLyricsEntity(track.videoId))
                            updateLyrics(track.videoId, duration ?: 0, data, false, LyricsProvider.SPOTIFY)
                            getAITranslationLyrics(track.videoId, data)
                        }
                    }
                    else -> {
                        getLrclibLyrics(
                            track.toSongEntity(),
                            track.artists.toListName().firstOrNull() ?: "",
                            duration ?: 0,
                        )
                    }
                }
            }
        }
    }

    private fun getSavedLyrics(track: Track) {
        scope.launch {
            lyricsCanvasRepository.getSavedLyrics(track.videoId).cancellable().collectLatest { lyrics ->
                if (lyrics != null) {
                    val lyricsData = lyrics.toLyrics()
                    Logger.d(tag, "Saved Lyrics $lyricsData")
                    updateLyrics(track.videoId, track.durationSeconds ?: 0, lyricsData, false, LyricsProvider.OFFLINE)
                    getAITranslationLyrics(track.videoId, lyricsData)
                }
            }
        }
    }

    fun insertLyrics(lyrics: LyricsEntity) {
        scope.launch {
            lyricsCanvasRepository.insertLyrics(lyrics)
        }
    }

    fun setLyricsProvider() {
        scope.launch {
            val songEntity = nowPlayingState.value?.songEntity ?: return@launch
            val isVideo = nowPlayingState.value?.mediaItem?.isVideo() ?: false
            getLyricsFromFormat(isVideo, songEntity, timeline.value.total.toInt() / 1000)
        }
    }

    fun voteLyrics(upvote: Boolean) {
        val lyricsData = nowPlayingScreenData.value.lyricsData
        val lyricsProvider = lyricsData?.lyricsProvider
        val gratifyMusicLyricsId = lyricsData?.lyrics?.gratifyMusicLyrics?.id ?: return

        if (lyricsProvider != LyricsProvider.GRATIFYMUSIC || gratifyMusicLyricsId.isEmpty()) {
            Logger.w(tag, "Cannot vote: not a Gratify lyrics or missing ID")
            return
        }

        scope.launch {
            _lyricsVoteState.update { it?.copy(state = VoteState.Loading) }
            lyricsCanvasRepository
                .voteGratifyLyrics(lyricsId = gratifyMusicLyricsId, upvote = upvote)
                .collectLatest { result ->
                    when (result) {
                        is Resource.Error -> {
                            Logger.w(tag, "Vote Gratify Lyrics Error ${result.message}")
                            _lyricsVoteState.update {
                                it?.copy(state = VoteState.Error(result.message ?: "Unknown error"))
                            }
                        }
                        is Resource.Success -> {
                            Logger.d(tag, "Vote Gratify Lyrics Success")
                            _lyricsVoteState.update {
                                it?.copy(state = VoteState.Success(upvote), vote = it.vote + if (upvote) 1 else -1)
                            }
                            makeToast(getString(Res.string.vote_submitted))
                        }
                    }
                }
        }
    }

    fun voteTranslatedLyrics(upvote: Boolean) {
        val translatedLyrics = nowPlayingScreenData.value.lyricsData?.translatedLyrics
        val lyricsProvider = translatedLyrics?.second
        val gratifyMusicLyricsId = translatedLyrics?.first?.gratifyMusicLyrics?.id ?: return

        if (lyricsProvider != LyricsProvider.GRATIFYMUSIC || gratifyMusicLyricsId.isEmpty()) {
            Logger.w(tag, "Cannot vote: not a Gratify translated lyrics or missing ID")
            return
        }

        scope.launch {
            _translatedVoteState.update { it?.copy(state = VoteState.Loading) }
            lyricsCanvasRepository
                .voteGratifyTranslatedLyrics(translatedLyricsId = gratifyMusicLyricsId, upvote = upvote)
                .collectLatest { result ->
                    when (result) {
                        is Resource.Error -> {
                            Logger.w(tag, "Vote Gratify Translated Lyrics Error ${result.message}")
                            _translatedVoteState.update {
                                it?.copy(state = VoteState.Error(result.message ?: "Unknown error"))
                            }
                        }
                        is Resource.Success -> {
                            Logger.d(tag, "Vote Gratify Translated Lyrics Success")
                            _translatedVoteState.update {
                                it?.copy(state = VoteState.Success(upvote), vote = it.vote + if (upvote) 1 else -1)
                            }
                            makeToast(getString(Res.string.vote_submitted))
                        }
                    }
                }
        }
    }

    fun resetLyricsVoteState() {
        _lyricsVoteState.value = null
        _translatedVoteState.value = null
    }

    private fun updateLyrics(
        videoId: String,
        duration: Int,
        inputLyrics: Lyrics?,
        isTranslatedLyrics: Boolean,
        lyricsProvider: LyricsProvider = LyricsProvider.GRATIFYMUSIC,
    ) {
        if (inputLyrics == null) {
            nowPlayingScreenData.update { it.copy(lyricsData = null) }
            return
        }

        val lyrics =
            inputLyrics.copy(
                lines = inputLyrics.lines?.map { line ->
                    line.copy(words = decodeHtmlEntities(line.words))
                },
            )

        if (isTranslatedLyrics && lyricsProvider != LyricsProvider.AI) {
            val originalLyrics = nowPlayingScreenData.value.lyricsData?.lyrics
            val originalLines = originalLyrics?.lines
            val lyricsLines = lyrics.lines
            if (originalLyrics != null && originalLines != null && lyricsLines != null) {
                var timeSyncErrorCount = 0
                val totalLines = originalLines.size

                if (originalLines.size == lyricsLines.size) {
                    originalLines.forEachIndexed { index, originalLine ->
                        val originalTime = originalLine.startTimeMs.toLongOrNull() ?: 0L
                        val translatedLine = lyricsLines[index]
                        val translatedTime = translatedLine.startTimeMs.toLongOrNull() ?: 0L
                        val timeDiff = abs(originalTime - translatedTime)
                        if (timeDiff > 1000L) {
                            timeSyncErrorCount++
                        }
                    }
                } else {
                    val usedIndices = mutableSetOf<Int>()
                    originalLines.forEach { originalLine ->
                        val originalTime = originalLine.startTimeMs.toLongOrNull() ?: 0L
                        var bestIndex = -1
                        var bestDiff = Long.MAX_VALUE
                        lyricsLines.forEachIndexed { index, line ->
                            if (index !in usedIndices) {
                                val diff = abs((line.startTimeMs.toLongOrNull() ?: 0L) - originalTime)
                                if (diff < bestDiff) {
                                    bestDiff = diff
                                    bestIndex = index
                                }
                            }
                        }
                        if (bestIndex >= 0) {
                            usedIndices.add(bestIndex)
                            if (bestDiff > 1000L) {
                                timeSyncErrorCount++
                            }
                        } else {
                            timeSyncErrorCount++
                        }
                    }
                }

                val syncErrorRatio = if (totalLines > 0) timeSyncErrorCount.toFloat() / totalLines else 0f
                if (syncErrorRatio > 0.25f || (totalLines > 0 && timeSyncErrorCount > totalLines / 2)) {
                    Logger.w(
                        tag,
                        "Translated lyrics out of sync: $timeSyncErrorCount/$totalLines lines with time diff > 1s (${(syncErrorRatio * 100).toInt()}%)",
                    )

                    nowPlayingScreenData.update {
                        it.copy(lyricsData = it.lyricsData?.copy(translatedLyrics = null))
                    }

                    scope.launch {
                        lyricsCanvasRepository.removeTranslatedLyrics(
                            videoId,
                            dataStoreManager.translationLanguage.first(),
                        )
                        Logger.d(tag, "Removed out-of-sync translated lyrics for $videoId")
                        val gratifyMusicLyricsId = lyrics.gratifyMusicLyrics?.id
                        if (lyricsProvider == LyricsProvider.GRATIFYMUSIC && !gratifyMusicLyricsId.isNullOrEmpty()) {
                            scope.launch {
                                lyricsCanvasRepository
                                    .voteGratifyTranslatedLyrics(
                                        translatedLyricsId = gratifyMusicLyricsId,
                                        false,
                                    ).collectLatest {
                                        when (it) {
                                            is Resource.Error -> {
                                                Logger.w(tag, "Vote Gratify Translated Lyrics Error ${it.message}")
                                            }
                                            is Resource.Success -> {
                                                Logger.d(tag, "Vote Gratify Translated Lyrics Success")
                                            }
                                        }
                                    }
                            }
                        }
                        nowPlayingScreenData.value.lyricsData?.lyrics?.let {
                            getAITranslationLyrics(videoId, it)
                        }
                    }
                    return
                }
            }
        }

        val shouldSendLyricsToGratify =
            runBlocking { dataStoreManager.helpBuildLyricsDatabase.first() == TRUE } &&
                lyricsProvider != LyricsProvider.GRATIFYMUSIC
        if (nowPlayingState.value?.songEntity?.videoId == videoId) {
            val track = nowPlayingState.value?.track
            when (isTranslatedLyrics) {
                true -> {
                    if (lyricsProvider == LyricsProvider.GRATIFYMUSIC) {
                        _translatedVoteState.value =
                            VoteData(
                                id = lyrics.gratifyMusicLyrics?.id ?: "",
                                vote = lyrics.gratifyMusicLyrics?.vote ?: 0,
                                state = VoteState.Idle,
                            )
                    }
                    nowPlayingScreenData.update {
                        it.copy(
                            lyricsData = it.lyricsData?.copy(translatedLyrics = lyrics to lyricsProvider),
                        )
                    }
                    if (shouldSendLyricsToGratify && track != null) {
                        scope.launch {
                            lyricsCanvasRepository
                                .insertGratifyTranslatedLyrics(
                                    dataStoreManager,
                                    track,
                                    lyrics,
                                    dataStoreManager.translationLanguage.first(),
                                ).collect {
                                    when (it) {
                                        is Resource.Error -> {
                                            Logger.w(tag, "Insert Gratify Translated Lyrics Error ${it.message}")
                                        }
                                        is Resource.Success -> {
                                            Logger.d(tag, "Insert Gratify Translated Lyrics Success")
                                        }
                                    }
                                }
                        }
                    }
                }
                false -> {
                    if (lyricsProvider == LyricsProvider.GRATIFYMUSIC) {
                        _lyricsVoteState.value =
                            VoteData(
                                id = lyrics.gratifyMusicLyrics?.id ?: "",
                                vote = lyrics.gratifyMusicLyrics?.vote ?: 0,
                                state = VoteState.Idle,
                            )
                    }
                    nowPlayingScreenData.update {
                        it.copy(
                            lyricsData = NowPlayingScreenData.LyricsData(
                                lyrics = lyrics,
                                lyricsProvider = lyricsProvider,
                            ),
                        )
                    }
                    scope.launch {
                        lyricsCanvasRepository.insertLyrics(
                            LyricsEntity(
                                videoId = videoId,
                                error = false,
                                lines = lyrics.lines,
                                syncType = lyrics.syncType,
                            ),
                        )
                    }
                    if (shouldSendLyricsToGratify && track != null) {
                        scope.launch {
                            lyricsCanvasRepository
                                .insertGratifyLyrics(
                                    dataStoreManager,
                                    track,
                                    duration,
                                    lyrics,
                                ).collect {
                                    when (it) {
                                        is Resource.Error -> {
                                            Logger.w(tag, "Insert Gratify Lyrics Error ${it.message}")
                                        }
                                        is Resource.Success -> {
                                            Logger.d(tag, "Insert Gratify Lyrics Success")
                                        }
                                    }
                                }
                        }
                    }
                }
            }
        }
    }
}
