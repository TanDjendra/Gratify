package com.tan.gratify.viewModel

import com.tan.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class YouTubePageState(val items: List<YouTubeLibraryItem> = emptyList(),
    val loading: Boolean = false, val nextPageToken: String? = null, val error: String? = null,
    val initialized: Boolean = false)

/** Private metadata exists only for the currently connected Gratify user. */
class YouTubeCollectionsState(private val repository: YouTubeLibraryRepository,
    private val scope: CoroutineScope, private val playlistId: String? = null,
    private val expectedOwner: String? = null) {
    private val mutable = MutableStateFlow(YouTubePageState())
    val state = mutable.asStateFlow()
    private var request: Job? = null
    private var owner: String? = null
    init {
        scope.launch {
            repository.connection.collect { connection ->
                val connected = connection.status == YouTubeConnectionStatus.CONNECTED &&
                    (expectedOwner == null || connection.ownerId == expectedOwner)
                if (owner != connection.ownerId || !connected) {
                    request?.cancel(); mutable.value = YouTubePageState(); owner = connection.ownerId
                }
                if (connected && !mutable.value.initialized && request?.isActive != true) loadMore()
            }
        }
    }
    fun refreshConnection() { scope.launch { repository.refreshConnection() } }
    fun loadMore() {
        val connection = repository.connection.value
        if (request?.isActive == true || connection.status != YouTubeConnectionStatus.CONNECTED ||
            (expectedOwner != null && connection.ownerId != expectedOwner)) return
        val before = mutable.value
        if (before.initialized && before.nextPageToken == null && before.error == null) return
        val uid = connection.ownerId
        request = scope.launch {
            mutable.value = before.copy(loading = true, error = null)
            try {
                val page = if (playlistId == null) repository.playlists(before.nextPageToken)
                    else repository.tracks(playlistId, before.nextPageToken)
                if (repository.connection.value.ownerId != uid || repository.connection.value.status != YouTubeConnectionStatus.CONNECTED) return@launch
                mutable.value = YouTubePageState((before.items + page.items).distinctBy { it.id },
                    nextPageToken = page.nextPageToken, initialized = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (repository.connection.value.ownerId == uid && repository.connection.value.status == YouTubeConnectionStatus.CONNECTED)
                    mutable.value = before.copy(loading = false, error = "Koleksi YouTube belum dapat dimuat. Coba kembali.")
            }
        }
    }
}
