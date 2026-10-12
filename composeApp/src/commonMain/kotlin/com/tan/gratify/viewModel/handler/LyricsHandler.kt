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
import com.tan.domain.utils.reconcileWordBoundaries
import com.tan.domain.utils.hasDisplayableLyrics
import com.tan.domain.utils.assessTextQuality
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        scope.launch {
            combine(dataStoreManager.enableTranslateLyric, dataStoreManager.useAITranslation) { provider, ai ->
                provider == TRUE || ai == TRUE
            }.distinctUntilChanged().drop(1).collectLatest { enabled ->
                if (enabled) setLyricsProvider()
                else nowPlayingScreenData.update { screen ->
                    screen.copy(lyricsData = screen.lyricsData?.copy(translatedLyrics = null))
                }
            }
        }
    }

    private var lyricsJob: kotlinx.coroutines.Job? = null
    private val generation = MutableStateFlow(0)
    private var requestedTrack: Pair<String, Int>? = null

    fun cancelLyrics() {
        generation.update { it + 1 }
        lyricsJob?.cancel()
        lyricsJob = null
        requestedTrack = null
    }

    fun getLyricsFromFormat(isVideo: Boolean, song: SongEntity, duration: Int, forceReload: Boolean = false) {
        val key = song.videoId to duration
        if (!forceReload && requestedTrack == key && lyricsJob?.isActive == true) return
        cancelLyrics()
        requestedTrack = key
        val request = generation.value
        val videoId = song.videoId
        nowPlayingScreenData.update { it.copy(lyricsLoading = true) }
        lyricsJob = scope.launch(Dispatchers.Default) {
            fun isCurrent() = request == generation.value && nowPlayingState.value?.songEntity?.videoId == videoId
            try {
                val artist = song.artistName?.firstOrNull()?.takeUnless { it.contains("Various Artists") }
                    ?: mediaPlayerHandler.nowPlaying.value?.metadata?.artist.orEmpty()
                resetLyricsVoteState()
                var cached = kotlinx.coroutines.withTimeoutOrNull(1500) {
                    lyricsCanvasRepository.getSavedLyrics(videoId).firstOrNull()?.toLyrics()?.assessTextQuality()
                        ?.takeIf { it.hasReadableLines() }
                }
                if (isCurrent() && cached?.hasDisplayableLyrics() == true) {
                    updateLyrics(videoId, duration, cached, false, LyricsProvider.OFFLINE)
                }
                val preferred = dataStoreManager.lyricsProvider.first()
                val spotifyAvailable = dataStoreManager.spotifyLyrics.first() == TRUE &&
                    dataStoreManager.spdc.first().isNotBlank()
                var youtubeTranslation: Lyrics? = null
                val result = resolveLyricsFallback(
                    providers = lyricsProviderOrder(preferred, spotifyAvailable),
                    preferWordTiming = false,
                    referenceLyrics = cached,
                    onCandidate = { candidate ->
                        cached = cached?.reconcileWordBoundaries(candidate.lyrics)
                        if (isCurrent() && !keepsFormattedRichCache(cached, candidate.lyrics, preferWordTiming = false)) {
                            updateLyrics(videoId, duration, candidate.lyrics, false, candidate.provider)
                        } else if (isCurrent() && cached?.hasReadableLines() == true) {
                            updateLyrics(videoId, duration, cached!!, false, LyricsProvider.OFFLINE)
                        }
                    },
                ) { provider ->
                    val knownDuration = duration.takeIf { it > 0 }
                    when (provider) {
                        LyricsProvider.SIMPMUSIC -> lyricsCanvasRepository.getSimpMusicLyrics(videoId).firstOrNull()?.data
                        LyricsProvider.GRATIFYMUSIC -> lyricsCanvasRepository.getGratifyLyrics(videoId, song.title, artist, knownDuration).firstOrNull()?.data
                        LyricsProvider.BETTER_LYRICS -> lyricsCanvasRepository.getBetterLyrics(artist, song.title, knownDuration).firstOrNull()?.data
                        LyricsProvider.LRCLIB -> lyricsCanvasRepository.getLrclibLyricsData(artist, song.title, knownDuration).firstOrNull()?.data
                        LyricsProvider.SPOTIFY -> lyricsCanvasRepository.getSpotifyLyrics(dataStoreManager, "${song.title} $artist", knownDuration).firstOrNull()?.data
                        LyricsProvider.YOUTUBE -> {
                            val caption = lyricsCanvasRepository.getYouTubeCaption(dataStoreManager.youtubeSubtitleLanguage.first(), videoId).firstOrNull()?.data
                            youtubeTranslation = caption?.second
                            caption?.first
                        }
                        else -> null
                    }
                }
                if (!isCurrent()) return@launch
                val lyrics = if (cached?.hasReadableLines() == true && keepsFormattedRichCache(cached, result?.lyrics, preferWordTiming = false)) {
                    cached!!
                } else result?.lyrics ?: cached?.takeIf { it.hasDisplayableLyrics() } ?: run {
                    nowPlayingScreenData.update { it.copy(lyricsData = null) }
                    return@launch
                }
                if (nowPlayingScreenData.value.lyricsData == null) {
                    updateLyrics(videoId, duration, lyrics, false, result?.provider ?: LyricsProvider.OFFLINE)
                }
                val translationRequested = dataStoreManager.enableTranslateLyric.first() == TRUE ||
                    dataStoreManager.useAITranslation.first() == TRUE
                if (!translationRequested) return@launch
                if (result?.provider == LyricsProvider.YOUTUBE && youtubeTranslation?.hasReadableLines() == true) {
                    updateLyrics(videoId, duration, youtubeTranslation, true, LyricsProvider.YOUTUBE)
                } else {
                    kotlinx.coroutines.withTimeoutOrNull(12000) {
                        when (result?.provider) {
                            LyricsProvider.SIMPMUSIC -> getSimpMusicTranslatedLyrics(videoId, lyrics)
                            LyricsProvider.GRATIFYMUSIC -> getGratifyTranslatedLyrics(videoId, lyrics)
                            else -> getAITranslationLyrics(videoId, lyrics)
                        }
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (failure: Exception) { Logger.w(tag, "Lyrics lookup failed: ${failure::class.simpleName}") }
            finally { if (isCurrent()) nowPlayingScreenData.update { it.copy(lyricsLoading = false) } }
        }
    }

    private suspend fun getSimpMusicTranslatedLyrics(videoId: String, lyrics: Lyrics) {
        val language = dataStoreManager.translationLanguage.first()
        val translated = lyricsCanvasRepository.getSimpMusicTranslatedLyrics(videoId, language).firstOrNull()?.data
        if (translated?.hasDisplayableLyrics() == true && translated.syncType != "RICH_SYNCED") {
            updateLyrics(videoId, 0, translated, true, LyricsProvider.SIMPMUSIC)
        } else getAITranslationLyrics(videoId, lyrics)
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
            dataStoreManager.aiApiKey.first().isNotEmpty()
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

    fun insertLyrics(lyrics: LyricsEntity) {
        scope.launch {
            lyricsCanvasRepository.insertLyrics(lyrics)
        }
    }

    fun setLyricsProvider() {
        scope.launch {
            val songEntity = nowPlayingState.value?.songEntity ?: return@launch
            val isVideo = nowPlayingState.value?.mediaItem?.isVideo() ?: false
            getLyricsFromFormat(isVideo, songEntity, timeline.value.total.toInt() / 1000, forceReload = true)
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

    private suspend fun updateLyrics(
        videoId: String,
        duration: Int,
        inputLyrics: Lyrics?,
        isTranslatedLyrics: Boolean,
        lyricsProvider: LyricsProvider = LyricsProvider.GRATIFYMUSIC,
    ) = withContext(Dispatchers.Default) {
        if (nowPlayingState.value?.songEntity?.videoId != videoId) return@withContext
        if (isTranslatedLyrics && dataStoreManager.enableTranslateLyric.first() != TRUE &&
            dataStoreManager.useAITranslation.first() != TRUE) return@withContext
        if (inputLyrics == null) {
            nowPlayingScreenData.update { it.copy(lyricsData = null) }
            return@withContext
        }
        // Final boundary for every provider, translation and cache update.
        if (!inputLyrics.hasDisplayableLyrics()) return@withContext

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
                    return@withContext
                }
            }
        }

        val shouldSendLyricsToGratify =
            lyricsProvider != LyricsProvider.GRATIFYMUSIC && lyricsProvider != LyricsProvider.SIMPMUSIC &&
                dataStoreManager.helpBuildLyricsDatabase.first() == TRUE
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
