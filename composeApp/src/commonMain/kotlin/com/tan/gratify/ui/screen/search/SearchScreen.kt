package com.tan.gratify.ui.screen.search

import com.tan.gratify.ui.theme.GratifyColors
import com.tan.gratify.ui.theme.GratifyShapes
import com.tan.gratify.viewModel.BrowseArtworkLoader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.tan.common.Config
import com.tan.domain.data.entities.SongEntity
import com.tan.domain.data.model.browse.album.Track
import com.tan.domain.data.model.searchResult.albums.AlbumsResult
import com.tan.domain.data.model.searchResult.artists.ArtistsResult
import com.tan.domain.data.model.searchResult.playlists.PlaylistsResult
import com.tan.domain.data.model.searchResult.songs.SongsResult
import com.tan.domain.data.model.searchResult.videos.VideosResult
import com.tan.domain.data.type.SearchResultType
import com.tan.domain.mediaservice.handler.PlaylistType
import com.tan.domain.mediaservice.handler.QueueData
import com.tan.domain.utils.toSongEntity
import com.tan.domain.utils.toTrack
import com.tan.gratify.extension.getStringBlocking
import com.tan.gratify.ui.component.Chip
import com.tan.gratify.ui.component.EndOfPage
import com.tan.gratify.ui.component.NowPlayingBottomSheet
import com.tan.gratify.ui.component.CenterLoadingBox
import com.tan.gratify.ui.component.GratifyChartButton
import com.tan.gratify.ui.navigation.destination.list.AlbumDestination
import com.tan.gratify.ui.navigation.destination.list.ArtistDestination
import com.tan.gratify.ui.navigation.destination.list.PlaylistDestination
import com.tan.gratify.ui.navigation.destination.list.SharedPlaylistDestination
import com.tan.gratify.ui.navigation.destination.list.PodcastDestination
import com.tan.gratify.ui.navigation.destination.social.UserProfileDestination
import com.tan.gratify.ui.navigation.destination.home.MoodDestination
import com.tan.gratify.ui.theme.typo
import com.tan.domain.manager.DataStoreManager
import com.tan.gratify.viewModel.HomeViewModel
import com.tan.gratify.viewModel.SearchScreenUIState
import com.tan.gratify.viewModel.SearchType
import com.tan.gratify.viewModel.SearchViewModel
import com.tan.gratify.viewModel.SharedViewModel
import com.tan.gratify.viewModel.toStringRes
import com.tan.gratify.viewModel.searchIdentity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import com.tan.domain.repository.SongRepository
import com.tan.domain.data.entities.DownloadState
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.Arrangement
import gratify.composeapp.generated.resources.*
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.albums
import gratify.composeapp.generated.resources.artists
import gratify.composeapp.generated.resources.baseline_arrow_outward_24
import gratify.composeapp.generated.resources.baseline_close_24
import gratify.composeapp.generated.resources.baseline_history_24
import gratify.composeapp.generated.resources.baseline_search_24
import gratify.composeapp.generated.resources.clear_search_history
import gratify.composeapp.generated.resources.error_occurred
import gratify.composeapp.generated.resources.holder
import gratify.composeapp.generated.resources.in_search
import gratify.composeapp.generated.resources.no_results_found
import gratify.composeapp.generated.resources.playlists
import gratify.composeapp.generated.resources.podcasts
import gratify.composeapp.generated.resources.retry
import gratify.composeapp.generated.resources.search_for
import gratify.composeapp.generated.resources.song
import gratify.composeapp.generated.resources.videos
import gratify.composeapp.generated.resources.what_do_you_want_to_listen_to

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    innerPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(0.dp),
    searchViewModel: SearchViewModel = koinInject(),
    sharedViewModel: SharedViewModel = koinInject(),
    homeViewModel: HomeViewModel = koinInject(),
    navController: NavController,
    onOpenDrawer: () -> Unit = {}
) {
    val dataStoreManager: DataStoreManager = koinInject()
    val songRepository: SongRepository = koinInject()
    val artworkHomeRepository: com.tan.domain.repository.HomeRepository = koinInject()
    val artworkSearchRepository: com.tan.domain.repository.SearchRepository = koinInject()
    val contentCache: com.tan.data.cache.JsonContentCache = koinInject()
    val appProfileImage by dataStoreManager.getString("AppProfileImage").collectAsStateWithLifecycle(initialValue = "")
    val appProfileName by dataStoreManager.getString("AppProfileName").collectAsStateWithLifecycle(initialValue = "")

    val uriHandler = LocalUriHandler.current
    val focusManager = LocalFocusManager.current
    val searchScreenState by searchViewModel.searchScreenState.collectAsStateWithLifecycle()
    val uiState by searchViewModel.searchScreenUIState.collectAsStateWithLifecycle()
    val searchHistory by searchViewModel.searchHistory.collectAsStateWithLifecycle()
    val exploreMoodItem by homeViewModel.exploreMoodItem.collectAsStateWithLifecycle()
    val discoveryItems by homeViewModel.homeItemList.collectAsStateWithLifecycle()
    val discoveryState by homeViewModel.homeListState.collectAsStateWithLifecycle()
    val discoveryLoading by homeViewModel.loading.collectAsStateWithLifecycle()
    val discoveryCursor by homeViewModel.continuation.collectAsStateWithLifecycle()
    val discoveryScroll = rememberLazyListState()
    val resultsScroll = rememberLazyListState()

    var searchUIType by rememberSaveable { mutableStateOf(SearchUIType.EMPTY) }
    val nearDiscoveryEnd by remember {
        androidx.compose.runtime.derivedStateOf {
            discoveryScroll.layoutInfo.totalItemsCount > 0 &&
                (discoveryScroll.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >=
                discoveryScroll.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(searchUIType, nearDiscoveryEnd, discoveryState, discoveryCursor) {
        if (searchUIType == SearchUIType.EMPTY && nearDiscoveryEnd && !discoveryLoading &&
            discoveryState == com.tan.gratify.viewModel.ListState.IDLE) {
            homeViewModel.getContinueHomeItem(discoveryCursor)
        }
    }
    var searchText by rememberSaveable { mutableStateOf(searchScreenState.query) }
    var isSearchSubmitted by rememberSaveable { mutableStateOf(false) }
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    var isFocused by rememberSaveable { mutableStateOf(false) }

    var sheetSong by remember { mutableStateOf<SongEntity?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    val currentVideoId by searchViewModel.nowPlayingVideoId.collectAsStateWithLifecycle()
    val chipRowState = rememberScrollState()
    val pullToRefreshState = rememberPullToRefreshState()
    var isRefreshingEmpty by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val browseArtwork = remember(scope, artworkHomeRepository, artworkSearchRepository) {
        BrowseArtworkLoader(scope, artworkHomeRepository, artworkSearchRepository, cache = contentCache)
    }
    val categoryCovers by browseArtwork.covers.collectAsStateWithLifecycle()
    val reselect by sharedViewModel.reloadDestination.collectAsStateWithLifecycle()
    LaunchedEffect(reselect) {
        if (reselect == com.tan.gratify.ui.navigation.destination.search.SearchDestination::class) {
            focusManager.clearFocus()
            isFocused = false; isExpanded = false
            discoveryScroll.animateScrollToItem(0)
            resultsScroll.animateScrollToItem(0)
            sharedViewModel.reloadDestinationDone()
            searchViewModel.getPublicPlaylists(refresh = true)
        }
    }
    val searchEntry = remember(navController) { navController.getBackStackEntry(com.tan.gratify.ui.navigation.destination.search.SearchDestination) }
    DisposableEffect(searchEntry, searchViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) searchViewModel.getPublicPlaylists(refresh = true)
        }
        searchEntry.lifecycle.addObserver(observer)
        if (searchEntry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
            searchViewModel.getPublicPlaylists(refresh = true)
        onDispose { searchEntry.lifecycle.removeObserver(observer) }
    }
    val peopleRequest by searchEntry.savedStateHandle
        .getStateFlow("people_search_requested", false).collectAsStateWithLifecycle()
    LaunchedEffect(peopleRequest) {
        if (peopleRequest) {
            searchViewModel.cancelSearch()
            searchViewModel.setSearchType(SearchType.USERS)
            searchText = "@"; isSearchSubmitted = false; isExpanded = true
            focusRequester.requestFocus()
            searchEntry.savedStateHandle["people_search_requested"] = false
        }
    }
    com.tan.gratify.ui.component.LayerBackHandler(isFocused) {
        focusManager.clearFocus(); isFocused = false; isExpanded = false
    }

    val accountRevision by searchViewModel.accountRevision.collectAsStateWithLifecycle()
    var previousAccountRevision by remember { mutableIntStateOf(accountRevision) }
    LaunchedEffect(accountRevision) {
        if (previousAccountRevision != accountRevision) {
            searchText = ""; isExpanded = false; isSearchSubmitted = false; focusManager.clearFocus()
            previousAccountRevision = accountRevision
        }
    }
    LaunchedEffect(searchScreenState.query, searchScreenState.searchType) { resultsScroll.scrollToItem(0) }
    val submitSearch: (String) -> Unit = { raw ->
        val query = raw.trim().take(200)
        if (canSubmitQuery(query, searchScreenState.searchType)) {
            searchText = query; isSearchSubmitted = true; isExpanded = false; focusManager.clearFocus()
            searchViewModel.insertSearchHistory(query)
            searchViewModel.search(query, searchScreenState.searchType)
        }
    }

    val onMoreClick: (SongEntity) -> Unit = { song ->
        sheetSong = song
        showBottomSheet = true
    }

    LaunchedEffect(searchText, searchScreenState.searchType, isFocused) {
        if (isFocused && searchScreenState.searchType == SearchType.USERS) {
            isExpanded = false
            isSearchSubmitted = true
            delay(250)
            searchViewModel.searchUsers(searchText)
            return@LaunchedEffect
        }
        if (isFocused) {
            isSearchSubmitted = false
            isExpanded = true
        }
        if (isFocused) {
            searchViewModel.suggestQuery(searchText)
        }
    }

    LaunchedEffect(isSearchSubmitted) {
        if (isSearchSubmitted) {
            isExpanded = false
        }
    }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            isExpanded = searchScreenState.searchType != SearchType.USERS
        }
    }

    LaunchedEffect(isExpanded, searchText, isFocused, searchScreenState.searchType) {
        searchUIType =
            if (searchScreenState.searchType == SearchType.USERS) {
                SearchUIType.SEARCH_RESULTS
            } else if (searchText.isNotEmpty() && isExpanded) {
                SearchUIType.SEARCH_SUGGESTIONS
            } else if (isFocused && isExpanded) {
                SearchUIType.SEARCH_HISTORY
            } else if (searchText.isEmpty()) {
                SearchUIType.EMPTY
            } else {
                SearchUIType.SEARCH_RESULTS
            }
    }

    if (showBottomSheet) {
        NowPlayingBottomSheet(
            onDismiss = {
                showBottomSheet = false
                sheetSong = null
            },
            navController = navController,
            song = sheetSong,
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Transparent)
    ) {
        com.tan.gratify.ui.component.ScreenHeader(
            title = stringResource(Res.string.search), profileName = appProfileName,
            profileImage = appProfileImage, onOpenProfile = onOpenDrawer,
            titleStyle = MaterialTheme.typography.headlineMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif, fontWeight = FontWeight.Bold),
        )
        val browsing = searchUIType == SearchUIType.EMPTY && !isFocused && searchScreenState.searchType != SearchType.USERS
        SearchScrollingControls(visible = !browsing || discoveryScroll.firstVisibleItemIndex == 0 && discoveryScroll.firstVisibleItemScrollOffset <= 16) {
        SearchControls(searchText, searchScreenState.searchType,
            onQuery = { newText -> searchViewModel.cancelSearch(); searchText = newText; isSearchSubmitted = false },
            onSubmit = { submitSearch(searchText) },
            onType = { type ->
                if (type != searchScreenState.searchType) {
                    val submitted = isSearchSubmitted
                    searchViewModel.setSearchType(type)
                    if (submitted && searchText.isNotBlank()) { isSearchSubmitted = true; searchViewModel.search(searchText, type) }
                }
            }, focusRequester = focusRequester, onFocus = { isFocused = it },
            landing = searchText.isEmpty() && !isFocused && !isExpanded && searchScreenState.searchType != SearchType.USERS)
        }

        if (searchScreenState.isSearching && uiState is SearchScreenUIState.Success) {
            androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Crossfade(targetState = searchUIType, modifier = Modifier.weight(1f).padding(bottom = innerPadding.calculateBottomPadding())) {
            when (it) {
                SearchUIType.SEARCH_SUGGESTIONS -> {
                    SearchQueryList(searchScreenState.suggestQueries, history = false,
                        problem = searchScreenState.suggestionFailed, suggestionLoading = searchScreenState.suggestionLoading,
                        onSubmit = submitSearch, onFill = { searchText = it; focusRequester.requestFocus() },
                        recommendations = {
                        items(searchScreenState.suggestYTItems) { item ->
                            SuggestItemRow(
                                searchResult = item,
                                onItemClick = { item ->
                                    when (item) {
                                        is SongsResult, is VideosResult -> {
                                            val firstTrack: Track = (item as? SongsResult)?.toTrack() ?: (item as VideosResult).toTrack()
                                            searchViewModel.setQueueData(
                                                QueueData.Data(
                                                    listTracks = arrayListOf(firstTrack),
                                                    firstPlayedTrack = firstTrack,
                                                    playlistId = "RDAMVM${firstTrack.videoId}",
                                                    playlistName = "\"${searchText}\" ${getStringBlocking(Res.string.in_search)}",
                                                    playlistType = PlaylistType.RADIO,
                                                    continuation = null,
                                                ),
                                            )
                                            searchViewModel.loadMediaItem(firstTrack, type = if (item is VideosResult) Config.VIDEO_CLICK else Config.SONG_CLICK)
                                        }

                                        is ArtistsResult -> {
                                            navController.navigate(
                                                ArtistDestination(item.browseId),
                                            )
                                        }

                                        is AlbumsResult -> {
                                            navController.navigate(
                                                AlbumDestination(item.browseId),
                                            )
                                        }

                                        is PlaylistsResult -> {
                                            navController.navigate(if (item.resultType == "Podcast") PodcastDestination(item.browseId) else PlaylistDestination(item.browseId))
                                        }
                                    }
                                },
                            )
                        }
                        })
                }

                SearchUIType.SEARCH_HISTORY -> {
                    SearchQueryList(searchHistory, history = true, busy = searchScreenState.historyBusy,
                        problem = searchScreenState.historyProblem, onSubmit = submitSearch,
                        onFill = { searchText = it; focusRequester.requestFocus() },
                        onRemove = { searchViewModel.deleteSearchHistory(it) },
                        onClear = { searchViewModel.deleteSearchHistory() }, onRetry = { searchViewModel.observeHistory() })
                }

                SearchUIType.EMPTY -> {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                    val browseColumns = searchBrowseColumns(maxWidth - 32.dp, LocalDensity.current.fontScale)
                    PullToRefreshBox(
                        isRefreshing = isRefreshingEmpty,
                        onRefresh = {
                            scope.launch {
                                isRefreshingEmpty = true
                                try {
                                    searchViewModel.getPublicPlaylists()
                                    homeViewModel.getHomeItemList()
                                    delay(1000)
                                } finally { isRefreshingEmpty = false }
                            }
                        },
                        state = pullToRefreshState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            state = discoveryScroll,
                            modifier = Modifier
                                .fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 120.dp, start = 16.dp, end = 16.dp, top = 8.dp)
                        ) {
                        item {
                            Text(
                                text = stringResource(Res.string.search_public_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif, fontWeight = FontWeight.Bold, color = Color.White),
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }

                        val chunkedExplore = searchScreenState.publicPlaylists.filter { !it.id.isNullOrBlank() }.distinctBy { it.id }.chunked(browseColumns)
                        if (chunkedExplore.isEmpty()) {
                            item {
                                Text(
                                    text = stringResource(when {
                                        searchScreenState.publicPlaylistsLoading -> Res.string.search_public_loading
                                        searchScreenState.publicPlaylistsProblem -> Res.string.search_public_failed
                                        else -> Res.string.search_public_empty
                                    }),
                                    style = typo().bodyMedium.copy(color = GratifyColors.TextSecondary),
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                                if (searchScreenState.publicPlaylistsProblem) TextButton(onClick = { searchViewModel.getPublicPlaylists(refresh = true) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(Res.string.retry)) }
                            }
                        } else {
                            items(chunkedExplore.size, key = { "public:${chunkedExplore[it].first().id}" }) { rowIndex ->
                                val rowItems = chunkedExplore[rowIndex]
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(IntrinsicSize.Min),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    rowItems.forEach { item ->
                                        SearchCategoryTile(SearchCategoryEntry(item.id.orEmpty(), item.title, item.thumbnailUrl,
                                            item.creatorName?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.search_kind_playlist),item.automaticImages),
                                            modifier = Modifier.weight(1f).fillMaxHeight()) {
                                            navController.navigate(SharedPlaylistDestination(item.id.orEmpty()))
                                        }
                                    }
                                    repeat(browseColumns - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                                }
                            }
                        }

                        item {
                            LaunchedEffect(Unit) { browseArtwork.podcasts() }
                            val musicCover = discoveryItems.asSequence().flatMap { it.contents.asSequence() }.filterNotNull()
                                .mapNotNull { it.thumbnails.lastOrNull { thumbnail -> thumbnail.url.isNotBlank() }?.url }.firstOrNull()
                            val shortcuts = listOf(
                                SearchCategoryEntry("quick:music", stringResource(Res.string.search_browse_music), musicCover),
                                SearchCategoryEntry("quick:podcasts", stringResource(Res.string.podcasts), categoryCovers["quick:podcasts"]),
                            )
                            Column(Modifier.padding(top = 12.dp, bottom = 8.dp)) {
                                shortcuts.chunked(browseColumns).forEach { row ->
                                    SearchCategoryRow(row, browseColumns, compact = true) { entry ->
                                        searchViewModel.setSearchType(if (entry.id == "quick:podcasts") SearchType.PODCASTS else SearchType.SONGS)
                                        isSearchSubmitted = false
                                        isExpanded = true
                                        focusRequester.requestFocus()
                                    }
                                }
                            }
                        }
                        // Only server-provided browse IDs are navigable; category labels never invent destinations.
                        val categories = searchBrowseCategories(exploreMoodItem)
                        if (categories.isNotEmpty()) {
                            item {
                                Text(stringResource(Res.string.search_explore_all),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                            }
                            val categoryRows = categories.chunked(browseColumns)
                            items(categoryRows.size, key = { "category:${categoryRows[it].first().id}" }) { row ->
                                LaunchedEffect(categoryRows[row].map { it.id }) {
                                    categoryRows[row].forEach { browseArtwork.category(it.id) }
                                }
                                SearchCategoryRow(categoryRows[row].map { it.copy(imageUrl = categoryCovers[it.id]) }, browseColumns) { entry ->
                                    navController.navigate(MoodDestination(entry.id))
                                }
                            }
                        }

                        item {
                            com.tan.gratify.ui.component.HomeSectionHeading(
                                title = stringResource(Res.string.search_discover_title),
                                subtitle = stringResource(Res.string.search_discover_body),
                                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                            )
                        }
                        items(discoveryItems.size) { index ->
                            com.tan.gratify.ui.component.HomeItem(
                                homeViewModel = homeViewModel, navController = navController,
                                data = discoveryItems[index],
                            )
                            Spacer(Modifier.height(24.dp))
                        }
                        item {
                            com.tan.gratify.ui.component.DiscoveryFooter(
                                loading = discoveryLoading || discoveryState == com.tan.gratify.viewModel.ListState.PAGINATING,
                                failed = homeViewModel.canRetryDiscovery,
                                exhausted = discoveryState == com.tan.gratify.viewModel.ListState.PAGINATION_EXHAUST,
                                onRetry = homeViewModel::retryDiscovery,
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            GratifyChartButton(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                            ) {
                                uriHandler.openUri("https://chart.gratify.org")
                            }
                        }
                    }
                    }
                }
                }

                SearchUIType.SEARCH_RESULTS -> {
                    // Content area
                    Column(modifier = Modifier.fillMaxSize()) {
                        PullToRefreshBox(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 10.dp),
                            state = pullToRefreshState,
                            onRefresh = {
                                val query = searchText.trim()
                                if (query.isNotEmpty()) {
                                    isSearchSubmitted = true
                                    searchViewModel.insertSearchHistory(query)
                                    searchViewModel.search(query)
                                }
                            },
                            isRefreshing = uiState is SearchScreenUIState.Loading,
                            indicator = {
                                PullToRefreshDefaults.Indicator(
                                    state = pullToRefreshState,
                                    isRefreshing = uiState is SearchScreenUIState.Loading,
                                    modifier = Modifier.align(Alignment.TopCenter),
                                    containerColor = PullToRefreshDefaults.indicatorContainerColor,
                                    color = PullToRefreshDefaults.indicatorColor,
                                    maxDistance = PullToRefreshDefaults.PositionalThreshold - 5.dp,
                                )
                            },
                        ) {
                            Crossfade(targetState = uiState) { uiState ->
                                when (uiState) {
                                    is SearchScreenUIState.Loading -> {
                                        SearchResultStatus(uiState, searchText, searchScreenState.searchType)
                                    }

                                    is SearchScreenUIState.Success -> {
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            if (searchScreenState.incomplete) SearchPartialNotice(searchScreenState.isSearching) {
                                                searchViewModel.search(searchText)
                                            }
                                            // Search Results List
                                            val currentResults =
                                                when (searchScreenState.searchType) {
                                                    SearchType.ALL -> searchScreenState.searchAllResult
                                                    SearchType.SONGS -> searchScreenState.searchSongsResult
                                                    SearchType.VIDEOS -> searchScreenState.searchVideosResult
                                                    SearchType.ALBUMS -> searchScreenState.searchAlbumsResult
                                                    SearchType.ARTISTS -> searchScreenState.searchArtistsResult
                                                    SearchType.PLAYLISTS -> searchScreenState.searchPlaylistsResult
                                                    SearchType.FEATURED_PLAYLISTS -> searchScreenState.searchFeaturedPlaylistsResult
                                                    SearchType.PODCASTS -> searchScreenState.searchPodcastsResult
                                                    SearchType.USERS -> emptyList() // Handled separately
                                                }

                                            Crossfade(targetState = (currentResults.isNotEmpty() || (searchScreenState.searchType == SearchType.USERS && searchScreenState.searchUsersResult.isNotEmpty()))) { hasResults ->
                                                if (hasResults) {
                                                    LazyColumn(
                                                        contentPadding = PaddingValues(horizontal = 4.dp),
                                                        state = resultsScroll,
                                                    ) {
                                                        if (searchScreenState.searchType == SearchType.USERS) {
                                                            items(searchScreenState.searchUsersResult, key = { it.id }) { user ->
                                                                SearchPersonCard(user.id, user.displayName, user.avatarUrl) {
                                                                    navController.navigate(UserProfileDestination(user.id))
                                                                }
                                                            }
                                                            item {
                                                                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                                    searchScreenState.usersPageError?.let { Text(stringResource(Res.string.search_people_page_failed), color = GratifyColors.TextSecondary) }
                                                                    if (searchScreenState.usersLoadingMore) Text(stringResource(Res.string.search_people_loading), color = GratifyColors.TextSecondary)
                                                                    else if (searchScreenState.usersMore) androidx.compose.material3.TextButton(onClick = { searchViewModel.loadMoreUsers() }, modifier = Modifier.heightIn(min = 48.dp)) {
                                                                        Text(stringResource(if (searchScreenState.usersPageError != null) Res.string.retry else Res.string.search_more_people))
                                                                    }
                                                                }
                                                            }
                                                        } else {
                                                            items(currentResults, key = { it.searchIdentity() }) { result ->
                                                                val info = result.searchCardInfo()
                                                                val track = when (result) {
                                                                    is SongsResult -> result.toTrack()
                                                                    is VideosResult -> result.toTrack()
                                                                    else -> null
                                                                }
                                                                val downloaded = if (track != null) {
                                                                    val state by remember(track.videoId) {
                                                                        songRepository.getSongAsFlow(track.videoId).map { it?.downloadState == DownloadState.STATE_DOWNLOADED }
                                                                    }.collectAsStateWithLifecycle(initialValue = false)
                                                                    state
                                                                } else false
                                                                SearchResultCard(
                                                                    info = info,
                                                                    isCurrent = info.playable && info.id.isNotBlank() && info.id == currentVideoId,
                                                                    downloaded = downloaded,
                                                                    onClick = {
                                                                        if (track != null) {
                                                                            searchViewModel.setQueueData(QueueData.Data(
                                                                                listTracks = arrayListOf(track), firstPlayedTrack = track,
                                                                                playlistId = "RDAMVM${info.id}",
                                                                                playlistName = "\"${searchText}\" ${getStringBlocking(Res.string.in_search)}",
                                                                                playlistType = PlaylistType.RADIO, continuation = null,
                                                                            ))
                                                                            searchViewModel.loadMediaItem(track, if (result is VideosResult) Config.VIDEO_CLICK else Config.SONG_CLICK)
                                                                        } else when (result) {
                                                                            is AlbumsResult -> navController.navigate(AlbumDestination(result.browseId))
                                                                            is ArtistsResult -> navController.navigate(ArtistDestination(result.browseId))
                                                                            is PlaylistsResult -> if (result.resultType == "Podcast") navController.navigate(PodcastDestination(result.browseId))
                                                                                else navController.navigate(PlaylistDestination(result.browseId))
                                                                            else -> Unit
                                                                        }
                                                                    },
                                                                    onMore = track?.let { { onMoreClick(it.toSongEntity()) } },
                                                                    onQueue = track?.let { { sharedViewModel.addListToQueue(arrayListOf(it)) } },
                                                                )
                                                            }
                                                        }
                                                        item { Spacer(modifier = Modifier.height(24.dp)) }
                                                    }
                                                } else {
                                                    SearchResultStatus(if (searchScreenState.incomplete && !searchScreenState.isSearching) SearchScreenUIState.Error else uiState, searchText, searchScreenState.searchType,
                                                        onRetry = { searchViewModel.search(searchText) }, loading = searchScreenState.isSearching,
                                                        onAll = { searchViewModel.search(searchText, SearchType.ALL) })
                                                }
                                            }
                                        }
                                    }

                                    is SearchScreenUIState.Error -> SearchResultStatus(uiState, searchText, searchScreenState.searchType,
                                        onRetry = { searchViewModel.search(searchText) })
                                    SearchScreenUIState.Empty -> SearchResultStatus(uiState, searchText, searchScreenState.searchType)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SuggestItemRow(searchResult: SearchResultType, onItemClick: (SearchResultType) -> Unit) {
    SearchResultCard(info = searchResult.searchCardInfo(), modifier = Modifier.padding(horizontal = 16.dp), onClick = { onItemClick(searchResult) })
}

enum class SearchUIType {
    EMPTY,
    SEARCH_HISTORY,
    SEARCH_SUGGESTIONS,
    SEARCH_RESULTS,
}
