package com.tan.gratify.viewModel

import androidx.lifecycle.viewModelScope
import com.tan.domain.data.entities.SongEntity
import com.tan.domain.data.model.home.HomeItem
import com.tan.domain.data.model.home.chart.Chart
import com.tan.domain.data.model.mood.Mood
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.manager.DataStoreManager.Values.TRUE
import com.tan.domain.repository.HomeRepository
import com.tan.domain.repository.ArtistRepository
import com.tan.domain.utils.Resource
import com.tan.logger.Logger
import com.tan.gratify.viewModel.base.BaseViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.music_video
import gratify.composeapp.generated.resources.new_release
import gratify.composeapp.generated.resources.song
import gratify.composeapp.generated.resources.view_count

@OptIn(FlowPreview::class)
class HomeViewModel(
    private val dataStoreManager: DataStoreManager,
    private val homeRepository: HomeRepository,
    private val artistRepository: ArtistRepository,
    private val songRepository: com.tan.domain.repository.SongRepository,
    private val playlistRepository: com.tan.domain.repository.PlaylistRepository,
    private val albumRepository: com.tan.domain.repository.AlbumRepository,
) : BaseViewModel() {
    private val _homeItemList: MutableStateFlow<List<HomeItem>> =
        MutableStateFlow(arrayListOf())
    val homeItemList: StateFlow<List<HomeItem>> = _homeItemList
    private val _initialHomeCount = MutableStateFlow(0)
    val initialHomeCount: StateFlow<Int> = _initialHomeCount

    private var _homeListState = MutableStateFlow<ListState>(ListState.IDLE)
    val homeListState: StateFlow<ListState> = _homeListState

    private var _continuation = MutableStateFlow<String?>(null)
    val continuation: StateFlow<String?> = _continuation

    private val _exploreMoodItem: MutableStateFlow<Mood?> = MutableStateFlow(null)
    val exploreMoodItem: StateFlow<Mood?> = _exploreMoodItem
    private val _accountInfo: MutableStateFlow<Pair<String?, String?>?> = MutableStateFlow(null)
    val accountInfo: StateFlow<Pair<String?, String?>?> = _accountInfo

    private var homeJob: Job? = null
    private var paginationJob: Job? = null
    private var chartJob: Job? = null
    private var homeRequest = 0
    private var chartRequest = 0
    private val exploredSeeds = mutableSetOf<String>()
    private var discoveryPage = 0
    private var paginationFailed = false

    val showSnackBarErrorState = MutableSharedFlow<String>()

    private val _chart: MutableStateFlow<Chart?> = MutableStateFlow(null)
    val chart: StateFlow<Chart?> = _chart
    private val _newRelease: MutableStateFlow<List<HomeItem>> = MutableStateFlow(arrayListOf())
    val newRelease: StateFlow<List<HomeItem>> = _newRelease
    var regionCodeChart: MutableStateFlow<String?> = MutableStateFlow(null)

    val loading = MutableStateFlow<Boolean>(true)
    val loadingChart = MutableStateFlow<Boolean>(true)
    private var regionCode: String = ""
    private var language: String = ""

    private val _songEntity: MutableStateFlow<SongEntity?> = MutableStateFlow(null)
    val songEntity: StateFlow<SongEntity?> = _songEntity

    private var _params: MutableStateFlow<String?> = MutableStateFlow(null)
    val params: StateFlow<String?> = _params

    // For showing alert that should log in to YouTube
    private val _showLogInAlert: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val showLogInAlert: StateFlow<Boolean> = _showLogInAlert

    val dataSyncId =
        dataStoreManager
            .dataSyncId
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _mainHomeThumbnail: MutableStateFlow<String?> = MutableStateFlow(null)
    val mainHomeThumbnail: StateFlow<String?> = _mainHomeThumbnail

    init {
        // Discovery is public. Personal YouTube collections are connected during onboarding;
        // an empty YouTube Music cookie must never trigger a second login prompt here.
        viewModelScope.launch {
            // One combined preference stream avoids five competing startup refreshes.
            combine(dataStoreManager.location, dataStoreManager.language,
                dataStoreManager.loggedIn, params, dataStoreManager.cookie
            ) { region, locale, loggedIn, filter, cookie ->
                listOf(region, locale, loggedIn, filter, cookie)
            }.debounce(150).distinctUntilChanged().collect {
                getHomeItemList(params.value)
                emitAccountInfo()
            }
        }
        viewModelScope.launch {
            dataStoreManager.chartKey.distinctUntilChanged().collect { loadChart(it) }
        }
        viewModelScope.launch {
            homeItemList.collectLatest { list ->
                _mainHomeThumbnail.value = list.firstOrNull()?.contents?.firstOrNull()
                    ?.thumbnails?.lastOrNull()?.url
            }
        }
    }

    fun doneShowLogInAlert(neverShowAgain: Boolean = false) {
        viewModelScope.launch {
            _showLogInAlert.update { false }
            if (neverShowAgain) {
                dataStoreManager.setShouldShowLogInRequiredAlert(false)
            }
        }
    }

    fun getHomeItemList(params: String? = null) {
        val request = ++homeRequest
        homeJob?.cancel()
        paginationJob?.cancel()
        _continuation.value = null
        exploredSeeds.clear()
        discoveryPage = 0
        paginationFailed = false
        if (chartJob?.isActive != true) regionCodeChart.value?.let(::loadChart)
        loading.value = true
        _homeListState.value = ListState.LOADING
        homeJob = viewModelScope.launch {
            supervisorScope {
                launch {
                    optionalData {
                        homeRepository.getMoodAndMomentsData().first().let {
                            if (request == homeRequest && it is Resource.Success) _exploreMoodItem.value = it.data
                        }
                    }
                }
                launch {
                    optionalData {
                        homeRepository.getNewRelease(
                            org.jetbrains.compose.resources.getString(Res.string.new_release),
                            org.jetbrains.compose.resources.getString(Res.string.music_video),
                        ).first().let {
                            if (request == homeRequest && it is Resource.Success) _newRelease.value = it.data.orEmpty()
                        }
                    }
                }
                try {
                    val result = withTimeout(20_000) {
                        homeRepository.getHomeData(
                            params,
                            org.jetbrains.compose.resources.getString(Res.string.view_count),
                            org.jetbrains.compose.resources.getString(Res.string.song),
                        ).first()
                    }
                    if (request != homeRequest) return@supervisorScope
                    when (result) {
                        is Resource.Success -> {
                            _initialHomeCount.value = result.data?.second.orEmpty().size
                            _homeItemList.value = result.data?.second.orEmpty()
                            _continuation.value = result.data?.first
                            // Publish core content before loading personalized recommendations.
                            launch { loadArtistRecommendations(request) }
                        }
                        is Resource.Error -> showSnackBarErrorState.emit(result.message ?: "Unable to load music")
                    }
                } catch (error: TimeoutCancellationException) {
                    showSnackBarErrorState.emit("Koneksi terlalu lama. Silakan coba lagi.")
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    showSnackBarErrorState.emit(error.message ?: "Unable to load music")
                } finally {
                    if (request == homeRequest) {
                        loading.value = false
                        _homeListState.value = ListState.IDLE
                    }
                }
            }
        }
    }

    private suspend fun optionalData(block: suspend () -> Unit) {
        try {
            withTimeoutOrNull(10_000) { block() }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Logger.w(tag, "Optional home section could not load")
        }
    }

    private suspend fun loadArtistRecommendations(request: Int) = optionalData {
        val artists = artistRepository.getFollowedArtists().first().take(3)
        val items = coroutineScope {
            artists.map { artist -> async {
                val result = artistRepository.getArtistData(artist.channelId).first()
                val songs = (result as? Resource.Success)?.data?.songs?.results.orEmpty().map { song ->
                    com.tan.domain.data.model.home.Content(
                        album = song.album, artists = song.artists, description = null,
                        isExplicit = song.isExplicit, playlistId = null, browseId = null,
                        thumbnails = song.thumbnails.orEmpty(), title = song.title.orEmpty(),
                        videoId = song.videoId, views = null,
                    )
                }
                songs.takeIf { it.isNotEmpty() }?.let {
                    HomeItem(contents = it, title = "Karena kamu suka ${artist.name}", subtitle = "Berdasarkan artis favoritmu")
                }
            } }.awaitAll().filterNotNull()
        }
        if (request == homeRequest) _homeItemList.update { com.tan.gratify.ui.utils.appendDiscovery(it, items) }
    }

    fun getContinueHomeItem(continuation: String?) {
        if (loading.value || paginationJob?.isActive == true || paginationFailed ||
            continuation != _continuation.value) return
        val request = homeRequest
        _homeListState.value = ListState.PAGINATING
        paginationJob = viewModelScope.launch {
            try {
                if (continuation.isNullOrBlank()) {
                    loadDiscoveryPage(request)
                    return@launch
                }
                val result = withTimeout(20_000) {
                    homeRepository.getHomeDataContinue(
                        continuation,
                        org.jetbrains.compose.resources.getString(Res.string.view_count),
                        org.jetbrains.compose.resources.getString(Res.string.song),
                    ).first()
                }
                if (request != homeRequest) return@launch
                when (result) {
                    is Resource.Success -> {
                        _continuation.value = result.data?.first?.takeUnless { it == continuation }
                        _homeItemList.update { com.tan.gratify.ui.utils.appendDiscovery(it, result.data?.second.orEmpty()) }
                    }
                    is Resource.Error -> {
                        paginationFailed = true
                        showSnackBarErrorState.emit(result.message ?: "Unable to load more music")
                    }
                }
            } catch (error: TimeoutCancellationException) {
                paginationFailed = true
                showSnackBarErrorState.emit("Koneksi terlalu lama. Tarik untuk memuat ulang.")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                paginationFailed = true
                showSnackBarErrorState.emit(error.message ?: "Unable to load more music")
            } finally {
                if (request == homeRequest && _homeListState.value != ListState.PAGINATION_EXHAUST)
                    _homeListState.value = ListState.IDLE
            }
        }
    }

    /** Continue discovery using real browse feeds and related songs after the home cursor ends. */
    private suspend fun loadDiscoveryPage(request: Int) {
        val filters = listOf(HOME_PARAMS_FEEL_GOOD, HOME_PARAMS_FOCUS, HOME_PARAMS_ENERGIZE,
            HOME_PARAMS_RELAX, HOME_PARAMS_ROMANCE, HOME_PARAMS_COMMUTE, HOME_PARAMS_PARTY,
            HOME_PARAMS_WORKOUT, HOME_PARAMS_SLEEP, HOME_PARAMS_SAD)
        val before = _homeItemList.value
        val page = discoveryPage
        val incoming = withTimeout(20_000) {
            if (page < filters.size) {
                val result = homeRepository.getHomeData(filters[page],
                    org.jetbrains.compose.resources.getString(Res.string.view_count),
                    org.jetbrains.compose.resources.getString(Res.string.song)).first()
                when (result) {
                    is Resource.Success -> result.data?.second.orEmpty()
                    is Resource.Error -> error(result.message ?: "Rekomendasi belum dapat dimuat")
                }
            } else {
                val seed = com.tan.gratify.ui.utils.nextDiscoverySeed(before, exploredSeeds)
                    ?: return@withTimeout emptyList()
                val result: Resource<Pair<List<com.tan.domain.data.model.browse.album.Track>, String?>> = when (seed.kind) {
                    com.tan.gratify.ui.utils.DiscoverySeedKind.SONG -> songRepository.getRelatedData(seed.id).first()
                    com.tan.gratify.ui.utils.DiscoverySeedKind.PLAYLIST -> {
                        when (val playlist = playlistRepository.getPlaylistData(seed.id,
                            org.jetbrains.compose.resources.getString(Res.string.view_count)).first()) {
                            is Resource.Success -> Resource.Success(playlist.data?.first?.tracks.orEmpty() to null)
                            is Resource.Error -> Resource.Error(playlist.message ?: "Playlist belum dapat dimuat")
                        }
                    }
                    com.tan.gratify.ui.utils.DiscoverySeedKind.ALBUM -> {
                        when (val album = albumRepository.getAlbumData(seed.id).first()) {
                            is Resource.Success -> Resource.Success(album.data?.tracks.orEmpty() to null)
                            is Resource.Error -> Resource.Error(album.message ?: "Album belum dapat dimuat")
                        }
                    }
                }
                when (result) {
                    is Resource.Success -> {
                        if (request != homeRequest) return@withTimeout emptyList()
                        exploredSeeds.add(seed.key)
                        val contents = result.data?.first.orEmpty().map { track ->
                            com.tan.domain.data.model.home.Content(album = track.album, artists = track.artists,
                                description = null, isExplicit = track.isExplicit, playlistId = null, browseId = null,
                                thumbnails = track.thumbnails.orEmpty(), title = track.title, videoId = track.videoId,
                                views = null, durationSeconds = track.durationSeconds)
                        }
                        listOf(HomeItem(contents, "Lanjut dari ${seed.content.title}", "Temukan musik yang senada"))
                    }
                    is Resource.Error -> error(result.message ?: "Rekomendasi belum dapat dimuat")
                }
            }
        }
        if (request != homeRequest) return
        discoveryPage++
        val merged = com.tan.gratify.ui.utils.appendDiscovery(_homeItemList.value, incoming)
        _homeItemList.value = merged
        // Move on to the next seed after a duplicate-only batch. Stop only when all sources are spent.
        if (merged.size == before.size && discoveryPage > filters.size &&
            com.tan.gratify.ui.utils.nextDiscoverySeed(merged, exploredSeeds) == null) {
            _homeListState.value = ListState.PAGINATION_EXHAUST
        }
    }

    fun retryDiscovery() {
        paginationFailed = false
        _homeListState.value = ListState.IDLE
        getContinueHomeItem(_continuation.value)
    }

    val canRetryDiscovery: Boolean get() = paginationFailed

    fun exploreChart(region: String) {
        viewModelScope.launch { dataStoreManager.setChartKey(region) }
        // Refresh the same region too; the preference collector handles actual changes.
        if (regionCodeChart.value == region) loadChart(region)
    }

    private fun loadChart(region: String) {
        val request = ++chartRequest
        chartJob?.cancel()
        regionCodeChart.value = region
        loadingChart.value = true
        chartJob = viewModelScope.launch {
            try {
                optionalData {
                    val result = homeRepository.getChartData(region).first()
                    if (request == chartRequest && result is Resource.Success) _chart.value = result.data
                }
            } finally {
                if (request == chartRequest) loadingChart.value = false
            }
        }
    }

    fun setParams(params: String?) {
        _params.value = params
    }

    private suspend fun emitAccountInfo() {
        val appName = dataStoreManager.getString("AppProfileName").first()
            ?: dataStoreManager.getString("AccountName").first()
        val thumbUrl = dataStoreManager.getString("AppProfileImage").first()
        if (!appName.isNullOrEmpty()) {
            _accountInfo.emit(Pair(appName, thumbUrl))
        } else {
            _accountInfo.emit(null)
        }
    }

    override fun onCleared() {
        super.onCleared()
        homeJob?.cancel()
    }

    companion object {
        // Home params
        const val HOME_PARAMS_RELAX = "ggM8SgQIBxADSgQIBRABSgQICRABSgQIChABSgQIDRABSgQICBABSgQIBBABSgQIDhABSgQIAxABSgQIBhAB"
        const val HOME_PARAMS_SLEEP = "ggM8SgQIBxABSgQIBRADSgQICRABSgQIChABSgQIDRABSgQICBABSgQIBBABSgQIDhABSgQIAxABSgQIBhAB"
        const val HOME_PARAMS_ENERGIZE = "ggM8SgQIBxABSgQIBRABSgQICRADSgQIChABSgQIDRABSgQICBABSgQIBBABSgQIDhABSgQIAxABSgQIBhAB"
        const val HOME_PARAMS_SAD = "ggM8SgQIBxABSgQIBRABSgQICRABSgQIChADSgQIDRABSgQICBABSgQIBBABSgQIDhABSgQIAxABSgQIBhAB"
        const val HOME_PARAMS_ROMANCE = "ggM8SgQIBxABSgQIBRABSgQICRABSgQIChABSgQIDRADSgQICBABSgQIBBABSgQIDhABSgQIAxABSgQIBhAB"
        const val HOME_PARAMS_FEEL_GOOD = "ggM8SgQIBxABSgQIBRABSgQICRABSgQIChABSgQIDRABSgQICBADSgQIBBABSgQIDhABSgQIAxABSgQIBhAB"
        const val HOME_PARAMS_WORKOUT = "ggM8SgQIBxABSgQIBRABSgQICRABSgQIChABSgQIDRABSgQICBABSgQIBBADSgQIDhABSgQIAxABSgQIBhAB"
        const val HOME_PARAMS_PARTY = "ggM8SgQIBxABSgQIBRABSgQICRABSgQIChABSgQIDRABSgQICBABSgQIBBABSgQIDhADSgQIAxABSgQIBhAB"
        const val HOME_PARAMS_COMMUTE = "ggM8SgQIBxABSgQIBRABSgQICRABSgQIChABSgQIDRABSgQICBABSgQIBBABSgQIDhABSgQIAxADSgQIBhAB"
        const val HOME_PARAMS_FOCUS = "ggM8SgQIBxABSgQIBRABSgQICRABSgQIChABSgQIDRABSgQICBABSgQIBBABSgQIDhABSgQIAxABSgQIBhAD"
    }
}
