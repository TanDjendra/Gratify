package com.tan.gratify.viewModel

import androidx.lifecycle.viewModelScope
import com.tan.domain.data.entities.*
import com.tan.domain.data.entities.analytics.PlaybackEventEntity
import com.tan.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.swing.Swing
import kotlinx.datetime.LocalDateTime
import java.lang.reflect.Proxy
import kotlin.test.*

class WrappedRegressionTest {
    private inline fun <reified T> fake(crossinline answer: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args -> answer(method.name, args.orEmpty()) } as T
    private suspend fun waitFor(test: () -> Boolean) = withTimeout(5000) { while (!test()) delay(10) }
    private fun song(id: String, name: String = "Same title", artists: List<String> = listOf("Nara"), artistIds: List<String> = listOf("artist")) =
        SongEntity(videoId = id, title = name, artistName = artists, artistId = artistIds, duration = "3:00", durationSeconds = 180,
            isAvailable = true, isExplicit = false, likeStatus = "INDIFFERENT", videoType = "MUSIC", category = null, resultType = null)
    private fun event(id: String, seconds: Long = 60, year: Int = 2026) = PlaybackEventEntity(videoId = id, listenedSecond = seconds,
        timestamp = LocalDateTime(year, 1, 1, 12, 0))
    private class Fixture {
        var rows = listOf<PlaybackEventEntity>()
        var songs = listOf<SongEntity>()
        var years = listOf(2025, 2026, 2030)
        var fails = false; var metadataFails = false
        val batches = mutableListOf<List<String>>()
        val queries = mutableListOf<Int>()
        var queryGate: Pair<Int, CompletableDeferred<Unit>>? = null
        var databaseOwner = "A"
        val access = MutableStateFlow(WrappedAccess("A"))
        val tracking = MutableStateFlow(true)
        var playlistGate: CompletableDeferred<Unit>? = null
        var playlistFails = false; var creates = 0
        var saved = emptyList<LocalPlaylistEntity>()
        var savedSongs = emptyList<SongEntity>()
    }
    private suspend fun fixture(setup: (Fixture) -> Unit = {}, block: suspend (Fixture, WrappedViewModel) -> Unit) {
        val f = Fixture().also(setup)
        val analytics = fake<AnalyticsRepository> { name, args -> when (name) {
            "getPlaybackYears" -> flowOf<List<Int>>(f.years)
            "getPlaybackEventsInRange" -> {
                val start = args[0] as LocalDateTime; val end = args[1] as LocalDateTime; val snapshot = f.rows
                flow<List<PlaybackEventEntity>> { f.queries += start.year; f.queryGate?.takeIf { it.first == start.year }?.second?.await()
                    if (f.fails) error("synthetic read failure"); emit(snapshot.filter { it.timestamp >= start && it.timestamp < end }) }
            }
            else -> error("Unexpected analytics $name")
        } }
        val songs = fake<SongRepository> { name, args -> when (name) {
            "getSongsByListVideoId" -> {
                @Suppress("UNCHECKED_CAST") val ids = args[0] as List<String>
                flow<List<SongEntity>> { f.batches += ids; if (f.metadataFails) error("synthetic metadata failure"); emit(f.songs.filter { it.videoId in ids }) }
            }
            else -> error("Unexpected songs $name")
        } }
        val common = fake<CommonRepository> { name, _ -> when (name) {
            "notificationAccountOwner" -> f.databaseOwner
            else -> error("Unexpected owner $name")
        } }
        val playlists = fake<LocalPlaylistRepository> { name, args -> when (name) {
            "getAllLocalPlaylists" -> flow { f.playlistGate?.await(); if (f.playlistFails) error("synthetic playlist failure"); emit(f.saved) }
            "createLocalPlaylistWithSongsForOwner" -> {
                check(f.databaseOwner == args[2]) { "owner changed" }; f.creates++
                @Suppress("UNCHECKED_CAST") val tracks = args[1] as List<SongEntity>
                f.savedSongs = tracks
                val row = (args[0] as LocalPlaylistEntity).copy(id = 42, tracks = tracks.map { it.videoId })
                f.saved = f.saved + row; 42L
            }
            else -> error("Unexpected playlists $name")
        } }
        val vm = WrappedViewModel(analytics, songs, common, playlists, f.tracking, f.access) { LocalDateTime(2026, 10, 8, 12, 0) }
        try { block(f, vm) } finally { vm.viewModelScope.cancel() }
    }

