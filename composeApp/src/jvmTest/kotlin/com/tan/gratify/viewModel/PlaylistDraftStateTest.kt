package com.tan.gratify.viewModel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.tan.domain.data.model.browse.album.Track
import com.tan.domain.mediaservice.handler.*
import com.tan.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.swing.Swing
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.lang.reflect.Proxy
import kotlin.test.*

class PlaylistDraftStateTest {
    private inline fun <reified T> fake(crossinline answer: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ -> answer(method.name) } as T
    private val track = Track(null, null, "3:00", 180, true, false, null, null,
        "Pilihan pengguna", "video", null, null, null, null)

    private suspend fun fixture(block: suspend (create: (SavedStateHandle) -> AddSongsToPlaylistViewModel,
        saves: () -> Int) -> Unit) {
        var saveCount = 0
        val player = fake<MediaPlayerHandler> { when (it) {
            "getNowPlayingState" -> MutableStateFlow(NowPlayingTrackState.initial())
            "getControlState" -> MutableStateFlow(ControlState(false, false, RepeatState.None, false, false, false, false, 1f))
            else -> error(it)
        } }
        val songs = fake<SongRepository> { when (it) { "getRecentSong" -> emptyList<Any>(); else -> error(it) } }
        val searches = fake<SearchRepository> { error(it) }
        val playlists = fake<LocalPlaylistRepository> { when (it) {
            "createLocalPlaylistWithSongs" -> { saveCount++; 123L }
            else -> error(it)
        } }
        val models = mutableListOf<AddSongsToPlaylistViewModel>()
        startKoin { modules(module { single<MediaPlayerHandler> { player } }) }
        try {
            block({ saved -> AddSongsToPlaylistViewModel(searches, songs, playlists, saved).also(models::add) }, { saveCount })
        } finally { models.forEach { it.viewModelScope.cancel() }; stopKoin() }
    }

    @Test fun selectedSongsSurviveSavedStateRecreationWithoutDuplicates(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { create, _ ->
            val saved = SavedStateHandle()
            val first = create(saved)
            first.addTrack(track); first.addTrack(track)
            val restored = create(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
            assertEquals(listOf(track), restored.uiState.value.selectedTracks)
            assertTrue(restored.canSave())
            restored.removeTrack(track.videoId)
            assertTrue(restored.uiState.value.selectedTracks.isEmpty())
        }
    }

    @Test fun fastRepeatedSaveAndRestorationCannotCreateSecondPlaylist(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { create, saves ->
            val saved = SavedStateHandle()
            val first = create(saved)
            first.addTrack(track)
            first.savePlaylist("Audit"); first.savePlaylist("Audit")
            withTimeout(2000) { while(first.uiState.value.createdPlaylistId == null) delay(10) }
            assertEquals(1, saves())
            val restored = create(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
            assertEquals(123L, restored.uiState.value.createdPlaylistId)
            assertFalse(restored.canSave())
            restored.savePlaylist("Audit")
            assertEquals(1, saves())
        }
    }

    @Test fun invalidSavedDraftDoesNotCrash(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { create, _ ->
            val vm = create(SavedStateHandle(mapOf("playlist_tracks" to "corrupt")))
            assertTrue(vm.uiState.value.selectedTracks.isEmpty())
        }
    }
}
