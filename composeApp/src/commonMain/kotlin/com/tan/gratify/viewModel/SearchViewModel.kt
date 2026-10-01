package com.tan.gratify.viewModel

import androidx.lifecycle.viewModelScope
import com.tan.common.SELECTED_LANGUAGE
import com.tan.domain.data.entities.SearchHistory
import com.tan.domain.data.model.searchResult.albums.AlbumsResult
import com.tan.domain.data.model.searchResult.artists.ArtistsResult
import com.tan.domain.data.model.searchResult.playlists.PlaylistsResult
import com.tan.domain.data.model.searchResult.songs.SongsResult
import com.tan.domain.data.model.searchResult.videos.VideosResult
import com.tan.domain.data.type.SearchResultType
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.repository.SearchRepository
import com.tan.domain.repository.SharedPlaylistRepository
import com.tan.domain.data.entities.SharedPlaylist
import com.tan.domain.data.entities.UserProfile
import com.tan.domain.repository.UserRepository
import com.tan.domain.utils.Resource
import com.tan.domain.utils.toQueryList
import com.tan.logger.Logger
import com.tan.gratify.viewModel.base.BaseViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.StringResource
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.albums
import gratify.composeapp.generated.resources.all
import gratify.composeapp.generated.resources.artists
import gratify.composeapp.generated.resources.featured_playlists
import gratify.composeapp.generated.resources.playlists
import gratify.composeapp.generated.resources.podcasts
import gratify.composeapp.generated.resources.songs
import gratify.composeapp.generated.resources.videos
import gratify.composeapp.generated.resources.users

// State cho tìm kiếm
data class SearchScreenState(
    val searchType: SearchType = SearchType.ALL,
    val isSearching: Boolean = false,
    val searchAllResult: List<SearchResultType> = emptyList(),
    val searchSongsResult: List<SongsResult> = emptyList(),
    val searchVideosResult: List<VideosResult> = emptyList(),
    val searchAlbumsResult: List<AlbumsResult> = emptyList(),
    val searchArtistsResult: List<ArtistsResult> = emptyList(),
    val searchPlaylistsResult: List<PlaylistsResult> = emptyList(),
    val searchFeaturedPlaylistsResult: List<PlaylistsResult> = emptyList(),
    val searchPodcastsResult: List<PlaylistsResult> = emptyList(),
    val suggestQueries: List<String> = emptyList(),
    val suggestYTItems: List<SearchResultType> = emptyList(),
    val publicPlaylists: List<SharedPlaylist> = emptyList(),
    val searchUsersResult: List<UserProfile> = emptyList(),
)

// Loại tìm kiếm
enum class SearchType {
    ALL,
    SONGS,
    VIDEOS,
    ALBUMS,
    ARTISTS,
    PLAYLISTS,
    FEATURED_PLAYLISTS,
    PODCASTS,
    USERS,
}

fun SearchType.toStringRes(): StringResource =
    when (this) {
        SearchType.ALL -> Res.string.all
        SearchType.SONGS -> Res.string.songs
        SearchType.VIDEOS -> Res.string.videos
        SearchType.ALBUMS -> Res.string.albums
        SearchType.ARTISTS -> Res.string.artists
        SearchType.PLAYLISTS -> Res.string.playlists
        SearchType.FEATURED_PLAYLISTS -> Res.string.featured_playlists
        SearchType.PODCASTS -> Res.string.podcasts
        SearchType.USERS -> Res.string.users
    }

// UI state cho tìm kiếm
sealed class SearchScreenUIState {
    object Empty : SearchScreenUIState()

    object Loading : SearchScreenUIState()

    object Success : SearchScreenUIState()

    object Error : SearchScreenUIState()
}