    @Test fun keepsSameTitleSongsSeparateAndRanksByQualifiedPlays(): Unit = runBlocking(Dispatchers.Swing) {
        fixture({ f -> f.rows = listOf(event("a", 60), event("a", 60), event("b", 500), event("short", 29)); f.songs = listOf(song("a"), song("b")) }) { _, vm ->
            waitFor { !vm.state.value.loading }
            assertEquals(listOf("a", "b"), vm.state.value.tracks.map { it.id }); assertEquals(3, vm.state.value.summary.plays)
            assertEquals(649L, vm.state.value.summary.totalSeconds); assertTrue(vm.state.value.metadataIncomplete)
            assertEquals(listOf(2026, 2025), vm.state.value.years)
        }
    }
    @Test fun collaborationCreditsEachArtistOnceAndArtistsRankByTime(): Unit = runBlocking(Dispatchers.Swing) {
        fixture({ f -> f.rows = listOf(event("a", 300), event("b", 60), event("b", 60))
            f.songs = listOf(song("a", artists = listOf("Nara", "Nara", "Ari"), artistIds = listOf("n", "n", "a")),
                song("b", artists = listOf("Other"), artistIds = listOf("o"))) }) { _, vm ->
            waitFor { !vm.state.value.loading }
            assertEquals(listOf("a", "n", "o"), vm.state.value.artists.map { it.id })
            assertEquals(300L, vm.state.value.artists.first { it.id == "n" }.seconds)
            assertEquals(1, vm.state.value.artists.first { it.id == "n" }.plays)
        }
    }
    @Test fun readsMetadataInBoundedBatchesAndSavesOnlyTopFifty(): Unit = runBlocking(Dispatchers.Swing) {
        fixture({ f -> f.rows = (1..451).map { event("song-$it") }; f.songs = (1..451).map { song("song-$it") } }) { f, vm ->
            waitFor { !vm.state.value.loading }; assertEquals(listOf(200, 200, 51), f.batches.map { it.size })
            assertEquals(451, vm.state.value.summary.uniqueTracks); vm.savePlaylist(); waitFor { vm.state.value.playlistId != null }
            assertEquals(50, f.savedSongs.size); assertEquals(50, f.savedSongs.map { it.videoId }.distinct().size)
        }
    }
    @Test fun metadataFailurePreservesListeningAndCanBeRetried(): Unit = runBlocking(Dispatchers.Swing) {
        fixture({ f -> f.rows = listOf(event("a")); f.songs = listOf(song("a")); f.metadataFails = true }) { f, vm ->
            waitFor { !vm.state.value.loading }; assertFalse(vm.state.value.error); assertEquals(60L, vm.state.value.summary.totalSeconds)
            assertTrue(vm.state.value.metadataIncomplete); assertFalse(vm.state.value.playlistAvailable)
            f.metadataFails = false; vm.load(2026); waitFor { !vm.state.value.loading }
            assertFalse(vm.state.value.metadataIncomplete); assertTrue(vm.state.value.playlistAvailable)
        }
    }
    @Test fun missingTopSongMetadataDoesNotHideAvailablePlaylistSongs(): Unit = runBlocking(Dispatchers.Swing) {
        fixture({ f -> f.rows = (1..55).map { event("missing-$it") } + event("z-known"); f.songs = listOf(song("z-known")) }) { f, vm ->
            waitFor { !vm.state.value.loading }; assertTrue(vm.state.value.metadataIncomplete); assertTrue(vm.state.value.playlistAvailable)
            vm.savePlaylist(); waitFor { vm.state.value.playlistId != null }; assertEquals(listOf("z-known"), f.savedSongs.map { it.videoId })
            assertEquals(56, vm.state.value.summary.uniqueTracks)
        }
    }
    @Test fun readFailureIsVisibleAndRetryRecovers(): Unit = runBlocking(Dispatchers.Swing) {
        fixture({ it.fails = true }) { f, vm ->
            waitFor { !vm.state.value.loading }; assertTrue(vm.state.value.error)
            f.fails = false; f.rows = listOf(event("a")); vm.load(2026); waitFor { !vm.state.value.loading }
            assertFalse(vm.state.value.error); assertEquals(60L, vm.state.value.summary.totalSeconds)
        }
    }
    @Test fun slowerPreviousYearCannotReplaceNewSelection(): Unit = runBlocking(Dispatchers.Swing) {
        fixture({ f -> f.rows = listOf(event("a"), event("old", 600, 2025)) }) { f, vm ->
            waitFor { !vm.state.value.loading }; val gate = CompletableDeferred<Unit>(); f.queryGate = 2025 to gate
            vm.load(2025); waitFor { 2025 in f.queries }; vm.load(2026); waitFor { !vm.state.value.loading }
            gate.complete(Unit); delay(50); assertEquals(2026, vm.state.value.year); assertEquals(60L, vm.state.value.summary.totalSeconds)
            vm.load(2030); assertEquals(2026, vm.state.value.year)
        }
    }
    @Test fun changingAccountClearsHistoryAndPlaylistActions(): Unit = runBlocking(Dispatchers.Swing) {
        fixture({ f -> f.rows = listOf(event("a")); f.songs = listOf(song("a")) }) { f, vm ->
            waitFor { !vm.state.value.loading }; assertTrue(vm.state.value.playlistAvailable)
            f.access.value = WrappedAccess(ready = false, loading = true)
            waitFor { vm.state.value.loading && vm.state.value.summary.totalSeconds == 0L }
            vm.savePlaylist(); assertEquals(0, f.creates)
            f.rows = emptyList(); f.databaseOwner = "B"; f.access.value = WrappedAccess("B")
            waitFor { !vm.state.value.loading }; assertEquals(0L, vm.state.value.summary.totalSeconds); assertFalse(vm.state.value.playlistAvailable)
        }
    }
    @Test fun recordingPreferenceChangesWithoutReload(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f, vm -> waitFor { !vm.state.value.loading }; assertTrue(vm.state.value.trackingEnabled)
            f.tracking.value = false; waitFor { !vm.state.value.trackingEnabled } }
    }
    @Test fun playlistDoubleTapSavesOnceAndExactRetryReusesExistingPlaylist(): Unit = runBlocking(Dispatchers.Swing) {
        fixture({ f -> f.rows = listOf(event("a")); f.songs = listOf(song("a")) }) { f, vm ->
            waitFor { !vm.state.value.loading }; f.playlistGate = CompletableDeferred()
            vm.savePlaylist(); vm.savePlaylist(); assertTrue(vm.state.value.savingPlaylist)
            f.playlistGate!!.complete(Unit); waitFor { vm.state.value.playlistId != null }; assertEquals(1, f.creates)
            vm.load(2026); waitFor { !vm.state.value.loading }; vm.savePlaylist(); waitFor { vm.state.value.playlistId != null }
            assertEquals(1, f.creates); assertEquals(42L, vm.state.value.playlistId)
        }
    }
    @Test fun failedPlaylistSaveHasRetryAndDoesNotOverwriteDifferentTracks(): Unit = runBlocking(Dispatchers.Swing) {
        fixture({ f -> f.rows = listOf(event("a")); f.songs = listOf(song("a")); f.playlistFails = true
            f.saved = listOf(LocalPlaylistEntity(title = "Gratify Wrapped 2026", tracks = listOf("other"), downloadedAt = null)) }) { f, vm ->
            waitFor { !vm.state.value.loading }; vm.savePlaylist(); waitFor { !vm.state.value.savingPlaylist }
            assertTrue(vm.state.value.playlistError); assertNull(vm.state.value.playlistId)
            f.playlistFails = false; vm.savePlaylist(); waitFor { vm.state.value.playlistId != null }
            assertFalse(vm.state.value.playlistError); assertEquals(2, f.saved.size); assertEquals(listOf("other"), f.saved.first().tracks)
        }
    }
    @Test fun switchedOwnerRejectsPlaylistWriteWhileReadIsInFlight(): Unit = runBlocking(Dispatchers.Swing) {
        fixture({ f -> f.rows = listOf(event("a")); f.songs = listOf(song("a")) }) { f, vm ->
            waitFor { !vm.state.value.loading }; f.playlistGate = CompletableDeferred(); vm.savePlaylist()
            yield(); f.databaseOwner = "B"; f.rows = emptyList(); f.access.value = WrappedAccess("B")
            waitFor { !vm.state.value.loading }; f.playlistGate!!.complete(Unit); delay(50)
            assertEquals(0, f.creates); assertNull(vm.state.value.playlistId); assertFalse(vm.state.value.savingPlaylist)
        }
    }
}
