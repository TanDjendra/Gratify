package com.tan.gratify.viewModel.handler

import com.tan.domain.data.entities.SongEntity
import com.tan.domain.data.model.metadata.Line
import com.tan.domain.data.model.metadata.Lyrics
import com.tan.domain.data.model.streams.TimeLine
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.mediaservice.handler.MediaPlayerHandler
import com.tan.domain.mediaservice.handler.NowPlayingTrackState
import com.tan.domain.repository.LyricsCanvasRepository
import com.tan.domain.utils.Resource
import com.tan.gratify.viewModel.NowPlayingScreenData
import java.lang.reflect.Proxy
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.test.*

class LyricsHandlerPerformanceTest {
    companion object {
    private inline fun <reified T> fake(crossinline reply: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ -> reply(method.name) } as T
    }

    private class Fixture {
        val ui = Executors.newSingleThreadExecutor { task -> Thread(task, "lyrics-handler-ui") }.asCoroutineDispatcher()
        val job = SupervisorJob()
        val failure = AtomicReference<Throwable?>(null)
        val scope = CoroutineScope(job + ui + CoroutineExceptionHandler { _, error -> failure.set(error) })
        val translate = MutableStateFlow(DataStoreManager.FALSE)
        val translationStarted = CompletableDeferred<Unit>()
        val translationGate = CompletableDeferred<Unit>()
        val translationCalls = AtomicInteger(0)
        val primaryThread = AtomicReference<String>()
        val lyric = Lyrics(syncType = "LINE_SYNCED", lines = listOf(Line("59000", "52320", words = "Kau tahu mana urutan satu")))
        val song = SongEntity(videoId = "fixture-33x", artistName = listOf("Perunggu"), duration = "7:14",
            durationSeconds = 434, isAvailable = true, isExplicit = false, likeStatus = "INDIFFERENT",
            title = "33x", videoType = "MUSIC_VIDEO_TYPE_ATV", category = null, resultType = null)
        val state = MutableStateFlow<NowPlayingTrackState?>(NowPlayingTrackState.initial().copy(songEntity = song))
        val screen = MutableStateFlow(NowPlayingScreenData.initial())
        val store = fake<DataStoreManager> { name -> when (name) {
            "getLyricsProvider" -> flowOf(DataStoreManager.SIMPMUSIC)
            "getEnableTranslateLyric" -> translate
            "getSpotifyLyrics", "getUseAITranslation", "getHelpBuildLyricsDatabase" -> flowOf(DataStoreManager.FALSE)
            "getSpdc" -> flowOf("")
            "getTranslationLanguage" -> flowOf("id")
            else -> error("Unexpected preference: $name")
        } }
        val repository = fake<LyricsCanvasRepository> { name -> when (name) {
            "getSavedLyrics" -> flowOf(null)
            "getSimpMusicLyrics" -> {
                primaryThread.set(Thread.currentThread().name)
                flowOf(Resource.Success(lyric))
            }
            "getSimpMusicTranslatedLyrics" -> {
                translationCalls.incrementAndGet()
                flow {
                    translationStarted.complete(Unit)
                    translationGate.await()
                    emit(Resource.Success(lyric))
                }
            }
            "insertLyrics" -> Unit
            else -> error("Unexpected repository operation: $name")
        } }
        val handler = LyricsHandler(scope, store, repository,
            fake<MediaPlayerHandler> { error("Unexpected player call: $it") }, screen, state,
            MutableStateFlow(TimeLine(1000, 434000, 100, false)), {}, { "" })
        suspend fun start() = withContext(ui) { handler.getLyricsFromFormat(false, song, 434) }
        suspend fun settled() {
            withTimeout(5000) { screen.first { it.lyricsData != null && !it.lyricsLoading } }
            failure.get()?.let { throw it }
        }
        suspend fun close() { job.cancelAndJoin(); ui.close() }
    }

    @Test fun disabledTranslationMakesNoExtraRequestAndLookupLeavesUiThread(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.start(); fixture.settled()
            assertEquals(0, fixture.translationCalls.get())
            assertNotEquals("lyrics-handler-ui", fixture.primaryThread.get())
            assertEquals("Kau tahu mana urutan satu", fixture.screen.value.lyricsData!!.lyrics.lines!!.single().words)
        } finally { fixture.close() }
    }

    @Test fun disablingTranslationWhileARequestIsPendingPreventsLatePublication(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.translate.value = DataStoreManager.TRUE
            fixture.start()
            withTimeout(5000) { fixture.translationStarted.await() }
            fixture.translate.value = DataStoreManager.FALSE
            fixture.translationGate.complete(Unit)
            fixture.settled()
            assertEquals(1, fixture.translationCalls.get())
            assertNull(fixture.screen.value.lyricsData!!.translatedLyrics)
        } finally { fixture.close() }
    }
}
