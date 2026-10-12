package com.tan.gratify.ui.screen.home


import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.tan.domain.manager.DataStoreManager
import com.tan.common.CHART_SUPPORTED_COUNTRY
import com.tan.common.Config
import com.tan.domain.data.model.browse.album.Track
import com.tan.domain.data.model.home.HomeItem
import com.tan.domain.data.model.home.chart.Chart
import com.tan.domain.data.model.mood.Mood
import com.tan.domain.mediaservice.handler.PlaylistType
import com.tan.domain.mediaservice.handler.QueueData
import com.tan.domain.utils.toTrack
import com.tan.logger.Logger
import com.tan.gratify.extension.isScrollingUp
import com.tan.gratify.ui.component.CenterLoadingBox
import com.tan.gratify.ui.component.Chip
import com.tan.gratify.ui.component.HomeMoodFilters
import com.tan.gratify.ui.component.HomeSectionHeading
import com.tan.gratify.ui.component.HomeChartRegionPicker
import com.tan.gratify.ui.component.HomeChartShelves
import com.tan.gratify.ui.component.HomeExploreCategories
import com.tan.gratify.ui.theme.GratifySpacing
import com.tan.gratify.ui.component.EndOfPage
import com.tan.gratify.ui.component.HomeItem
import com.tan.gratify.ui.component.OfflineErrorState
import com.tan.gratify.ui.component.QuickPicksItem
import com.tan.gratify.ui.navigation.destination.home.HomeDestination
import com.tan.gratify.ui.navigation.destination.home.MoodDestination
import com.tan.gratify.ui.navigation.destination.library.LibraryDynamicPlaylistDestination
import com.tan.gratify.ui.navigation.destination.list.ArtistDestination
import com.tan.gratify.ui.screen.library.LibraryDynamicPlaylistType
import com.tan.gratify.ui.navigation.destination.list.PlaylistDestination
import com.tan.gratify.ui.navigation.destination.login.LoginDestination
import com.tan.gratify.ui.theme.typo
import com.tan.gratify.viewModel.HomeViewModel
import com.tan.gratify.viewModel.HomeViewModel.Companion.HOME_PARAMS_COMMUTE
import com.tan.gratify.viewModel.HomeViewModel.Companion.HOME_PARAMS_ENERGIZE
import com.tan.gratify.viewModel.HomeViewModel.Companion.HOME_PARAMS_FEEL_GOOD
import com.tan.gratify.viewModel.HomeViewModel.Companion.HOME_PARAMS_FOCUS
import com.tan.gratify.viewModel.HomeViewModel.Companion.HOME_PARAMS_PARTY
import com.tan.gratify.viewModel.HomeViewModel.Companion.HOME_PARAMS_RELAX
import com.tan.gratify.viewModel.HomeViewModel.Companion.HOME_PARAMS_ROMANCE
import com.tan.gratify.viewModel.HomeViewModel.Companion.HOME_PARAMS_SAD
import com.tan.gratify.viewModel.HomeViewModel.Companion.HOME_PARAMS_SLEEP
import com.tan.gratify.viewModel.HomeViewModel.Companion.HOME_PARAMS_WORKOUT
import com.tan.gratify.viewModel.ListState
import com.tan.gratify.viewModel.SharedViewModel
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import gratify.composeapp.generated.resources.home
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.all
import gratify.composeapp.generated.resources.cancel
import gratify.composeapp.generated.resources.chart
import gratify.composeapp.generated.resources.commute
import gratify.composeapp.generated.resources.do_not_show_again
import gratify.composeapp.generated.resources.energize
import gratify.composeapp.generated.resources.feel_good
import gratify.composeapp.generated.resources.focus
import gratify.composeapp.generated.resources.go_to_log_in_page
import gratify.composeapp.generated.resources.let_s_start_with_a_radio
import gratify.composeapp.generated.resources.log_in_warning
import gratify.composeapp.generated.resources.party
import gratify.composeapp.generated.resources.quick_picks
import gratify.composeapp.generated.resources.relax
import gratify.composeapp.generated.resources.romance
import gratify.composeapp.generated.resources.sad
import gratify.composeapp.generated.resources.sleep
import gratify.composeapp.generated.resources.warning
import gratify.composeapp.generated.resources.what_is_best_choice_today
import gratify.composeapp.generated.resources.workout
import gratify.composeapp.generated.resources.settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import com.tan.gratify.ui.navigation.destination.home.SettingsDestination

// DataStore key for blog-promo one-shot dialog. Bump the suffix (v2, v3, …) to re-promote.
private const val BLOG_PROMO_KEY = "blog_promo_v1_seen"



