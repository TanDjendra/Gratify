package com.tan.gratify.ui.screen.playlist

import androidx.compose.runtime.*
import androidx.compose.material3.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import gratify.composeapp.generated.resources.*
import androidx.compose.foundation.layout.*
import com.tan.gratify.ui.component.*
import androidx.compose.material.icons.rounded.*

import com.tan.gratify.ui.theme.GratifyColors
import com.tan.gratify.ui.navigation.destination.list.ArtistDestination

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kmpalette.rememberPaletteState
import com.tan.domain.data.entities.DownloadState
import com.tan.domain.data.model.browse.album.Track
import com.tan.domain.utils.toSongEntity
import com.tan.logger.Logger
import com.tan.gratify.expect.ui.toImageBitmap
import com.tan.gratify.extension.getColorFromPalette
import com.tan.gratify.extension.getScreenSizeInfo
import com.tan.gratify.ui.theme.md_theme_dark_background
import com.tan.gratify.ui.theme.typo
import com.tan.gratify.viewModel.ListState
import com.tan.gratify.viewModel.PlaylistUIEvent
import com.tan.gratify.viewModel.PlaylistUIState
import com.tan.gratify.viewModel.PlaylistViewModel
import com.tan.gratify.viewModel.SharedViewModel
import com.tan.gratify.viewModel.UIEvent
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    viewModel: PlaylistViewModel = koinViewModel(),
    sharedViewModel: SharedViewModel = koinInject(),
    playlistId: String,
    isYourYouTubePlaylist: Boolean,
    navController: NavController,
) {
    val tag = "PlaylistScreen"

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val continuation by viewModel.continuation.collectAsStateWithLifecycle()
    val listColors by viewModel.listColors.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val liked by viewModel.liked.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val tracksListState by viewModel.tracksListState.collectAsStateWithLifecycle()

    var showSearchBar by rememberSaveable { mutableStateOf(false) }
    var searchBarHeightPx by remember { mutableStateOf(0) }

    val lazyState = rememberLazyListState()
    val firstItemVisible by remember {
        derivedStateOf {
            lazyState.firstVisibleItemIndex == 0
        }
    }
    var shouldHideTopBar by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    val filteredTrack by remember {
        derivedStateOf {
            if (query.isEmpty() || !showSearchBar) {
                tracks
            } else {
                tracks.filter {
                    it.title.contains(query, ignoreCase = true) ||
                        it.artists?.joinToString(", ")?.contains(query, ignoreCase = true) == true
                }
            }
        }
    }

    LaunchedEffect(uiState) {
        Logger.d(tag, "uiState hash: ${uiState.hashCode()}")
        Logger.d(tag, "uiState data: ${uiState.data}")
    }

    LaunchedEffect(showSearchBar) {
        if (showSearchBar) {
            viewModel.getFullTracks {}
            lazyState.animateScrollToItem(0)
        }
    }

    val shouldStartPaginate =
        remember {
            derivedStateOf {
                tracksListState != ListState.PAGINATION_EXHAUST &&
                    (
                        lazyState.layoutInfo.visibleItemsInfo
                            .lastOrNull()
                            ?.index ?: -9
                    ) >= (lazyState.layoutInfo.totalItemsCount - 6)
            }
        }

    LaunchedEffect(key1 = shouldStartPaginate.value) {
        Logger.d(tag, "shouldStartPaginate: ${shouldStartPaginate.value}")
        Logger.d(tag, "tracksListState: $tracksListState")
        Logger.d(tag, "Continuation: $continuation")
        if (shouldStartPaginate.value && tracksListState == ListState.IDLE) {
            viewModel.getContinuationTrack(
                playlistId,
                continuation,
            )
        }
    }

    val queueData by sharedViewModel.getQueueDataState().collectAsStateWithLifecycle()
    val playingPlaylistId by remember {
        derivedStateOf {
            queueData?.data?.playlistId
        }
    }

    val playingTrack by sharedViewModel.nowPlayingState
        .mapLatest {
            it?.songEntity
        }.collectAsState(initial = null)
    val isPlaying by sharedViewModel.controllerState.map { it.isPlaying }.collectAsState(initial = false)

    var currentItem by remember {
        mutableStateOf<Track?>(null)
    }

    var itemBottomSheetShow by remember {
        mutableStateOf(false)
    }
    var playlistBottomSheetShow by remember {
        mutableStateOf(false)
    }

    val onPlaylistItemClick: (videoId: String) -> Unit = { videoId ->
        viewModel.onUIEvent(
            PlaylistUIEvent.ItemClick(
                videoId = videoId,
            ),
        )
    }
    val onItemMoreClick: (videoId: String) -> Unit = { videoId ->
        currentItem = tracks.firstOrNull { it.videoId == videoId }
        if (currentItem != null) {
            itemBottomSheetShow = true
        }
    }
    val onPlaylistMoreClick: () -> Unit = {
        playlistBottomSheetShow = true
    }

    LaunchedEffect(key1 = playlistId) {
        if (playlistId != uiState.data?.id) {
            Logger.w(tag, "new id: $playlistId")
            viewModel.getData(playlistId)
        }
    }
    LaunchedEffect(key1 = firstItemVisible) {
        shouldHideTopBar = !firstItemVisible
    }
    val paletteState = rememberPaletteState()
    var bitmap by remember {
        mutableStateOf<ImageBitmap?>(null)
    }
    // Track which thumbnail URL we've already extracted a palette from.
    // Prevents palette flash when LazyColumn recycles the header item on scroll —
    // AsyncImage re-mount fires onSuccess again, but we skip the regenerate.
    var paletteGeneratedFor by remember {
        mutableStateOf<String?>(null)
    }
    val currentThumbnail = (uiState as? PlaylistUIState.Success)?.data?.thumbnail

    LaunchedEffect(bitmap) {
        val bm = bitmap
        if (bm != null && currentThumbnail != null && paletteGeneratedFor != currentThumbnail) {
            paletteState.generate(bm)
            paletteGeneratedFor = currentThumbnail
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { paletteState.palette }
            .distinctUntilChanged()
            .collectLatest {
                viewModel.setBrush(listOf(it.getColorFromPalette(), md_theme_dark_background))
            }
    }

    val isMobilePortrait = getScreenSizeInfo().wDP < 600

    // Loading dialog
    val showLoadingDialog by viewModel.showLoadingDialog.collectAsStateWithLifecycle()
    if (showLoadingDialog.first) {
        LoadingDialog(
            true,
            showLoadingDialog.second,
        )
    }
    Crossfade(
        targetState = uiState,
    ) { state ->
        Logger.w(tag, "State hash: ${state.hashCode()}")
        when (state) {
            is PlaylistUIState.Success -> {
                val data = state.data
                Logger.d(tag, "data: $data")
                if (data == null) return@Crossfade
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(GratifyColors.Background),
                    state = lazyState,
                ) {
                    if (!showSearchBar) {
                        item(key = "collection-header", contentType = "header") {
                            val uriHandler = LocalUriHandler.current
                            CollectionDetailHeader(
                                title = data.title,
                                kind = stringResource(Res.string.playlist),
                                subtitle = data.author.name,
                                onSubtitleClick = if (data.author.id.isBlank()) null else ({ navController.navigate(ArtistDestination(data.author.id)) }),
                                metadata = if (data.isRadio) stringResource(Res.string.unlimited) else stringResource(Res.string.album_length, data.trackCount.toString(), ""),
                                accent = listColors.firstOrNull() ?: GratifyColors.SurfaceRaised,
                                onBack = { navController.navigateUp() },
                                isPlaying = isPlaying && playingPlaylistId == data.id,
                                canPlay = tracks.isNotEmpty(),
                                onPlay = {
                                    if (playingPlaylistId == data.id) sharedViewModel.onUIEvent(UIEvent.PlayPause)
                                    else viewModel.onUIEvent(PlaylistUIEvent.PlayAll)
                                },
                                onShuffle = { viewModel.onUIEvent(PlaylistUIEvent.Shuffle) },
                                artwork = { modifier ->
                                    AsyncImage(model = ImageRequest.Builder(LocalPlatformContext.current).data(data.thumbnail).size(512, 512).crossfade(true).build(),
                                        placeholder = painterResource(Res.drawable.holder), error = painterResource(Res.drawable.holder),
                                        contentDescription = data.title, contentScale = ContentScale.Fit,
                                        onSuccess = { bitmap = it.result.image.toImageBitmap() }, modifier = modifier)
                                },
                                description = if (data.description.isNullOrBlank()) null else ({
                                    DescriptionView(text = data.description.orEmpty(), limitLine = 2, onTimeClicked = {}, onURLClicked = { uriHandler.openUri(it) })
                                }),
                                actions = {
                                    if (!data.isRadio) {
                                        CollectionSaveAction(liked, onClick = { viewModel.onUIEvent(PlaylistUIEvent.Favorite) })
                                        CollectionDownloadAction(downloadState, enabled = tracks.isNotEmpty(), onClick = {
                                            if (downloadState != DownloadState.STATE_DOWNLOADED) viewModel.onUIEvent(PlaylistUIEvent.Download)
                                        })
                                    }
                                    CollectionDetailAction(Icons.Rounded.MoreHoriz, stringResource(Res.string.detail_options), onClick = onPlaylistMoreClick)
                                },
                            )
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { showSearchBar = true }) {
                                    Icon(Icons.Rounded.Search, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(Res.string.detail_search_tracks), color = GratifyColors.TextSecondary)
                                }
                                Spacer(Modifier.weight(1f))
                                if (!data.isRadio) CollectionDetailAction(Icons.Rounded.Sensors, stringResource(Res.string.radio), onClick = { viewModel.onUIEvent(PlaylistUIEvent.StartRadio) })
                            }
                        }
                    } else {
                        item { Spacer(Modifier.height(with(LocalDensity.current) { searchBarHeightPx.toDp() })) }
                    }
                    if (filteredTrack.isEmpty() && tracksListState != ListState.LOADING) item {
                        CollectionDetailEmpty(isFiltered = showSearchBar && query.isNotBlank())
                    }
                    items(count = filteredTrack.size, key = { index ->
                        val item = filteredTrack.getOrNull(index)
                        (item?.videoId ?: "") + "item_$index"
                    }) { index ->
                        val item = filteredTrack.getOrNull(index)
                        if (item != null) {
                            Column(modifier = Modifier.animateItem()) {
                                if (playingTrack?.videoId == item.videoId && isPlaying) {
                                    SongFullWidthItems(collectionStyle = true,
                                        isPlaying = true,
                                        track = item,
                                        onMoreClickListener = { onItemMoreClick(it) },
                                        onClickListener = {
                                            Logger.w("PlaylistScreen", "index: $index")
                                            onPlaylistItemClick(it)
                                        },
                                        onAddToQueue = {
                                            sharedViewModel.addListToQueue(
                                                arrayListOf(item),
                                            )
                                        },
                                        modifier = Modifier,
                                    )
                                } else {
                                    SongFullWidthItems(collectionStyle = true,
                                        isPlaying = false,
                                        track = item,
                                        onMoreClickListener = { onItemMoreClick(it) },
                                        onClickListener = {
                                            Logger.w("PlaylistScreen", "index: $index")
                                            onPlaylistItemClick(it)
                                        },
                                        onAddToQueue = {
                                            sharedViewModel.addListToQueue(
                                                arrayListOf(item),
                                            )
                                        },
                                        modifier = Modifier,
                                    )
                                }
                            }
                        }
                    }
                    when (tracksListState) {
                        ListState.IDLE -> {
                            // DO NOTHING
                            item {
                                EndOfPage()
                            }
                        }

                        ListState.LOADING, ListState.PAGINATING -> {
                            item {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CenterLoadingBox(
                                        modifier = Modifier.size(80.dp),
                                    )
                                }
                            }
                            item {
                                EndOfPage()
                            }
                        }

                        ListState.ERROR -> {
                            item {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .height(64.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(Res.string.error),
                                        style = typo().bodyMedium,
                                    )
                                }
                            }
                            item {
                                EndOfPage()
                            }
                        }

                        ListState.PAGINATION_EXHAUST -> {
                            item {
                                EndOfPage()
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = showSearchBar,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically(),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { searchBarHeightPx = it.size.height }
                            .then(
                                if (isMobilePortrait) {
                                    Modifier.background(GratifyColors.Background)
                                } else {
                                    Modifier.background(GratifyColors.Background)
                                },
                            ),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                                    .windowInsetsPadding(WindowInsets.statusBars),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RippleIconButton(
                                resId = Res.drawable.baseline_arrow_back_ios_new_24,
                            ) {
                                navController.navigateUp()
                            }
                            SearchBar(
                                modifier =
                                    Modifier
                                        .height(50.dp)
                                        .padding(horizontal = 12.dp)
                                        .weight(1f),
                                colors =
                                    SearchBarDefaults.colors().copy(
                                        containerColor = Color.Transparent,
                                    ),
                                inputField = {
                                    CompositionLocalProvider(LocalTextStyle provides typo().bodySmall) {
                                        SearchBarDefaults.InputField(
                                            query = query,
                                            onQueryChange = { query = it },
                                            onSearch = { showSearchBar = false },
                                            expanded = showSearchBar,
                                            onExpandedChange = { showSearchBar = it },
                                            placeholder = {
                                                Text(
                                                    stringResource(Res.string.search),
                                                    style = typo().bodyMedium,
                                                )
                                            },
                                        )
                                    }
                                },
                                expanded = false,
                                onExpandedChange = {},
                                windowInsets = WindowInsets(0, 0, 0, 0),
                            ) {
                            }
                            IconButton(
                                onClick = {
                                    showSearchBar = !showSearchBar
                                },
                            ) {
                                Icon(Icons.Rounded.Close, null, tint = Color.White)
                            }
                        }
                    }
                }

                if (itemBottomSheetShow && currentItem != null) {
                    val track = currentItem?.toSongEntity() ?: return@Crossfade
                    NowPlayingBottomSheet(
                        onDismiss = {
                            itemBottomSheetShow = false
                            currentItem = null
                        },
                        navController = navController,
                        song = track,
                    )
                }
                if (playlistBottomSheetShow) {
                    Logger.w("PlaylistScreen", "PlaylistBottomSheet")
                    val addToQueue = {
                        viewModel.getFullTracks { track ->
                            sharedViewModel.addListToQueue(
                                track.toCollection(arrayListOf()),
                            )
                        }
                    }
                    PlaylistBottomSheet(
                        onDismiss = { playlistBottomSheetShow = false },
                        playlistId = data.id,
                        playlistName = data.title,
                        isYourYouTubePlaylist = isYourYouTubePlaylist && !data.isRadio,
                        onSaveToLocal = {
                            viewModel.getFullTracks { track ->
                                viewModel.saveToLocal(track)
                            }
                        },
                        onEditTitle = { newTitle ->
                            viewModel.updatePlaylistTitle(newTitle, data.id)
                        },
                        onAddToQueue = if (data.isRadio) null else addToQueue,
                    )
                }
                AnimatedVisibility(visible = shouldHideTopBar && !showSearchBar, enter = fadeIn(), exit = fadeOut()) {
                    CollectionDetailTopBar(data.title, onBack = { navController.navigateUp() }, actions = {
                        CollectionDetailAction(Icons.Rounded.Search, stringResource(Res.string.detail_search_tracks), onClick = { showSearchBar = true })
                    })
                }
            }

            is PlaylistUIState.Loading -> {
                CollectionDetailLoading(onBack = { navController.navigateUp() })
            }
            is PlaylistUIState.Error -> {
                CollectionDetailError(onBack = { navController.navigateUp() }, onRetry = { viewModel.getData(playlistId) })
            }
        }
    }
}
