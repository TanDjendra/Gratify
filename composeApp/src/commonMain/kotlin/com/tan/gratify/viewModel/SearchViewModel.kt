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
import com.tan.domain.repository.CommonRepository
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
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
    val query: String = "",
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
    val publicPlaylistsLoading: Boolean = false,
    val publicPlaylistsProblem: Boolean = false,
    val usersMore: Boolean = false,
    val usersLoadingMore: Boolean = false,
    val usersPageError: String? = null,
    val searchUsersResult: List<UserProfile> = emptyList(),
    val incomplete: Boolean = false,
    val suggestionLoading: Boolean = false,
    val suggestionFailed: Boolean = false,
    val historyReady: Boolean = false,
    val historyProblem: Boolean = false,
    val historyBusy: Boolean = false,
)

data class SearchAccess(val owner: String? = null, val ready: Boolean = true)
private class SearchRequest(val id: Int) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<SearchRequest>
}

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
    private val commonRepository: CommonRepository? = null,
    access: Flow<SearchAccess> = flowOf(SearchAccess()),
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
    private var publicPlaylistsGeneration = 0
    private var requestId = 0
    private var usersPageJob: Job? = null
    private var usersQuery = ""
    private var usersOffset = 0
    private var historyJob: Job? = null
    private var historyWriteJob: Job? = null
    private var account = SearchAccess()
    private var suggestionId = 0
    private val _accountRevision = MutableStateFlow(0)
    val accountRevision = _accountRevision.asStateFlow()

    init {
        viewModelScope.launch {
            regionCode = dataStoreManager.location.first()
            language = dataStoreManager.getString(SELECTED_LANGUAGE).first()
        }
        observeHistory()
        viewModelScope.launch { access.distinctUntilChanged().collect { value ->
            if (value != account) {
                account = value; cancelSearch(); historyJob?.cancel(); historyWriteJob?.cancel(); publicPlaylistsJob?.cancel()
                _searchHistory.value = emptyList(); _searchScreenState.value = SearchScreenState()
                _accountRevision.value++
                if (value.ready) { observeHistory(); getPublicPlaylists() }
            }
        } }
        getPublicPlaylists()
    }

    private suspend fun owns(owner: SearchAccess): Boolean {
        if (account != owner || !owner.ready) return false
        val storedOwner = commonRepository?.notificationAccountOwner()
        return account == owner && (commonRepository == null || storedOwner == owner.owner)
    }

    fun observeHistory() {
        historyJob?.cancel(); val owner = account
        historyJob = viewModelScope.launch {
            try {
                searchRepository.getSearchHistory().collect { rows ->
                    if (owns(owner)) { _searchHistory.value = rows.toQueryList().distinct().take(30)
                        _searchScreenState.update { it.copy(historyReady = true, historyProblem = false) } }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (account == owner) _searchScreenState.update { it.copy(historyReady = false, historyProblem = true) } }
        }
    }

    fun getPublicPlaylists(refresh: Boolean = false) {
        if (!account.ready || (!refresh && publicPlaylistsJob?.isActive == true)) return
        publicPlaylistsJob?.cancel()
        val generation = ++publicPlaylistsGeneration
        val owner = account
        // Publication rights may have changed while Search was covered by another screen.
        _searchScreenState.update { it.copy(publicPlaylists = emptyList(), publicPlaylistsLoading = true, publicPlaylistsProblem = false) }
        publicPlaylistsJob = viewModelScope.launch {
            try {
                val result = withTimeout(10_000) { sharedPlaylistRepository.getSharedPlaylists().first() }
                if (owns(owner) && generation == publicPlaylistsGeneration) result.fold(
                    onSuccess = { playlists -> _searchScreenState.update { it.copy(publicPlaylists = playlists.filter { playlist -> playlist.isPublic }, publicPlaylistsProblem = false) } },
                    onFailure = { _searchScreenState.update { it.copy(publicPlaylistsProblem = true) } },
                )
            } catch (_: TimeoutCancellationException) {
                if (account == owner && generation == publicPlaylistsGeneration) _searchScreenState.update { it.copy(publicPlaylistsProblem = true) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Logger.w(tag, "Public playlists could not load")
                if (account == owner && generation == publicPlaylistsGeneration) _searchScreenState.update { it.copy(publicPlaylistsProblem = true) }
            } finally {
                if (account == owner && generation == publicPlaylistsGeneration) _searchScreenState.update { it.copy(publicPlaylistsLoading = false) }
            }
        }
    }

    fun insertSearchHistory(query: String) {
        if (query.isBlank() || !account.ready) return
        val owner = account
        viewModelScope.launch {
            try { if (owns(owner)) {
                val entry = SearchHistory(query = query.trim().take(200))
                (if (commonRepository == null) searchRepository.insertSearchHistory(entry)
                    else searchRepository.insertSearchHistoryForOwner(entry, owner.owner)).collect {}
            } } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (owner == account) _searchScreenState.update { it.copy(historyProblem = true) } }
        }
    }

    fun deleteSearchHistory(query: String? = null) {
        if (!account.ready || _searchScreenState.value.historyBusy) return
        val owner = account
        _searchScreenState.update { it.copy(historyBusy = true, historyProblem = false) }
        historyWriteJob = viewModelScope.launch {
            try { withTimeout(10_000) { check(owns(owner)); searchRepository.deleteSearchHistoryForOwner(owner.owner, query) } }
            catch (_: TimeoutCancellationException) { if (owner == account) _searchScreenState.update { it.copy(historyProblem = true) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (owner == account) _searchScreenState.update { it.copy(historyProblem = true) } }
            finally { if (owner == account) _searchScreenState.update { it.copy(historyBusy = false) } }
        }
    }

    /** Only the latest query/filter may publish results; each request has a deadline. */
    private fun runSearch(type: SearchType, query: String? = null, block: suspend () -> Unit) {
        if (!account.ready) return
        if (query != null && query.isBlank()) { cancelSearch(); setSearchType(type); return }
        suggestionJob?.cancel(); ++suggestionId
        val request = ++requestId
        searchJob?.cancel()
        usersPageJob?.cancel()
        _searchScreenUIState.value = SearchScreenUIState.Loading
        _searchScreenState.update { SearchScreenState(
            query = query?.trim().orEmpty(), searchType = type, isSearching = true, publicPlaylists = it.publicPlaylists,
            publicPlaylistsLoading = it.publicPlaylistsLoading, publicPlaylistsProblem = it.publicPlaylistsProblem,
            historyReady = it.historyReady, historyBusy = it.historyBusy, historyProblem = it.historyProblem,
        ) }
        searchJob = viewModelScope.launch {
            try {
                withTimeout(20_000) { withContext(SearchRequest(request)) { block() } }
                if (request == requestId && _searchScreenUIState.value is SearchScreenUIState.Loading)
                    _searchScreenUIState.value = SearchScreenUIState.Success
            } catch (error: TimeoutCancellationException) {
                if (request == requestId) {
                    _searchScreenState.update { it.copy(incomplete = true) }
                    if (_searchScreenUIState.value !is SearchScreenUIState.Success) _searchScreenUIState.value = SearchScreenUIState.Error
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (request == requestId) {
                    _searchScreenState.update { it.copy(incomplete = true) }
                    if (_searchScreenUIState.value !is SearchScreenUIState.Success) _searchScreenUIState.value = SearchScreenUIState.Error
                }
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
        var received = false
        val request = currentCoroutineContext()[SearchRequest]?.id ?: requestId
        val result = try {
            withTimeout(12_000) { flow.collect { result ->
                currentCoroutineContext().ensureActive()
                if (request == requestId) when (result) {
                    is Resource.Success -> {
                        _searchScreenState.update { state -> update(state, result.data.orEmpty()).withAllResults() }
                        _searchScreenUIState.value = SearchScreenUIState.Success; received = true
                    }
                    is Resource.Error -> _searchScreenState.update { it.copy(incomplete = true) }
                    else -> Unit
                }
            } }
            received
        } catch (_: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            if (request == requestId) _searchScreenState.update { it.copy(incomplete = true) }
            received
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            if (request == requestId) _searchScreenState.update { it.copy(incomplete = true) }; received
        }
        currentCoroutineContext().ensureActive()
        if (result) {
            return true
        }
        if (request != requestId) return false
        _searchScreenState.update { it.copy(incomplete = true) }
        if (!optional && _searchScreenUIState.value !is SearchScreenUIState.Success) _searchScreenUIState.value = SearchScreenUIState.Error
        return false
    }

    private fun SearchScreenState.withAllResults() = copy(
        searchAllResult = (searchSongsResult.take(3) + searchArtistsResult.take(3) +
            searchAlbumsResult.take(3) + searchPlaylistsResult + searchVideosResult +
            searchFeaturedPlaylistsResult + searchPodcastsResult + searchSongsResult.drop(3) +
            searchArtistsResult.drop(3) + searchAlbumsResult.drop(3)).distinctBy { it.searchIdentity() },
    )

    fun search(query: String, type: SearchType = _searchScreenState.value.searchType) {
        val text = query.trim()
        if (text.isEmpty()) { cancelSearch(); return }
        if (text.startsWith("@") && (type == SearchType.ALL || type == SearchType.USERS)) { searchUsers(text); return }
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

    fun searchSongs(query: String) = runSearch(SearchType.SONGS, query) {
        collectCategory(searchRepository.getSearchDataSong(query), { state, items -> state.copy(searchSongsResult = items) })
    }
    fun searchVideos(query: String) = runSearch(SearchType.VIDEOS, query) {
        collectCategory(searchRepository.getSearchDataVideo(query), { state, items -> state.copy(searchVideosResult = items) })
    }
    fun searchAlbums(query: String) = runSearch(SearchType.ALBUMS, query) {
        collectCategory(searchRepository.getSearchDataAlbum(query), { state, items -> state.copy(searchAlbumsResult = items) })
    }
    fun searchArtists(query: String) = runSearch(SearchType.ARTISTS, query) {
        collectCategory(searchRepository.getSearchDataArtist(query), { state, items -> state.copy(searchArtistsResult = items) })
    }
    fun searchPlaylists(query: String) = runSearch(SearchType.PLAYLISTS, query) {
        collectCategory(searchRepository.getSearchDataPlaylist(query), { state, items -> state.copy(searchPlaylistsResult = items) })
    }
    fun searchFeaturedPlaylist(query: String) = runSearch(SearchType.FEATURED_PLAYLISTS, query) {
        collectCategory(searchRepository.getSearchDataFeaturedPlaylist(query), { state, items -> state.copy(searchFeaturedPlaylistsResult = items) })
    }
    fun searchPodcast(query: String) = runSearch(SearchType.PODCASTS, query) {
        collectCategory(searchRepository.getSearchDataPodcast(query), { state, items -> state.copy(searchPodcastsResult = items) })
    }
    fun searchUsers(query: String) = runSearch(SearchType.USERS, query.trim().removePrefix("@").trim()) {
        usersQuery = query.trim().removePrefix("@").trim()
        usersOffset = 0
        val users = userRepository.searchUsersPage(usersQuery, 0, 40).first()
        currentCoroutineContext().ensureActive()
        usersOffset = users.size
        _searchScreenState.update { it.copy(searchUsersResult = users.distinctBy { user -> user.id }, usersMore = users.size == 40) }
    }

    fun loadMoreUsers() {
        if (usersPageJob?.isActive == true || !_searchScreenState.value.usersMore || _searchScreenState.value.searchType != SearchType.USERS) return
        val request = requestId
        val query = usersQuery
        val offset = usersOffset
        _searchScreenState.update { it.copy(usersLoadingMore = true, usersPageError = null) }
        usersPageJob = viewModelScope.launch {
            try {
                val users = withTimeout(20_000) { userRepository.searchUsersPage(query, offset, 40).first() }
                currentCoroutineContext().ensureActive()
                if (request != requestId) return@launch
                usersOffset += users.size
                _searchScreenState.update { it.copy(searchUsersResult = (it.searchUsersResult + users).distinctBy { user -> user.id }, usersMore = users.size == 40) }
            } catch (error: TimeoutCancellationException) {
                if (request == requestId) _searchScreenState.update { it.copy(usersPageError = "Akun berikutnya belum dapat dimuat.") }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if (request == requestId) _searchScreenState.update { it.copy(usersPageError = "Akun berikutnya belum dapat dimuat. Periksa koneksi.") }
            } finally {
                if (request == requestId) _searchScreenState.update { it.copy(usersLoadingMore = false) }
            }
        }
    }

    fun searchAll(query: String) = runSearch(SearchType.ALL, query) {
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
        suggestionJob?.cancel(); val request = ++suggestionId
        _searchScreenState.update { it.copy(suggestQueries = emptyList(), suggestYTItems = emptyList(), suggestionLoading = query.isNotBlank(), suggestionFailed = false) }
        if (query.isBlank()) return
        suggestionJob = viewModelScope.launch {
            delay(300)
            try {
                val result = withTimeoutOrNull(5_000) { searchRepository.getSuggestQuery(query).first() }
                currentCoroutineContext().ensureActive()
                if (request == suggestionId && result is Resource.Success) result.data?.let { suggestions ->
                    _searchScreenState.update { it.copy(suggestQueries = suggestions.queries.filter { value -> value.isNotBlank() }.distinct().take(8), suggestYTItems = suggestions.recommendedItems.take(8)) }
                }
                if (request == suggestionId && result !is Resource.Success) _searchScreenState.update { it.copy(suggestionFailed = true) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Logger.w(tag, "Search suggestions could not load")
                if (request == suggestionId) _searchScreenState.update { it.copy(suggestionFailed = true) }
            } finally {
                if (request == suggestionId) _searchScreenState.update { it.copy(suggestionLoading = false) }
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
        usersPageJob?.cancel()
        suggestionJob?.cancel(); ++suggestionId
        _searchScreenUIState.value = SearchScreenUIState.Empty
        _searchScreenState.update { SearchScreenState(searchType = it.searchType, publicPlaylists = it.publicPlaylists,
            publicPlaylistsLoading = it.publicPlaylistsLoading, publicPlaylistsProblem = it.publicPlaylistsProblem,
            historyReady = it.historyReady, historyProblem = it.historyProblem, historyBusy = it.historyBusy) }
    }

    fun setSearchType(searchType: SearchType) {
        if (searchType == _searchScreenState.value.searchType) return
        cancelSearch()
        _searchScreenState.update { it.copy(searchType = searchType) }
    }
}