@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@ExperimentalFoundationApi
@Composable
fun HomeScreen(
    innerPadding: PaddingValues = PaddingValues(0.dp),
    onScrolling: (onTop: Boolean) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    viewModel: HomeViewModel =
        koinViewModel(),
    sharedViewModel: SharedViewModel =
        koinInject(),
    navController: NavController,
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberLazyListState()
    val isScrollingUp by scrollState.isScrollingUp()
    val homeData by viewModel.homeItemList.collectAsStateWithLifecycle()
    val initialHomeCount by viewModel.initialHomeCount.collectAsStateWithLifecycle()
    val newRelease by viewModel.newRelease.collectAsStateWithLifecycle()
    val chart by viewModel.chart.collectAsStateWithLifecycle()
    val moodMomentAndGenre by viewModel.exploreMoodItem.collectAsStateWithLifecycle()
    val chartLoading by viewModel.loadingChart.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val regionChart by viewModel.regionCodeChart.collectAsStateWithLifecycle()
    val reloadDestination by sharedViewModel.reloadDestination.collectAsStateWithLifecycle()
    val pullToRefreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }
    val params by viewModel.params.collectAsStateWithLifecycle()
    val homeListState by viewModel.homeListState.collectAsStateWithLifecycle()
    val continuation by viewModel.continuation.collectAsStateWithLifecycle()

    val shouldShowLogInAlert by viewModel.showLogInAlert.collectAsStateWithLifecycle()



    var topAppBarHeightPx by rememberSaveable {
        mutableIntStateOf(0)
    }

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.firstVisibleItemIndex }
            .collect {
                if (it <= 1) {
                    onScrolling.invoke(true)
                } else {
                    onScrolling.invoke(isScrollingUp)
                }
            }
    }

    val onRefresh: () -> Unit = {
        isRefreshing = true
        viewModel.getHomeItemList(params)
        Logger.w("HomeScreen", "onRefresh")
    }
    LaunchedEffect(reloadDestination) {
        if (reloadDestination == HomeDestination::class) {
            scrollState.animateScrollToItem(0)
            sharedViewModel.reloadDestinationDone()
        }
    }
    LaunchedEffect(key1 = loading) {
        if (!loading) {
            isRefreshing = false
            sharedViewModel.reloadDestinationDone()
            coroutineScope.launch {
                pullToRefreshState.animateToHidden()
            }
        }
    }
    
    val shouldStartPaginate =
        remember(homeListState, continuation, loading) {
            derivedStateOf {
                !loading && scrollState.layoutInfo.totalItemsCount > 0 &&
                homeListState != ListState.PAGINATION_EXHAUST &&
                    (
                        scrollState.layoutInfo.visibleItemsInfo
                            .lastOrNull()
                            ?.index ?: -9
                    ) >= (scrollState.layoutInfo.totalItemsCount - 2)
            }
        }

    LaunchedEffect(shouldStartPaginate.value, homeListState) {
        Logger.d("HomeScreen", "shouldStartPaginate: ${shouldStartPaginate.value}")
        Logger.d("HomeScreen", "homeListState: $homeListState")
        Logger.d("HomeScreen", "Continuation: $continuation")
        if (shouldStartPaginate.value && homeListState == ListState.IDLE) {
            viewModel.getContinueHomeItem(
                continuation,
            )
        }
    }

