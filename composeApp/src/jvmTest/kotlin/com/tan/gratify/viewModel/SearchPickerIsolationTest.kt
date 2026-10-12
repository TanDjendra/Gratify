package com.tan.gratify.viewModel

import androidx.lifecycle.viewModelScope
import com.tan.domain.data.entities.*
import com.tan.domain.data.model.searchResult.artists.ArtistsResult
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.mediaservice.handler.*
import com.tan.domain.repository.*
import com.tan.domain.utils.Resource
import com.tan.gratify.di.viewModelModule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.swing.Swing
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.lang.reflect.Proxy
import kotlin.test.*

class SearchPickerIsolationTest {
    private inline fun <reified T> fake(crossinline answer: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, m, args -> answer(m.name, args.orEmpty()) } as T

    @Test fun productionPickerDefinitionDoesNotOverwriteMainSearch(): Unit = runBlocking(Dispatchers.Swing) {
        val player = fake<MediaPlayerHandler> { method, _ -> when(method) {
            "getNowPlayingState" -> MutableStateFlow(NowPlayingTrackState.initial())
            "getControlState" -> MutableStateFlow(ControlState(false, false, RepeatState.None, false, false, false, false, 1f))
            else -> error(method)
        } }
        val preferences = fake<DataStoreManager> { method, _ -> when(method) {
            "getLocation" -> flowOf("ID"); "getString" -> flowOf<String?>(null); else -> error(method)
        } }
        val searches = fake<SearchRepository> { method, _ -> when(method) {
            "getSearchHistory" -> flowOf(emptyList<SearchHistory>())
            "getSearchDataArtist" -> flowOf(Resource.Success(arrayListOf<ArtistsResult>()))
            else -> error(method)
        } }
        val shared = fake<SharedPlaylistRepository> { method, _ -> when(method) {
            "getSharedPlaylists" -> flowOf(Result.success(emptyList<SharedPlaylist>())); else -> error(method)
        } }
        val users = fake<UserRepository> { method, args -> when(method) {
            "searchUsersPage" -> flowOf(listOf(UserProfile("user", args[0] as String))); else -> error(method)
        } }
        val app = startKoin { modules(viewModelModule, module {
            single<MediaPlayerHandler> { player }; single<DataStoreManager> { preferences }
            single<SearchRepository> { searches }; single<SharedPlaylistRepository> { shared }; single<UserRepository> { users }
        }) }
        val main = app.koin.get<SearchViewModel>()
        val picker = app.koin.get<SearchViewModel>(named("artist-picker"))
        try {
            assertNotSame(main, picker)
            main.searchUsers("Tan")
            withTimeout(2000) { while(main.searchScreenUIState.value !is SearchScreenUIState.Success) delay(10) }
            picker.fetchRecommendedArtists()
            withTimeout(2000) { while(picker.searchScreenUIState.value !is SearchScreenUIState.Success) delay(10) }
            assertEquals(SearchType.ARTISTS, picker.searchScreenState.value.searchType)
            assertEquals(SearchType.USERS, main.searchScreenState.value.searchType)
            assertEquals("Tan", main.searchScreenState.value.searchUsersResult.single().displayName)
            assertSame(main, app.koin.get<SearchViewModel>())
        } finally { main.viewModelScope.cancel(); picker.viewModelScope.cancel(); stopKoin() }
    }
}