class SearchViewModel(
    private val dataStoreManager: DataStoreManager,
    private val searchRepository: SearchRepository,
    private val sharedPlaylistRepository: SharedPlaylistRepository,
    private val userRepository: UserRepository,
) : BaseViewModel() {
    private val _searchScreenUIState = MutableStateFlow<SearchScreenUIState>(SearchScreenUIState.Empty)
    val searchScreenUIState: StateFlow<SearchScreenUIState> get() = _searchScreenUIState.asStateFlow()

    private val _searchScreenState = MutableStateFlow(SearchScreenState())
    val searchScreenState: StateFlow<SearchScreenState> get() = _searchScreenState.asStateFlow()

    private val _searchHistory: MutableStateFlow<List<String>> = MutableStateFlow(emptyList())
    val searchHistory: StateFlow<List<String>> get() = _searchHistory.asStateFlow()

    var regionCode: String? = null
    var language: String? = null

    private var searchJob: Job? = null
    private var suggestionJob: Job? = null
    private var publicPlaylistsJob: Job? = null
    private var requestId = 0

    init {
        viewModelScope.launch {
            regionCode = dataStoreManager.location.first()
            language = dataStoreManager.getString(SELECTED_LANGUAGE).first()
        }
        viewModelScope.launch {
            searchRepository.getSearchHistory().collect { values ->
                _searchHistory.value = values.toQueryList().reversed()
            }
        }
        getPublicPlaylists()
    }

    fun getPublicPlaylists() {
        if (publicPlaylistsJob?.isActive == true) return
        publicPlaylistsJob = viewModelScope.launch {
            try {
                sharedPlaylistRepository.getSharedPlaylists().collectLatest { result ->
                    result.getOrNull()?.let { playlists ->
                        _searchScreenState.update { it.copy(publicPlaylists = playlists) }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Logger.w(tag, "Public playlists could not load")
            }
        }
    }

    fun insertSearchHistory(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            searchRepository.insertSearchHistory(SearchHistory(query = query.trim())).collect {}
        }
    }

    fun deleteSearchHistory() {
        viewModelScope.launch { searchRepository.deleteSearchHistory() }
    }

    /** Only the latest query/filter may publish results; each request has a deadline. */
    private fun runSearch(type: SearchType, block: suspend () -> Unit) {
        val request = ++requestId
        searchJob?.cancel()
        _searchScreenUIState.value = SearchScreenUIState.Loading
        _searchScreenState.update { SearchScreenState(
            searchType = type, isSearching = true, publicPlaylists = it.publicPlaylists,
        ) }
        searchJob = viewModelScope.launch {
            try {
                withTimeout(20_000) { block() }
                if (request == requestId && _searchScreenUIState.value is SearchScreenUIState.Loading)
                    _searchScreenUIState.value = SearchScreenUIState.Success
            } catch (error: TimeoutCancellationException) {
                if (request == requestId && _searchScreenUIState.value !is SearchScreenUIState.Success)
                    _searchScreenUIState.value = SearchScreenUIState.Error
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (request == requestId) _searchScreenUIState.value = SearchScreenUIState.Error
            } finally {
                if (request == requestId) _searchScreenState.update { it.copy(isSearching = false) }
            }
        }
    }

    private suspend fun <T> collectCategory(
        flow: Flow<Resource<ArrayList<T>>>,
        update: (SearchScreenState, List<T>) -> SearchScreenState,
        optional: Boolean = false,
    ): Boolean {
        val result = try {
            withTimeoutOrNull(12_000) { flow.first() }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            null
        }
        currentCoroutineContext().ensureActive()
        if (result is Resource.Success) {
            _searchScreenState.update { state -> update(state, result.data.orEmpty()).withAllResults() }
            _searchScreenUIState.value = SearchScreenUIState.Success
            return true
        }
        if (!optional) _searchScreenUIState.value = SearchScreenUIState.Error
        return false
    }

    private fun SearchScreenState.withAllResults() = copy(
        searchAllResult = searchSongsResult.take(3) + searchArtistsResult.take(3) +
            searchAlbumsResult.take(3) + searchPlaylistsResult + searchVideosResult +
            searchFeaturedPlaylistsResult + searchPodcastsResult + searchSongsResult.drop(3) +
            searchArtistsResult.drop(3) + searchAlbumsResult.drop(3),
    )

    fun search(query: String, type: SearchType = _searchScreenState.value.searchType) {
        val text = query.trim()
        if (text.isEmpty()) { cancelSearch(); return }
        if (text.startsWith("@")) { searchUsers(text); return }
        when (type) {
            SearchType.ALL -> searchAll(text)
            SearchType.SONGS -> searchSongs(text)
            SearchType.VIDEOS -> searchVideos(text)
            SearchType.ALBUMS -> searchAlbums(text)
            SearchType.ARTISTS -> searchArtists(text)
            SearchType.PLAYLISTS -> searchPlaylists(text)
            SearchType.FEATURED_PLAYLISTS -> searchFeaturedPlaylist(text)
            SearchType.PODCASTS -> searchPodcast(text)
            SearchType.USERS -> searchUsers(text)
        }
    }

    fun searchSongs(query: String) = runSearch(SearchType.SONGS) {
        collectCategory(searchRepository.getSearchDataSong(query), { state, items -> state.copy(searchSongsResult = items) })
    }
    fun searchVideos(query: String) = runSearch(SearchType.VIDEOS) {
        collectCategory(searchRepository.getSearchDataVideo(query), { state, items -> state.copy(searchVideosResult = items) })
    }
    fun searchAlbums(query: String) = runSearch(SearchType.ALBUMS) {
        collectCategory(searchRepository.getSearchDataAlbum(query), { state, items -> state.copy(searchAlbumsResult = items) })
    }
    fun searchArtists(query: String) = runSearch(SearchType.ARTISTS) {
        collectCategory(searchRepository.getSearchDataArtist(query), { state, items -> state.copy(searchArtistsResult = items) })
    }
    fun searchPlaylists(query: String) = runSearch(SearchType.PLAYLISTS) {
        collectCategory(searchRepository.getSearchDataPlaylist(query), { state, items -> state.copy(searchPlaylistsResult = items) })
    }
    fun searchFeaturedPlaylist(query: String) = runSearch(SearchType.FEATURED_PLAYLISTS) {
        collectCategory(searchRepository.getSearchDataFeaturedPlaylist(query), { state, items -> state.copy(searchFeaturedPlaylistsResult = items) })
    }
    fun searchPodcast(query: String) = runSearch(SearchType.PODCASTS) {
        collectCategory(searchRepository.getSearchDataPodcast(query), { state, items -> state.copy(searchPodcastsResult = items) })
    }
    fun searchUsers(query: String) = runSearch(SearchType.USERS) {
        val users = userRepository.searchUsers(query.removePrefix("@").trim()).first()
        currentCoroutineContext().ensureActive()
        _searchScreenState.update { it.copy(searchUsersResult = users) }
    }

    fun searchAll(query: String) = runSearch(SearchType.ALL) {
        coroutineScope {
            val jobs = listOf(
                async { collectCategory(searchRepository.getSearchDataSong(query), { s, v -> s.copy(searchSongsResult = v) }, true) },
                async { collectCategory(searchRepository.getSearchDataArtist(query), { s, v -> s.copy(searchArtistsResult = v) }, true) },
                async { collectCategory(searchRepository.getSearchDataAlbum(query), { s, v -> s.copy(searchAlbumsResult = v) }, true) },
                async { collectCategory(searchRepository.getSearchDataPlaylist(query), { s, v -> s.copy(searchPlaylistsResult = v) }, true) },
                async { collectCategory(searchRepository.getSearchDataVideo(query), { s, v -> s.copy(searchVideosResult = v) }, true) },
                async { collectCategory(searchRepository.getSearchDataFeaturedPlaylist(query), { s, v -> s.copy(searchFeaturedPlaylistsResult = v) }, true) },
                async { collectCategory(searchRepository.getSearchDataPodcast(query), { s, v -> s.copy(searchPodcastsResult = v) }, true) },
            )
            if (jobs.awaitAll().none { it }) _searchScreenUIState.value = SearchScreenUIState.Error
        }
    }

    fun suggestQuery(query: String) {
        suggestionJob?.cancel()
        _searchScreenState.update { it.copy(suggestQueries = emptyList(), suggestYTItems = emptyList()) }
        if (query.isBlank()) return
        suggestionJob = viewModelScope.launch {
            delay(300)
            try {
                val result = withTimeoutOrNull(5_000) { searchRepository.getSuggestQuery(query).first() }
                currentCoroutineContext().ensureActive()
                if (result is Resource.Success) result.data?.let { suggestions ->
                    _searchScreenState.update { it.copy(suggestQueries = suggestions.queries, suggestYTItems = suggestions.recommendedItems) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Logger.w(tag, "Search suggestions could not load")
            }
        }
    }

    fun fetchRecommendedArtists() = runSearch(SearchType.ARTISTS) {
        coroutineScope {
            listOf("Pop Indonesia", "Indie Indonesia", "Band Indonesia").map { query -> async {
                collectCategory(searchRepository.getSearchDataArtist(query), { state, artists ->
                    state.copy(searchArtistsResult = (state.searchArtistsResult + artists).distinctBy { it.browseId })
                }, true)
            } }.awaitAll().let { success ->
                if (success.none { it }) _searchScreenUIState.value = SearchScreenUIState.Error
            }
        }
    }

    fun cancelSearch() {
        ++requestId
        searchJob?.cancel()
        _searchScreenState.update { it.copy(isSearching = false) }
    }

    fun setSearchType(searchType: SearchType) {
        _searchScreenState.update { it.copy(searchType = searchType) }
    }
}