//    if (shouldShowGetDataSyncIdBottomSheet) {
//        GetDataSyncIdBottomSheet(
//            cookie = youTubeCookie,
//            onDismissRequest = {
//                shouldShowGetDataSyncIdBottomSheet = false
//            },
//        )
//    }

    if (shouldShowLogInAlert) {
        var doNotShowAgain by rememberSaveable {
            mutableStateOf(false)
        }
        AlertDialog(
            title = {
                Text(stringResource(Res.string.warning))
            },
            text = {
                Column {
                    Text(text = stringResource(Res.string.log_in_warning))
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .clickable {
                                    doNotShowAgain = !doNotShowAgain
                                }.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = doNotShowAgain,
                            onCheckedChange = {
                                doNotShowAgain = it
                            },
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(stringResource(Res.string.do_not_show_again))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.doneShowLogInAlert(doNotShowAgain)
                    navController.navigate(LoginDestination)
                }) {
                    Text(stringResource(Res.string.go_to_log_in_page))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.doneShowLogInAlert(doNotShowAgain)
                }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
            onDismissRequest = {
                viewModel.doneShowLogInAlert()
            },
        )
    }

    Box {
        PullToRefreshBox(
            modifier =
                Modifier.fillMaxSize(),
            state = pullToRefreshState,
            onRefresh = onRefresh,
            isRefreshing = isRefreshing,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(
                                top =
                                    with(LocalDensity.current) {
                                        topAppBarHeightPx.toDp()
                                    },
                            ),
                    containerColor = PullToRefreshDefaults.indicatorContainerColor,
                    color = PullToRefreshDefaults.indicatorColor,
                    maxDistance = PullToRefreshDefaults.PositionalThreshold,
                )
            },
        ) {
            Crossfade(targetState = loading && homeData.isEmpty(), label = "Home Shimmer") { loading ->
                if (!loading) {
                    if (homeData.isEmpty()) {
                        OfflineErrorState(
                            onRetry = onRefresh,
                            onOpenDownloaded = {
                                navController.navigate(
                                    LibraryDynamicPlaylistDestination(
                                        type = LibraryDynamicPlaylistType.Downloaded.toStringParams(),
                                    ),
                                )
                            },
                        )
                        return@Crossfade
                    }
                    LazyColumn(
                        state = scrollState,
                        verticalArrangement = Arrangement.spacedBy(28.dp),
                        contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 16.dp),
                    ) {
                        itemsIndexed(homeData.take(initialHomeCount), key = { index, item ->
                            "$index-${item.title}"
                        }) { index, item ->
                            Box {
                                Column(
                                    modifier =
                                        Modifier
                                            .padding(horizontal = GratifySpacing.Page),
                                ) {
                                    if (index == 0) {
                                        Spacer(
                                            Modifier.height(
                                                with(LocalDensity.current) {
                                                    topAppBarHeightPx.toDp()
                                                }
                                            )
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (item.title == stringResource(Res.string.quick_picks)) {
                                        AnimatedVisibility(
                                            visible =
                                                homeData.find {
                                                    it.title ==
                                                        stringResource(
                                                            Res.string.quick_picks,
                                                        )
                                                } != null,
                                        ) {
                                            QuickPicks(
                                                homeItem =
                                                    (
                                                        homeData.find {
                                                            it.title ==
                                                                stringResource(
                                                                    Res.string.quick_picks,
                                                                )
                                                        } ?: return@AnimatedVisibility
                                                    ).let { content ->
                                                        content.copy(
                                                            contents =
                                                                content.contents.mapNotNull { ct ->
                                                                    ct?.copy(
                                                                        artists =
                                                                            ct.artists?.let { art ->
                                                                                if (art.size > 1) {
                                                                                    art.dropLast(1)
                                                                                } else {
                                                                                    art
                                                                                }
                                                                            },
                                                                    )
                                                                },
                                                        )
                                                    },
                                                viewModel = viewModel,
                                            )
                                        }
                                    } else {
                                        HomeItem(
                                            homeViewModel = viewModel,
                                            navController = navController,
                                            data = item,
                                        )
                                    }
                                }
                            }
                        }
                        if (!loading) {
                            items(newRelease, key = { it.hashCode() }) {
                                AnimatedVisibility(
                                    visible = newRelease.isNotEmpty(),
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .padding(horizontal = GratifySpacing.Page),
                                    ) {
                                        HomeItem(
                                            homeViewModel = viewModel,
                                            navController = navController,
                                            data = it,
                                        )
                                    }
                                }
                            }
                            item {
                                AnimatedVisibility(
                                    visible = moodMomentAndGenre != null,
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .padding(horizontal = GratifySpacing.Page),
                                    ) {
                                        moodMomentAndGenre?.let {
                                            MoodMomentAndGenre(
                                                mood = it,
                                                navController = navController,
                                            )
                                        }
                                    }
                                }
                            }
                            item {
                                Column(
                                    Modifier
                                        .padding(horizontal = GratifySpacing.Page),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    ChartTitle()
                                    Crossfade(targetState = regionChart) {
                                        Logger.w("HomeScreen", "regionChart: $it")
                                        if (it != null) {
                                            HomeChartRegionPicker(
                                                regions = CHART_SUPPORTED_COUNTRY.itemsData.toList(),
                                                selected =
                                                    CHART_SUPPORTED_COUNTRY.itemsData.getOrNull(
                                                        CHART_SUPPORTED_COUNTRY.items.indexOf(it),
                                                    )
                                                        ?: CHART_SUPPORTED_COUNTRY.itemsData[1],
                                            ) {
                                                viewModel.exploreChart(
                                                    CHART_SUPPORTED_COUNTRY.items[
                                                        CHART_SUPPORTED_COUNTRY.itemsData.indexOf(
                                                            it,
                                                        ),
                                                    ],
                                                )
                                            }
                                        }
                                    }
                                    Crossfade(
                                        targetState = chartLoading,
                                        label = "Chart",
                                    ) { loading ->
                                        if (!loading) {
                                            chart?.let {
                                                ChartData(
                                                    chart = it,
                                                    navController = navController,
                                                )
                                            }
                                        } else {
                                            CenterLoadingBox(
                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .height(400.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        itemsIndexed(homeData.drop(initialHomeCount), key = { index, item -> "discovery-$index-${item.title}" }) { _, section ->
                            Box(Modifier.padding(horizontal = 16.dp)) {
                                HomeItem(homeViewModel = viewModel, navController = navController, data = section)
                            }
                        }
                        item {
                            com.tan.gratify.ui.component.DiscoveryFooter(
                                loading = homeListState == ListState.PAGINATING,
                                failed = viewModel.canRetryDiscovery,
                                exhausted = homeListState == ListState.PAGINATION_EXHAUST,
                                onRetry = viewModel::retryDiscovery,
                            )
                        }
                    }
                } else {
                    Column {
                        Spacer(
                            Modifier.height(
                                with(LocalDensity.current) {
                                    topAppBarHeightPx.toDp()
                                },
                            ),
                        )
                        CenterLoadingBox(Modifier.fillMaxSize())
                    }
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
                .onGloballyPositioned { topAppBarHeightPx = it.size.height }
        ) {
            HomeTopAppBar(navController, onOpenDrawer)
            if (loading && homeData.isNotEmpty()) {
                androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
            }
            HomeMoodFilters(params, onSelect = viewModel::setParams)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopAppBar(navController: NavController, onOpenDrawer: () -> Unit = {}) {
    val dataStoreManager: DataStoreManager = koinInject()
    val profileImage by dataStoreManager.getString("AppProfileImage").collectAsStateWithLifecycle(initialValue = "")
    val profileName by dataStoreManager.getString("AppProfileName").collectAsStateWithLifecycle(initialValue = "")
    HomeHeaderContent(profileName, profileImage, onOpenDrawer) {
        navController.navigate(SettingsDestination) { launchSingleTop = true }
    }
}

@Composable
fun HomeHeaderContent(
    profileName: String?, profileImage: String?, onOpenProfile: () -> Unit, onOpenSettings: () -> Unit,
) {
    com.tan.gratify.ui.component.ScreenHeader(
        title = stringResource(Res.string.home), profileName = profileName,
        profileImage = profileImage, onOpenProfile = onOpenProfile,
        actions = {
            androidx.compose.material3.IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
                androidx.compose.material3.Icon(Icons.Rounded.Settings, contentDescription = stringResource(Res.string.settings))
            }
        },
    )
}


@ExperimentalFoundationApi
@Composable
fun QuickPicks(
    homeItem: HomeItem,
    viewModel: HomeViewModel = koinViewModel(),
) {
    QuickPicksContent(homeItem) { content ->
        val firstQueue: Track = content.toTrack()
        viewModel.setQueueData(
            QueueData.Data(
                listTracks = arrayListOf(firstQueue),
                firstPlayedTrack = firstQueue,
                playlistId = "RDAMVM${content.videoId}",
                playlistName = "\"${content.title}\" Radio",
                playlistType = PlaylistType.RADIO,
                continuation = null,
            ),
        )
        viewModel.loadMediaItem(firstQueue, type = Config.SONG_CLICK)
    }
}

/** The same home section can be rendered with live music or debug sample data. */
@ExperimentalFoundationApi
@Composable
fun QuickPicksContent(
    homeItem: HomeItem,
    onPlay: (com.tan.domain.data.model.home.Content) -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = when {
            maxWidth >= 760.dp -> 3
            maxWidth < 400.dp && androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.2f -> 1
            else -> 2
        }
        val cardWidth = (maxWidth - 8.dp * (columns - 1)) / columns
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeSectionHeading(
                title = stringResource(Res.string.quick_picks),
                subtitle = stringResource(Res.string.let_s_start_with_a_radio),
                modifier = Modifier.padding(bottom = 8.dp),
            )
            homeItem.contents.filterNotNull().take(maxOf(8, columns * 4)).chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { content ->
                        com.tan.gratify.ui.component.QuickDiscoveryCard(
                            content = content, modifier = Modifier.width(cardWidth),
                            onPlay = { onPlay(content) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MoodMomentAndGenre(mood: Mood, navController: NavController) {
    HomeExploreCategories(mood) { params -> navController.navigate(MoodDestination(params)) }
}

@Composable
fun ChartTitle() {
    HomeSectionHeading(
        title = stringResource(Res.string.chart),
        subtitle = stringResource(Res.string.what_is_best_choice_today),
    )
}

@Composable
fun ChartData(chart: Chart, navController: NavController) {
    HomeChartShelves(
        chart = chart,
        onPlaylist = { id ->
            navController.navigate(PlaylistDestination(playlistId = id, isYourYouTubePlaylist = false))
        },
        onArtist = { id -> navController.navigate(ArtistDestination(channelId = id)) },
    )
}
