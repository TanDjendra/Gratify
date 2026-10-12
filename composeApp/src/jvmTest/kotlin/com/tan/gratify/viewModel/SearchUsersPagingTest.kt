package com.tan.gratify.viewModel

import androidx.lifecycle.viewModelScope
import com.tan.domain.data.entities.*
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.mediaservice.handler.*
import com.tan.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.swing.Swing
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.lang.reflect.Proxy
import kotlin.test.*

class SearchUsersPagingTest {
    private inline fun <reified T> fake(crossinline answer: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args -> answer(method.name, args.orEmpty()) } as T
    private class Fixture {
        var failure = false
        var gate: CompletableDeferred<Unit>? = null
        val requests = mutableListOf<Pair<String, Int>>()
    }
    private suspend fun await(condition: () -> Boolean) = withTimeout(3000) { while (!condition()) delay(10) }
    private suspend fun fixture(block: suspend (Fixture, SearchViewModel) -> Unit) {
        val f = Fixture()
        val player = fake<MediaPlayerHandler> { method, _ -> when (method) {
            "getNowPlayingState" -> MutableStateFlow(NowPlayingTrackState.initial())
            "getControlState" -> MutableStateFlow(ControlState(false, false, RepeatState.None, false, false, false, false, 1f))
            else -> error(method)
        } }
        startKoin { modules(module { single<MediaPlayerHandler> { player } }) }
        val users = fake<UserRepository> { method, args -> when (method) {
            "searchUsersPage" -> flow<List<UserProfile>> {
                val query = args[0] as String; val offset = args[1] as Int
                f.requests += query to offset; f.gate?.await()
                if (f.failure) error("offline")
                emit((0..44).map { UserProfile("$query-$it", "$query $it") }.drop(offset).take(args[2] as Int))
            }
            else -> error(method)
        } }
        val settings = fake<DataStoreManager> { method, _ -> when (method) {
            "getLocation" -> flowOf("US")
            "getString" -> flowOf<String?>(null)
            else -> error(method)
        } }
        val search = fake<SearchRepository> { method, _ -> when (method) {
            "getSearchHistory" -> flowOf(emptyList<SearchHistory>())
            else -> error(method)
        } }
        val shared = fake<SharedPlaylistRepository> { method, _ -> when (method) {
            "getSharedPlaylists" -> flowOf(Result.success(emptyList<SharedPlaylist>()))
            else -> error(method)
        } }
        val vm = SearchViewModel(settings, search, shared, users)
        try { block(f, vm) } finally { vm.viewModelScope.cancel(); stopKoin() }
    }
    @Test fun pagesAppendWithoutRepeatingTheInitialQuery(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f, vm ->
            vm.searchUsers("@Nara"); await { !vm.searchScreenState.value.isSearching }
            assertEquals(40, vm.searchScreenState.value.searchUsersResult.size)
            vm.loadMoreUsers(); await { vm.searchScreenState.value.searchUsersResult.size == 45 }
            assertFalse(vm.searchScreenState.value.usersMore); assertEquals(listOf("Nara" to 0, "Nara" to 40), f.requests)
        }
    }
    @Test fun pageFailureKeepsExistingResultsAndRetriesTheSameOffset(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f, vm ->
            vm.searchUsers("Nara"); await { !vm.searchScreenState.value.isSearching }
            f.failure = true; vm.loadMoreUsers(); await { vm.searchScreenState.value.usersPageError != null }
            assertEquals(40, vm.searchScreenState.value.searchUsersResult.size)
            f.failure = false; vm.loadMoreUsers(); await { vm.searchScreenState.value.searchUsersResult.size == 45 }
            assertEquals(listOf(0, 40, 40), f.requests.map { it.second })
        }
    }
    @Test fun firstPageFailureUsesTheErrorState(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f, vm ->
            f.failure = true; vm.searchUsers("Nara"); await { !vm.searchScreenState.value.isSearching }
            assertTrue(vm.searchScreenUIState.value is SearchScreenUIState.Error)
        }
    }
    @Test fun oldPageCannotAppendToANewQuery(): Unit = runBlocking(Dispatchers.Swing) {
        fixture { f, vm ->
            vm.searchUsers("Nara"); await { !vm.searchScreenState.value.isSearching }
            f.gate = CompletableDeferred(); val gate = f.gate!!; vm.loadMoreUsers()
            await { f.requests.size == 2 }
            f.gate = null; vm.searchUsers("Bima"); await { !vm.searchScreenState.value.isSearching }
            gate.complete(Unit); delay(30)
            assertTrue(vm.searchScreenState.value.searchUsersResult.all { it.id.startsWith("Bima-") })
            assertEquals(40, vm.searchScreenState.value.searchUsersResult.size)
        }
    }
}
