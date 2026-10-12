package com.tan.gratify.viewModel

import com.tan.domain.repository.HomeRepository
import com.tan.domain.repository.SearchRepository
import com.tan.domain.utils.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.serialization.builtins.serializer

/** Covers come from the requested YouTube Music category, with bounded visible-row requests. */
internal class BrowseArtworkLoader(
    private val scope: CoroutineScope,
    private val home: HomeRepository,
    private val search: SearchRepository,
    private val retryDelay: Duration = 30.seconds,
    private val cache: com.tan.data.cache.JsonContentCache? = null,
) {
    private val _covers = MutableStateFlow<Map<String, String>>(emptyMap())
    val covers = _covers.asStateFlow()
    private val pending = mutableSetOf<String>()
    private val failed = mutableMapOf<String, TimeMark>()
    private val permits = Semaphore(2)

    fun category(params: String) = request(params) {
        val result = home.getMoodData(params).first()
        (result as? Resource.Success)?.data?.items.orEmpty().asSequence()
            .flatMap { it.contents.asSequence() }
            .mapNotNull { it.thumbnails?.lastOrNull { thumbnail -> thumbnail.url.startsWith("https://") }?.url }
            .firstOrNull()
    }

    fun podcasts() = request("quick:podcasts") {
        val result = search.getSearchDataPodcast("podcast").first()
        (result as? Resource.Success)?.data.orEmpty().asSequence()
            .mapNotNull { it.thumbnails.lastOrNull { thumbnail -> thumbnail.url.startsWith("https://") }?.url }
            .firstOrNull()
    }

    private fun request(key: String, fetch: suspend () -> String?) {
        if (key.isBlank() || key in _covers.value || key in pending ||
            failed[key]?.elapsedNow()?.let { it < retryDelay } == true) return
        pending += key
        scope.launch {
            try {
                val disk = cache?.read("browse-cover-v1:$key", 7L * 24 * 60 * 60 * 1000, String.serializer())
                if (disk != null) { _covers.update { it + (key to disk) }; failed.remove(key); return@launch }
                val cover = permits.withPermit { withTimeout(12_000) { fetch() } }
                if (cover != null) {
                    _covers.update { it + (key to cover) }
                    failed.remove(key)
                    cache?.write("browse-cover-v1:$key", cover, String.serializer())
                } else failed[key] = TimeSource.Monotonic.markNow()
            } catch (_: TimeoutCancellationException) {
                failed[key] = TimeSource.Monotonic.markNow()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                failed[key] = TimeSource.Monotonic.markNow()
            } finally {
                pending -= key
            }
        }
    }
}
