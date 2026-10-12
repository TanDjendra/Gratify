package com.tan.gratify.viewModel

import androidx.lifecycle.viewModelScope
import com.tan.domain.data.entities.*
import com.tan.domain.data.model.searchResult.SearchSuggestions
import com.tan.domain.data.model.searchResult.songs.SongsResult
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.mediaservice.handler.*
import com.tan.domain.repository.*
import com.tan.domain.utils.Resource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.swing.Swing
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.lang.reflect.Proxy
import kotlin.test.*

class SearchAuditTest {
    private inline fun <reified T> fake(crossinline answer: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args -> answer(method.name, args.orEmpty()) } as T
    private fun song(id: String) = SongsResult(null, null, null, null, null, null, false, "Song", emptyList(), id, id, null, "")
    private class Fixture {
        val history = MutableStateFlow(emptyList<SearchHistory>())
        val access = MutableStateFlow(SearchAccess("A"))
        var owner = "A"
        var songFlow: (String) -> Flow<Resource<ArrayList<SongsResult>>> = { flowOf(Resource.Success(arrayListOf())) }
        var suggestFlow: (String) -> Flow<Resource<SearchSuggestions>> = { flowOf(Resource.Success(SearchSuggestions(listOf(it), emptyList()))) }
        var failOtherCategories = false
        var deleteFails = false
        var sharedFails = false
        var sharedFlow: (() -> Flow<Result<List<SharedPlaylist>>>)? = null
        val requests = mutableListOf<Pair<String, String>>()
        val writes = mutableListOf<Pair<String?, String>>()
    }
    private suspend fun await(condition: () -> Boolean) = withTimeout(3500) { while (!condition()) delay(10) }
    private suspend fun fixture(block: suspend (Fixture, SearchViewModel) -> Unit) {
        val f = Fixture()
        val player = fake<MediaPlayerHandler> { method, _ -> when (method) {
            "getNowPlayingState" -> MutableStateFlow(NowPlayingTrackState.initial())
            "getControlState" -> MutableStateFlow(ControlState(false, false, RepeatState.None, false, false, false, false, 1f))
            else -> error(method)
        } }
        startKoin { modules(module { single<MediaPlayerHandler> { player } }) }
        val search = fake<SearchRepository> { method, args -> when (method) {
            "getSearchHistory" -> f.history
            "getSearchDataSong" -> f.songFlow(args[0] as String).onStart { f.requests += method to (args[0] as String) }
            "getSuggestQuery" -> f.suggestFlow(args[0] as String)
            "insertSearchHistoryForOwner" -> flow { f.writes += (args[1] as String?) to (args[0] as SearchHistory).query; f.history.value = listOf(args[0] as SearchHistory) + f.history.value.filterNot { it == args[0] }; emit(1L) }
            "deleteSearchHistoryForOwner" -> { if (f.deleteFails) error("offline"); f.history.value = if (args[1] == null) emptyList() else f.history.value.filterNot { it.query == args[1] }; Unit }
            else -> if (method.startsWith("getSearchData")) { f.requests += method to (args[0] as String)
                if (f.failOtherCategories) flowOf(Resource.Error<ArrayList<Any>>("offline")) else flowOf(Resource.Success(arrayListOf<Any>()))
            } else error(method)
        } }
        val settings = fake<DataStoreManager> { method, _ -> when (method) { "getLocation" -> flowOf("US"); "getString" -> flowOf<String?>(null); else -> error(method) } }
        val shared = fake<SharedPlaylistRepository> { method, _ -> when (method) { "getSharedPlaylists" -> f.sharedFlow?.invoke() ?: flowOf(if (f.sharedFails) Result.failure(IllegalStateException("offline")) else Result.success(emptyList<SharedPlaylist>())); else -> error(method) } }
        val users = fake<UserRepository> { method, args -> when (method) { "searchUsersPage" -> { f.requests += method to (args[0] as String); flowOf(emptyList<UserProfile>()) }; else -> error(method) } }
        val common = fake<CommonRepository> { method, _ -> when (method) { "notificationAccountOwner" -> f.owner; else -> error(method) } }
        val vm = SearchViewModel(settings, search, shared, users, common, f.access)
        try { await { vm.accountRevision.value == 1 && vm.searchScreenState.value.historyReady }; block(f, vm) }
        finally { vm.viewModelScope.cancel(); stopKoin() }
    }
    @Test fun resultsGrowWhileContinuationIsStillLoading(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f, vm ->
            val gate = CompletableDeferred<Unit>(); f.songFlow = { flow { emit(Resource.Success(arrayListOf(song("a")))); gate.await(); emit(Resource.Success(arrayListOf(song("a"), song("b")))) } }
            vm.search("Nara", SearchType.SONGS); await { vm.searchScreenState.value.searchSongsResult.size == 1 }
            assertTrue(vm.searchScreenState.value.isSearching); assertTrue(vm.searchScreenUIState.value is SearchScreenUIState.Success)
            gate.complete(Unit); await { !vm.searchScreenState.value.isSearching }; assertEquals(listOf("a", "b"), vm.searchScreenState.value.searchSongsResult.map { it.videoId })
        }
    }
    @Test fun partialFailureKeepsResultsAndAllFailureShowsError(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f, vm ->
            f.songFlow = { flowOf(Resource.Success(arrayListOf(song("a"))), Resource.Error("later failure")) }
            vm.searchSongs("Nara"); await { !vm.searchScreenState.value.isSearching }; assertEquals(1, vm.searchScreenState.value.searchSongsResult.size)
            assertTrue(vm.searchScreenState.value.incomplete); assertTrue(vm.searchScreenUIState.value is SearchScreenUIState.Success)
            f.songFlow = { flowOf(Resource.Error("offline")) }; f.failOtherCategories = true
            vm.searchAll("Nara"); await { !vm.searchScreenState.value.isSearching }; assertTrue(vm.searchScreenUIState.value is SearchScreenUIState.Error)
        }
    }
    @Test fun slowOldRequestCannotReturnAfterAFilterChange(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f, vm ->
            val gate = CompletableDeferred<Unit>(); f.songFlow = { flow { withContext(NonCancellable) { gate.await() }; emit(Resource.Success(arrayListOf(song("old")))) } }
            vm.searchSongs("old"); await { f.requests.isNotEmpty() }; vm.setSearchType(SearchType.ALBUMS)
            assertTrue(vm.searchScreenUIState.value is SearchScreenUIState.Empty); gate.complete(Unit); delay(80)
            assertEquals(SearchType.ALBUMS, vm.searchScreenState.value.searchType); assertTrue(vm.searchScreenState.value.searchAllResult.isEmpty())
        }
    }
    @Test fun blankAndAtOnlyQueriesNeverFetchPeopleAndExplicitMusicFilterIsRespected(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f, vm ->
            vm.search(" "); vm.searchUsers("@"); vm.searchUsers(" @ "); delay(40); assertTrue(f.requests.isEmpty())
            vm.search("@Nara", SearchType.SONGS); await { !vm.searchScreenState.value.isSearching }
            assertEquals(listOf("getSearchDataSong" to "@Nara"), f.requests)
            vm.search("@Nara", SearchType.ALL); await { !vm.searchScreenState.value.isSearching }; assertEquals("searchUsersPage" to "Nara", f.requests.last())
        }
    }
    @Test fun historyImmediatelyReflectsOwnedWritesDeleteAndClear(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f, vm ->
            vm.insertSearchHistory("  Nara  "); await { vm.searchHistory.value == listOf("Nara") }; assertEquals(listOf<Pair<String?, String>>("A" to "Nara"), f.writes)
            vm.insertSearchHistory("Ari"); await { vm.searchHistory.value == listOf("Ari", "Nara") }
            vm.deleteSearchHistory("Nara"); await { vm.searchHistory.value == listOf("Ari") }
            vm.deleteSearchHistory(); await { vm.searchHistory.value.isEmpty() && !vm.searchScreenState.value.historyBusy }
        }
    }
    @Test fun deleteFailureKeepsHistoryAndReleasesBusyState(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f, vm ->
            f.history.value = listOf(SearchHistory("Nara")); await { vm.searchHistory.value.isNotEmpty() }
            f.deleteFails = true; vm.deleteSearchHistory(); await { vm.searchScreenState.value.historyProblem }
            assertFalse(vm.searchScreenState.value.historyBusy); assertEquals(listOf("Nara"), vm.searchHistory.value)
            f.deleteFails = false; vm.deleteSearchHistory(); await { vm.searchHistory.value.isEmpty() }; assertFalse(vm.searchScreenState.value.historyProblem)
        }
    }
    @Test fun accountChangeClearsResultsSuggestionsAndHistoryBeforeNewOwnerLoads(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f, vm ->
            f.songFlow = { flowOf(Resource.Success(arrayListOf(song("private")))) }; vm.searchSongs("Nara"); await { !vm.searchScreenState.value.isSearching }
            f.history.value = listOf(SearchHistory("private")); await { vm.searchHistory.value.isNotEmpty() }
            f.access.value = SearchAccess(ready = false); await { vm.accountRevision.value == 2 }
            assertTrue(vm.searchHistory.value.isEmpty()); assertTrue(vm.searchScreenState.value.searchAllResult.isEmpty())
            vm.searchSongs("ignored"); assertTrue(vm.searchScreenUIState.value is SearchScreenUIState.Empty)
            f.owner = "B"; f.history.value = emptyList(); f.access.value = SearchAccess("B"); await { vm.accountRevision.value == 3 && vm.searchScreenState.value.historyReady }
            assertTrue(vm.searchHistory.value.isEmpty())
        }
    }
    @Test fun staleSuggestionsAreCancelledAndFailureDoesNotBlockSubmission(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f, vm ->
            val started = CompletableDeferred<Unit>(); val gate = CompletableDeferred<Unit>()
            f.suggestFlow = { flow { started.complete(Unit); withContext(NonCancellable) { gate.await() }; emit(Resource.Success(SearchSuggestions(listOf("old"), emptyList()))) } }
            vm.suggestQuery("old"); started.await(); vm.searchSongs("new"); await { !vm.searchScreenState.value.isSearching }; gate.complete(Unit); delay(80)
            assertTrue(vm.searchScreenState.value.suggestQueries.isEmpty()); assertFalse(vm.searchScreenState.value.suggestionLoading)
            f.suggestFlow = { flowOf(Resource.Error("offline")) }; vm.suggestQuery("new"); await { vm.searchScreenState.value.suggestionFailed }
            vm.searchSongs("new"); await { !vm.searchScreenState.value.isSearching }; assertTrue(vm.searchScreenUIState.value is SearchScreenUIState.Success)
        }
    }
    @Test fun suggestionsAreBoundedDeduplicatedAndClearedOnEmptyInput(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f, vm ->
            f.suggestFlow = { flowOf(Resource.Success(SearchSuggestions(listOf("", "a", "a") + (0..20).map { "$it" }, emptyList()))) }
            vm.suggestQuery("a"); await { vm.searchScreenState.value.suggestQueries.isNotEmpty() }
            assertEquals(8, vm.searchScreenState.value.suggestQueries.size); assertEquals("a", vm.searchScreenState.value.suggestQueries.first())
            vm.suggestQuery(""); assertTrue(vm.searchScreenState.value.suggestQueries.isEmpty()); assertFalse(vm.searchScreenState.value.suggestionLoading)
        }
    }
    @Test fun discoveryFailureIsDistinctFromEmptyAndCanBeRetried(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f, vm ->
            await { !vm.searchScreenState.value.publicPlaylistsLoading }; f.sharedFails = true; vm.getPublicPlaylists()
            await { vm.searchScreenState.value.publicPlaylistsProblem && !vm.searchScreenState.value.publicPlaylistsLoading }
            f.sharedFails = false; vm.getPublicPlaylists(); await { !vm.searchScreenState.value.publicPlaylistsLoading }
            assertFalse(vm.searchScreenState.value.publicPlaylistsProblem); assertTrue(vm.searchScreenState.value.publicPlaylists.isEmpty())
        }
    }
    @Test fun returningToDiscoveryRemovesHiddenPlaylistAndFailureDoesNotRestoreIt(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f, vm ->
            await { !vm.searchScreenState.value.publicPlaylistsLoading }
            val published = SharedPlaylist(id = "published", userId = "A", title = "Playlist")
            f.sharedFlow = { flowOf(Result.success(listOf(published))) }
            vm.getPublicPlaylists(refresh = true)
            await { vm.searchScreenState.value.publicPlaylists.size == 1 }
            val gate = CompletableDeferred<Unit>()
            f.sharedFlow = { flow { gate.await(); emit(Result.failure(IllegalStateException("offline"))) } }
            vm.getPublicPlaylists(refresh = true)
            assertTrue(vm.searchScreenState.value.publicPlaylists.isEmpty())
            gate.complete(Unit)
            await { !vm.searchScreenState.value.publicPlaylistsLoading }
            assertTrue(vm.searchScreenState.value.publicPlaylistsProblem)
            assertTrue(vm.searchScreenState.value.publicPlaylists.isEmpty())
            f.sharedFlow = { flowOf(Result.success(listOf(published.copy(isPublic = false)))) }
            vm.getPublicPlaylists(refresh = true)
            await { !vm.searchScreenState.value.publicPlaylistsLoading }
            assertTrue(vm.searchScreenState.value.publicPlaylists.isEmpty())
        }
    }
    @Test fun oldDiscoveryResponseCannotRestorePlaylistAfterPrivacyRefresh(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f, vm ->
            await { !vm.searchScreenState.value.publicPlaylistsLoading }
            val started = CompletableDeferred<Unit>(); val oldGate = CompletableDeferred<Unit>()
            f.sharedFlow = { flow {
                started.complete(Unit)
                withContext(NonCancellable) { oldGate.await() }
                emit(Result.success(listOf(SharedPlaylist(id = "hidden", userId = "A", title = "Hidden"))))
            } }
            vm.getPublicPlaylists(refresh = true); started.await()
            f.sharedFlow = { flowOf(Result.success(emptyList())) }
            vm.getPublicPlaylists(refresh = true)
            await { !vm.searchScreenState.value.publicPlaylistsLoading }
            oldGate.complete(Unit); delay(80)
            assertTrue(vm.searchScreenState.value.publicPlaylists.isEmpty())
            assertFalse(vm.searchScreenState.value.publicPlaylistsProblem)
        }
    }
}
