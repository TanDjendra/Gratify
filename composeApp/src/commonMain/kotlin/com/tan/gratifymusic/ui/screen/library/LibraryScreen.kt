package com.tan.gratifymusic.ui.screen.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.tan.common.LibraryChipType
import com.tan.domain.utils.LocalResource
import com.tan.logger.Logger
import com.tan.gratifymusic.extension.copy
import com.tan.gratifymusic.extension.isScrollingUp
import com.tan.gratifymusic.ui.component.Chip
import com.tan.gratifymusic.ui.component.EndOfPage
import com.tan.gratifymusic.ui.component.GridLibraryPlaylist
import com.tan.gratifymusic.ui.component.LibraryItem
import com.tan.gratifymusic.ui.component.LibraryItemState
import com.tan.gratifymusic.ui.component.LibraryItemType
import com.tan.gratifymusic.ui.component.LibraryEmptyRecommendationBox
import com.tan.gratifymusic.ui.navigation.destination.home.AnalyticsDestination
import com.tan.gratifymusic.ui.theme.transparent
import com.tan.gratifymusic.ui.theme.typo
import com.tan.gratifymusic.viewModel.LibraryViewModel
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import com.tan.gratifymusic.viewModel.SharedViewModel
import gratifymusic.composeapp.generated.resources.*
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import com.tan.gratifymusic.extension.angledGradientBackground
import gratifymusic.composeapp.generated.resources.round_library_music_24
import gratifymusic.composeapp.generated.resources.baseline_queue_music_24
import gratifymusic.composeapp.generated.resources.baseline_sensors_24
import gratifymusic.composeapp.generated.resources.baseline_playlist_add_24
import gratifymusic.composeapp.generated.resources.baseline_favorite_24
import gratifymusic.composeapp.generated.resources.baseline_downloaded
import gratifymusic.composeapp.generated.resources.ic_microphone
import gratifymusic.composeapp.generated.resources.baseline_trending_up_24

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun LibraryScreen(
    innerPadding: PaddingValues,
    viewModel: LibraryViewModel = koinViewModel(),
    sharedViewModel: SharedViewModel = koinInject(),
    navController: NavController,
    onScrolling: (onTop: Boolean) -> Unit = {},
) {
    val density = LocalDensity.current

    val loggedIn by viewModel.youtubeLoggedIn.collectAsStateWithLifecycle(initialValue = false)
    val nowPlaying by viewModel.nowPlayingVideoId.collectAsStateWithLifecycle()
    val youTubePlaylist by viewModel.youTubePlaylist.collectAsStateWithLifecycle()
    val youTubeMixForYou by viewModel.youTubeMixForYou.collectAsStateWithLifecycle()
    val listCanvasSong by viewModel.listCanvasSong.collectAsStateWithLifecycle()
    val yourLocalPlaylist by viewModel.yourLocalPlaylist.collectAsStateWithLifecycle()
    val favoritePlaylist by viewModel.favoritePlaylist.collectAsStateWithLifecycle()
    val downloadedPlaylist by viewModel.downloadedPlaylist.collectAsStateWithLifecycle()
    val favoritePodcasts by viewModel.favoritePodcasts.collectAsStateWithLifecycle()
    val chartPlaylists by viewModel.chartPlaylists.collectAsStateWithLifecycle()
    val recentlyAdded by viewModel.recentlyAdded.collectAsStateWithLifecycle()
    val accountThumbnail by viewModel.accountThumbnail.collectAsStateWithLifecycle()
    val navigateToLibraryFilter by sharedViewModel.navigateToLibraryFilter.collectAsStateWithLifecycle()

    LaunchedEffect(navigateToLibraryFilter) {
        navigateToLibraryFilter?.let { filter ->
            viewModel.setCurrentScreen(filter)
            sharedViewModel.setNavigateToLibraryFilter(null)
        }
    }
    val hazeState =
        rememberHazeState(
            blurEnabled = true,
        )

    var topAppBarHeight by remember {
        mutableStateOf(0.dp)
    }
    var showAddSheet by remember { mutableStateOf(false) }

    LaunchedEffect(nowPlaying) {
        Logger.w("LibraryScreen", "Check nowPlaying: $nowPlaying")
        viewModel.getRecentlyAdded()
    }

    val chipRowState = rememberScrollState()
    val currentFilter by viewModel.currentScreen.collectAsStateWithLifecycle()

    LaunchedEffect(currentFilter) {
        when (currentFilter) {
            LibraryChipType.YOUTUBE_MUSIC_PLAYLIST -> {
                if (youTubePlaylist.data.isNullOrEmpty()) {
                    viewModel.getYouTubePlaylist()
                }
            }

            LibraryChipType.YOUTUBE_MIX_FOR_YOU -> {
                if (youTubeMixForYou.data.isNullOrEmpty()) {
                    viewModel.getYouTubeMixedForYou()
                }
            }

            LibraryChipType.YOUR_LIBRARY -> {
                viewModel.getCanvasSong()
                viewModel.getRecentlyAdded()
            }

            LibraryChipType.LOCAL_PLAYLIST -> {
                viewModel.getLocalPlaylist()
            }

            LibraryChipType.FAVORITE_PLAYLIST -> {
                viewModel.getPlaylistFavorite()
            }

            LibraryChipType.DOWNLOADED_PLAYLIST -> {
                viewModel.getDownloadedPlaylist()
            }

            LibraryChipType.FAVORITE_PODCAST -> {
                viewModel.getFavoritePodcasts()
            }

            LibraryChipType.CHART -> {
                if (chartPlaylists.data.isNullOrEmpty()) {
                    viewModel.getChartPlaylists()
                }
            }
        }
    }

    Crossfade(
        modifier = Modifier.hazeSource(hazeState),
        targetState = currentFilter,
    ) { filter ->
        when (filter) {
            LibraryChipType.YOUR_LIBRARY -> {
                val state = rememberLazyListState()
                val isScrollingUp by state.isScrollingUp()
                LaunchedEffect(state) {
                    snapshotFlow { state.firstVisibleItemIndex }
                        .collect {
                            if (it <= 1) {
                                onScrolling.invoke(true)
                            } else {
                                onScrolling.invoke(isScrollingUp)
                            }
                        }
                }
                LazyColumn(
                    contentPadding =
                        innerPadding.copy(
                            top = topAppBarHeight,
                        ),
                    state = state,
                ) {
                    item {
                        Text(
                            text = stringResource(Res.string.your_library),
                            style = typo().titleLarge,
                            color = Color.White,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                        )
                    }

                    if (listCanvasSong.data.isNullOrEmpty() && recentlyAdded.data.isNullOrEmpty() &&
                        listCanvasSong !is LocalResource.Loading && recentlyAdded !is LocalResource.Loading) {
                        item {
                            LibraryEmptyRecommendationBox(
                                iconRes = Res.drawable.round_library_music_24,
                                gradientColors = listOf(Color(0xFF3B82F6), Color(0xFF1E3A8A)),
                                emptyDescText = Res.string.library_your_library_desc,
                                emptyRecText = Res.string.library_your_library_rec
                            )
                        }
                    }

                    if (!listCanvasSong.data.isNullOrEmpty()) {
                        item {
                            LibraryItem(
                                state =
                                    LibraryItemState(
                                        type = LibraryItemType.CanvasSong,
                                        data = listCanvasSong.data ?: emptyList(),
                                        isLoading = listCanvasSong is LocalResource.Loading,
                                    ),
                                navController = navController,
                            )
                        }
                    }

                    item {
                        LibraryItem(
                            state =
                                LibraryItemState(
                                    type =
                                        LibraryItemType.RecentlyAdded(
                                            playingVideoId = nowPlaying,
                                        ),
                                    data = recentlyAdded.data ?: emptyList(),
                                    isLoading = recentlyAdded is LocalResource.Loading,
                                ),
                            navController = navController,
                        )
                    }
                    item {
                        EndOfPage()
                    }
                }
            }

            LibraryChipType.YOUTUBE_MUSIC_PLAYLIST -> {
                GridLibraryPlaylist(
                    navController = navController,
                    contentPadding = innerPadding.copy(top = topAppBarHeight),
                    data = youTubePlaylist,
                    titleText = Res.string.your_youtube_playlists,
                    iconRes = Res.drawable.baseline_queue_music_24,
                    gradientColors = listOf(Color(0xFFFF0000), Color(0xFF8B0000)),
                    emptyDescText = Res.string.library_yt_playlists_desc,
                    emptyRecText = Res.string.library_yt_playlists_rec,
                    onScrolling = onScrolling,
                ) {
                    viewModel.getYouTubePlaylist()
                }
            }

            LibraryChipType.YOUTUBE_MIX_FOR_YOU -> {
                GridLibraryPlaylist(
                    navController = navController,
                    contentPadding = innerPadding.copy(top = topAppBarHeight),
                    data = youTubeMixForYou,
                    titleText = Res.string.mix_for_you,
                    iconRes = Res.drawable.baseline_sensors_24,
                    gradientColors = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)),
                    emptyDescText = Res.string.library_mix_for_you_desc,
                    emptyRecText = Res.string.library_mix_for_you_rec,
                    onScrolling = onScrolling,
                ) {
                    viewModel.getYouTubeMixedForYou()
                }
            }

            LibraryChipType.LOCAL_PLAYLIST -> {
                GridLibraryPlaylist(
                    navController = navController,
                    contentPadding = innerPadding.copy(top = topAppBarHeight),
                    data = yourLocalPlaylist,
                    titleText = Res.string.your_playlists,
                    iconRes = Res.drawable.baseline_playlist_add_24,
                    gradientColors = listOf(Color(0xFF06B6D4), Color(0xFF0891B2)),
                    emptyDescText = Res.string.library_local_playlists_desc,
                    emptyRecText = Res.string.library_local_playlists_rec,
                    onScrolling = onScrolling,
                    createNewPlaylist = {
                        showAddSheet = true
                    },
                ) {
                    viewModel.getLocalPlaylist()
                }
            }

            LibraryChipType.FAVORITE_PLAYLIST -> {
                GridLibraryPlaylist(
                    navController = navController,
                    contentPadding = innerPadding.copy(top = topAppBarHeight),
                    data = favoritePlaylist,
                    titleText = Res.string.favorite_playlists,
                    iconRes = Res.drawable.baseline_favorite_24,
                    gradientColors = listOf(Color(0xFFFD297B), Color(0xFFFF5858)),
                    emptyDescText = Res.string.library_favorite_playlists_desc,
                    emptyRecText = Res.string.library_favorite_playlists_rec,
                    onScrolling = onScrolling,
                ) {
                    viewModel.getPlaylistFavorite()
                }
            }

            LibraryChipType.DOWNLOADED_PLAYLIST -> {
                GridLibraryPlaylist(
                    navController = navController,
                    contentPadding = innerPadding.copy(top = topAppBarHeight),
                    data = downloadedPlaylist,
                    titleText = Res.string.downloaded_playlists,
                    iconRes = Res.drawable.baseline_downloaded,
                    gradientColors = listOf(Color(0xFF10B981), Color(0xFF059669)),
                    emptyDescText = Res.string.library_downloaded_playlists_desc,
                    emptyRecText = Res.string.library_downloaded_playlists_rec,
                    onScrolling = onScrolling,
                ) {
                    viewModel.getDownloadedPlaylist()
                }
            }

            LibraryChipType.FAVORITE_PODCAST -> {
                GridLibraryPlaylist(
                    navController = navController,
                    contentPadding = innerPadding.copy(top = topAppBarHeight),
                    data = favoritePodcasts,
                    titleText = Res.string.favorite_podcasts,
                    iconRes = Res.drawable.ic_microphone,
                    gradientColors = listOf(Color(0xFFF59E0B), Color(0xFFD97706)),
                    emptyDescText = Res.string.library_favorite_podcasts_desc,
                    emptyRecText = Res.string.library_favorite_podcasts_rec,
                    onScrolling = onScrolling,
                ) {
                    viewModel.getFavoritePodcasts()
                }
            }

            LibraryChipType.CHART -> {
                GridLibraryPlaylist(
                    navController = navController,
                    contentPadding = innerPadding.copy(top = topAppBarHeight),
                    data = chartPlaylists,
                    titleText = Res.string.gratifymusic_charts,
                    iconRes = Res.drawable.baseline_trending_up_24,
                    gradientColors = listOf(Color(0xFFEC4899), Color(0xFFBE185D)),
                    emptyDescText = Res.string.library_charts_desc,
                    emptyRecText = Res.string.library_charts_rec,
                    onScrolling = onScrolling,
                ) {
                    viewModel.getChartPlaylists()
                }
            }
        }
    }
    val coroutineScope = rememberCoroutineScope()
    if (showAddSheet) {
        var newTitle by remember { mutableStateOf("") }
        val showAddSheetState =
            rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
            )
        val hideEditTitleBottomSheet: () -> Unit =
            {
                coroutineScope.launch {
                    showAddSheetState.hide()
                    showAddSheet = false
                }
            }
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = showAddSheetState,
            containerColor = Color.Transparent,
            contentColor = Color.Transparent,
            dragHandle = null,
            scrimColor = Color.Black.copy(alpha = .5f),
        ) {
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                colors = CardDefaults.cardColors().copy(containerColor = Color(0xFF242424)),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Card(
                        modifier =
                            Modifier
                                .width(60.dp)
                                .height(4.dp),
                        colors =
                            CardDefaults.cardColors().copy(
                                containerColor = Color(0xFF474545),
                            ),
                        shape = RoundedCornerShape(50),
                    ) {}
                    Spacer(modifier = Modifier.height(5.dp))
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { s -> newTitle = s },
                        label = {
                            Text(text = stringResource(Res.string.playlist_name))
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    TextButton(
                        onClick = {
                            if (newTitle.isBlank()) {
                                viewModel.makeToast(runBlocking { getString(Res.string.playlist_name_cannot_be_empty) })
                            } else {
                                viewModel.createPlaylist(newTitle)
                                hideEditTitleBottomSheet()
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .align(Alignment.CenterHorizontally),
                    ) {
                        Text(text = stringResource(Res.string.create))
                    }
                }
            }
        }
    }
    Column(
        Modifier
            .background(transparent)
            .hazeEffect(hazeState, style = HazeMaterials.ultraThin()) {
                blurEnabled = true
            }.onGloballyPositioned { coordinates ->
                topAppBarHeight = with(density) { coordinates.size.height.toDp() }
            },
    ) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(Res.string.library),
                    style = typo().titleMedium,
                    color = Color.White,
                )
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            actions = {
                IconButton(
                    onClick = {
                        navController.navigate(AnalyticsDestination)
                    },
                ) {
                    Box {
                        Icon(Icons.Rounded.AutoGraph, "Analytics", tint = Color.White)
                        Text(
                            "NEW",
                            Modifier.align(Alignment.BottomEnd),
                            style =
                                typo().bodySmall.copy(
                                    fontSize = 5.sp,
                                ),
                        )
                    }
                }
            },
            navigationIcon = {
                AnimatedVisibility(
                    !accountThumbnail.isNullOrEmpty(),
                    modifier = Modifier.padding(horizontal = 12.dp),
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    AsyncImage(
                        model =
                            ImageRequest
                                .Builder(LocalPlatformContext.current)
                                .data(accountThumbnail)
                                .crossfade(550)
                                .build(),
                        placeholder = painterResource(Res.drawable.baseline_people_alt_24),
                        error = painterResource(Res.drawable.baseline_people_alt_24),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(26.dp)
                                .clip(CircleShape),
                    )
                }
            },
        )
        Row(
            modifier =
                Modifier
                    .horizontalScroll(chipRowState)
                    .padding(horizontal = 15.dp)
                    .padding(bottom = 8.dp)
                    .background(Color.Transparent),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibraryChipType.entries.forEach { type ->
                if ((type == LibraryChipType.YOUTUBE_MUSIC_PLAYLIST || type == LibraryChipType.YOUTUBE_MIX_FOR_YOU) && !loggedIn) {
                    return@forEach
                }
                val isSelected = type == currentFilter
                val (iconRes, gradientColors) = when (type) {
                    LibraryChipType.YOUR_LIBRARY -> 
                        Res.drawable.round_library_music_24 to listOf(Color(0xFF3B82F6), Color(0xFF1E3A8A))
                    LibraryChipType.YOUTUBE_MUSIC_PLAYLIST -> 
                        Res.drawable.baseline_queue_music_24 to listOf(Color(0xFFFF0000), Color(0xFF8B0000))
                    LibraryChipType.YOUTUBE_MIX_FOR_YOU -> 
                        Res.drawable.baseline_sensors_24 to listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))
                    LibraryChipType.LOCAL_PLAYLIST -> 
                        Res.drawable.baseline_playlist_add_24 to listOf(Color(0xFF06B6D4), Color(0xFF0891B2))
                    LibraryChipType.FAVORITE_PLAYLIST -> 
                        Res.drawable.baseline_favorite_24 to listOf(Color(0xFFFD297B), Color(0xFFFF5858))
                    LibraryChipType.DOWNLOADED_PLAYLIST -> 
                        Res.drawable.baseline_downloaded to listOf(Color(0xFF10B981), Color(0xFF059669))
                    LibraryChipType.FAVORITE_PODCAST -> 
                        Res.drawable.ic_microphone to listOf(Color(0xFFF59E0B), Color(0xFFD97706))
                    LibraryChipType.CHART -> 
                        Res.drawable.baseline_trending_up_24 to listOf(Color(0xFFEC4899), Color(0xFFBE185D))
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .then(
                            if (isSelected) {
                                Modifier
                                    .angledGradientBackground(gradientColors, 25f)
                                    .border(
                                        width = 1.dp,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.4f),
                                                Color.White.copy(alpha = 0.1f)
                                            )
                                        ),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                            } else {
                                Modifier
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(
                                        width = 1.dp,
                                        color = Color.White.copy(alpha = 0.05f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                            }
                        )
                        .clickable {
                            viewModel.setCurrentScreen(type)
                        }
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = type.name,
                        tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}