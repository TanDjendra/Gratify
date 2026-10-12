package com.tan.data.repository

import com.tan.domain.data.entities.LocalPlaylistEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlin.test.*

class PlaylistArtworkFlowTest {
    private val cover = "https://test/storage/v1/object/public/playlist-covers/owner/playlist/image.jpg"
    private fun row(url: String? = cover, owner: String = "A") =
        LocalPlaylistEntity(id = 1, title = "Playlist", thumbnail = url, ownerEmail = owner)

    private suspend fun exercise(
        cached: suspend (String?) -> String? = { if (playlistCoverPath(it) == null) it else null },
        resolve: suspend (List<LocalPlaylistEntity>) -> List<LocalPlaylistEntity> = { rows -> rows.map { it.copy(thumbnail = "signed") } },
        block: suspend (Channel<List<LocalPlaylistEntity>>, Channel<List<LocalPlaylistEntity>>) -> Unit,
    ) = coroutineScope {
        val input = Channel<List<LocalPlaylistEntity>>(Channel.UNLIMITED)
        val output = Channel<List<LocalPlaylistEntity>>(Channel.UNLIMITED)
        val job = launch { input.receiveAsFlow().withPlaylistArtwork(cached, resolve).collect { output.send(it) } }
        try { withTimeout(5_000) { block(input, output) } }
        finally { job.cancelAndJoin(); input.close(); output.close() }
    }

    @Test fun cachedUploadIsVisibleOnFirstEmission(): Unit = runBlocking {
        exercise(cached = { "signed" }) { input, output ->
            input.send(listOf(row()))
            assertEquals("signed", output.receive().single().thumbnail)
        }
    }

    @Test fun unrelatedRowChangesKeepUploadVisibleDuringSigning(): Unit = runBlocking {
        val signing = Channel<Unit>(Channel.UNLIMITED)
        exercise(resolve = { rows -> signing.receive(); rows.map { it.copy(thumbnail = "signed") } }) { input, output ->
            val original = row()
            input.send(listOf(original))
            assertNull(output.receive().single().thumbnail) // Local data is available even while offline.
            signing.send(Unit)
            assertEquals("signed", output.receive().single().thumbnail)
            input.send(listOf(original.copy(title = "Renamed", downloadState = 1)))
            val pending = output.receive().single()
            assertEquals("Renamed", pending.title)
            assertEquals("signed", pending.thumbnail)
            signing.send(Unit)
        }
    }

    @Test fun identicalDatabaseInvalidationsDoNotRestartSigning(): Unit = runBlocking {
        var requests = 0
        exercise(resolve = { rows -> requests++; rows.map { it.copy(thumbnail = "signed") } }) { input, output ->
            val original = row()
            input.send(listOf(original)); output.receive(); output.receive()
            repeat(10) { input.send(listOf(original.copy())) }
            // A following changed row proves all preceding duplicate emissions were processed.
            input.send(listOf(original.copy(title = "Changed")))
            assertEquals("Changed", output.receive().single().title)
            input.send(emptyList())
            while (output.receive().isNotEmpty()) { }
            assertTrue(requests <= 2, "Duplicate rows triggered $requests signing requests")
        }
    }

    @Test fun changedOwnerOrCoverNeverBorrowsPreviousImage(): Unit = runBlocking {
        exercise { input, output ->
            val original = row()
            input.send(listOf(original)); output.receive(); output.receive()
            input.send(listOf(original.copy(ownerEmail = "B")))
            assertNull(output.receive().single().thumbnail)
            output.receive()
            input.send(listOf(original.copy(ownerEmail = "B", thumbnail = cover.replace("image.jpg", "new.jpg"))))
            assertNull(output.receive().single().thumbnail)
        }
    }

    @Test fun defaultArtworkStaysVisibleAndNeverNeedsSigning(): Unit = runBlocking {
        val systemCover = "https://i.ytimg.com/vi/song/mqdefault.jpg"
        exercise(resolve = { error("System artwork should not be signed") }) { input, output ->
            val original = row(systemCover)
            input.send(listOf(original))
            assertEquals(systemCover, output.receive().single().thumbnail)
            input.send(listOf(original.copy(title = "Updated")))
            assertEquals(systemCover, output.receive().single().thumbnail)
            input.send(emptyList())
            assertTrue(output.receive().isEmpty())
        }
    }

    @Test fun permissionFailureClearsPreviouslyDisplayedUpload(): Unit = runBlocking {
        var denied = false
        exercise(resolve = { rows -> rows.map { it.copy(thumbnail = if (denied) null else "signed") } }) { input, output ->
            val original = row()
            input.send(listOf(original)); output.receive(); output.receive()
            denied = true
            input.send(listOf(original.copy(title = "Changed")))
            assertEquals("signed", output.receive().single().thumbnail)
            assertNull(output.receive().single().thumbnail)
        }
    }
}
