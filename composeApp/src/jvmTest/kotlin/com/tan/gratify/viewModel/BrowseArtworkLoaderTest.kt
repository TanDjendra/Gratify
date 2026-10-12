package com.tan.gratify.viewModel

import com.tan.domain.data.model.mood.moodmoments.Content
import com.tan.domain.data.model.mood.moodmoments.Item
import com.tan.domain.data.model.mood.moodmoments.MoodsMomentObject
import com.tan.domain.data.model.searchResult.playlists.PlaylistsResult
import com.tan.domain.data.model.searchResult.songs.Thumbnail
import com.tan.domain.repository.HomeRepository
import com.tan.domain.repository.SearchRepository
import com.tan.domain.utils.Resource
import java.lang.reflect.Proxy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.test.*
import kotlin.time.Duration
import okio.Path.Companion.toPath

class BrowseArtworkLoaderTest {
    @Test fun reopeningSearchUsesPersistedCoverMetadata(): Unit = runBlocking {
        val directory = java.nio.file.Files.createTempDirectory("gratify-browse-cover")
        var requests = 0
        val home = fake<HomeRepository> { _, args -> requests++; flowOf(Resource.Success(mood(args[0] as String))) }
        try {
            val path = directory.toString().toPath()
            val first = BrowseArtworkLoader(this, home, unusedSearch, cache = com.tan.data.cache.JsonContentCache({ path }))
            first.category("jazz"); await { "jazz" in first.covers.value }
            // Wait for the first loader's disk write, not just its visible state update.
            await { directory.toFile().listFiles().orEmpty().any { it.extension == "json" } }
            val reopened = BrowseArtworkLoader(this, home, unusedSearch, cache = com.tan.data.cache.JsonContentCache({ path }))
            reopened.category("jazz"); await { "jazz" in reopened.covers.value }
            assertEquals(first.covers.value["jazz"], reopened.covers.value["jazz"])
            assertEquals(1, requests)
        } finally { directory.toFile().deleteRecursively() }
    }
    private inline fun <reified T> fake(crossinline answer: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            if (method.name == "toString") "ArtworkTestRepository" else answer(method.name, args.orEmpty())
        } as T
    private fun mood(params: String) = MoodsMomentObject("", params, listOf(Item(listOf(
        Content("no-art", "", listOf(Thumbnail(1, "", 1)), "Missing cover"),
        Content(params, "", listOf(Thumbnail(64, "https://lh3.googleusercontent.com/$params-small", 64),
            Thumbnail(512, "https://lh3.googleusercontent.com/$params-large", 512)), "Actual category playlist"),
    ), "Playlists")), params)
    private val unusedSearch = fake<SearchRepository> { method, _ -> error(method) }
    private suspend fun await(block: () -> Boolean) = withTimeout(2000) { while (!block()) delay(5) }

    @Test fun categoryUsesItsOwnMetadataAndDeduplicatesPendingAndCachedRequests(): Unit = runBlocking {
        val requests = mutableListOf<String>(); val gate = CompletableDeferred<Unit>()
        val home = fake<HomeRepository> { method, args ->
            check(method == "getMoodData")
            flow { val params = args[0] as String; requests += params; gate.await(); emit(Resource.Success(mood(params))) }
        }
        val loader = BrowseArtworkLoader(this, home, unusedSearch)
        loader.category("jazz"); loader.category("jazz"); loader.category("pop"); loader.category("")
        await { requests.size == 2 }; gate.complete(Unit)
        await { loader.covers.value.size == 2 }
        assertEquals("https://lh3.googleusercontent.com/jazz-large", loader.covers.value["jazz"])
        assertEquals("https://lh3.googleusercontent.com/pop-large", loader.covers.value["pop"])
        loader.category("jazz"); delay(10); assertEquals(listOf("jazz", "pop"), requests)
    }

    @Test fun visibleCategoriesNeverFetchMoreThanTwoAtOnce(): Unit = runBlocking {
        var running = 0; var peak = 0; val gate = CompletableDeferred<Unit>()
        val home = fake<HomeRepository> { _, args -> flow {
            running++; peak = maxOf(peak, running)
            try { gate.await(); emit(Resource.Success(mood(args[0] as String))) } finally { running-- }
        } }
        val loader = BrowseArtworkLoader(this, home, unusedSearch)
        (1..8).forEach { loader.category("category-$it") }
        await { running == 2 }; assertEquals(2, peak)
        gate.complete(Unit); await { loader.covers.value.size == 8 }; assertEquals(2, peak)
    }

    @Test fun offlineFailureCanRetryAndCancelledScreenCannotPublishStaleCover(): Unit = runBlocking {
        var offline = true; val gate = CompletableDeferred<Unit>(); var calls = 0
        val home = fake<HomeRepository> { _, args -> flow {
            calls++
            if (offline) error("offline")
            if (args[0] == "slow") gate.await()
            emit(Resource.Success(mood(args[0] as String)))
        } }
        val screen = CoroutineScope(coroutineContext + Job())
        val loader = BrowseArtworkLoader(screen, home, unusedSearch, Duration.ZERO)
        try {
            loader.category("pop"); await { calls == 1 }; delay(10); assertTrue(loader.covers.value.isEmpty())
            offline = false; loader.category("pop"); await { "pop" in loader.covers.value }
            loader.category("slow"); await { calls == 3 }; screen.cancel(); gate.complete(Unit); delay(20)
            assertFalse("slow" in loader.covers.value)
        } finally { screen.cancel() }
    }

    @Test fun podcastShortcutUsesActualPodcastResultArtwork(): Unit = runBlocking {
        val home = fake<HomeRepository> { method, _ -> error(method) }
        var query = ""
        val search = fake<SearchRepository> { method, args ->
            check(method == "getSearchDataPodcast"); query = args[0] as String
            flowOf(Resource.Success(arrayListOf(PlaylistsResult("Creator", "podcast-id", "Podcast", "10", "Podcast",
                listOf(Thumbnail(512, "https://yt3.googleusercontent.com/podcast-cover", 512)), "Podcast title"))))
        }
        val loader = BrowseArtworkLoader(this, home, search)
        loader.podcasts(); await { loader.covers.value.isNotEmpty() }
        assertEquals("podcast", query)
        assertEquals("https://yt3.googleusercontent.com/podcast-cover", loader.covers.value["quick:podcasts"])
    }
}
