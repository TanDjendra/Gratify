package com.tan.gratify.ui.screen.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.*
import androidx.compose.material3.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import gratify.composeapp.generated.resources.*
import androidx.compose.foundation.layout.*
import com.tan.gratify.ui.component.*
import com.tan.gratify.viewModel.LocalPlaylistState
import androidx.compose.material.icons.rounded.*

import com.tan.gratify.ui.theme.GratifyColors

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.sharp.Sort
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import com.tan.gratify.expect.ui.toImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kmpalette.rememberPaletteState
import com.tan.common.LOCAL_PLAYLIST_ID
import com.tan.domain.data.entities.DownloadState
import com.tan.domain.data.entities.LocalPlaylistEntity
import com.tan.domain.data.entities.PairSongLocalPlaylist
import com.tan.domain.data.entities.SongEntity
import com.tan.domain.utils.FilterState
import com.tan.domain.utils.toTrack
import com.tan.logger.Logger
import com.tan.gratify.Platform
import com.tan.gratify.extension.displayNameRes
import com.tan.gratify.extension.getColorFromPalette
import com.tan.gratify.getPlatform
import com.tan.gratify.ui.theme.md_theme_dark_background
import com.tan.gratify.ui.theme.typo
import com.tan.gratify.viewModel.LocalPlaylistUIEvent
import com.tan.gratify.viewModel.LocalPlaylistViewModel
import com.tan.gratify.viewModel.SharedViewModel
import com.tan.gratify.viewModel.UIEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val TAG = "LocalPlaylistScreen"

@ExperimentalFoundationApi
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalCoroutinesApi::class,
)
@Composable
fun LocalPlaylistScreen(
    id: Long,
    sharedViewModel: SharedViewModel = koinInject(),
    viewModel: LocalPlaylistViewModel = koinViewModel(),
    navController: NavController,
) {

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val lazyState = rememberLazyListState()
    val firstItemVisible by remember {
        derivedStateOf {
            lazyState.firstVisibleItemIndex == 0
        }
    }
    val downloadState by viewModel.uiState.map { it.downloadState }.collectAsState(
        initial = DownloadState.STATE_NOT_DOWNLOADED,
    )
    var shouldHideTopBar by rememberSaveable { mutableStateOf(false) }
    var shouldShowSuggestions by rememberSaveable { mutableStateOf(false) }
    var shouldShowSuggestButton by rememberSaveable { mutableStateOf(false) }

    val playingTrack by sharedViewModel.nowPlayingState
        .mapLatest {
            it?.songEntity
        }.collectAsState(initial = null)
    val isPlaying by sharedViewModel.controllerState.map { it.isPlaying }.collectAsState(initial = false)

    val queueData by sharedViewModel.getQueueDataState().collectAsStateWithLifecycle()
    val playingPlaylistId by remember {
        derivedStateOf {
            queueData?.data?.playlistId
        }
    }

    val suggestedTracks by viewModel.uiState.map { it.suggestions?.songs ?: emptyList() }.collectAsState(initial = emptyList())
    val suggestionsLoading by viewModel.loading.collectAsStateWithLifecycle()
    var showSyncAlertDialog by rememberSaveable { mutableStateOf(false) }
    var showUnsyncAlertDialog by rememberSaveable { mutableStateOf(false) }
    var firstTimeGetLocalPlaylist by rememberSaveable {
        mutableStateOf(false)
    }

    var currentItem by remember {
        mutableStateOf<SongEntity?>(null)
    }

    var itemBottomSheetShow by remember {
        mutableStateOf(false)
    }
    var playlistBottomSheetShow by remember {
        mutableStateOf(false)
    }

    var sortBottomSheetShow by remember {
        mutableStateOf(false)
    }

    var changingOrder by remember {
        mutableStateOf(false)
    }

    val trackPagingItems: LazyPagingItems<Pair<SongEntity, PairSongLocalPlaylist>> = viewModel.tracksPagingState.collectAsLazyPagingItems()
    LaunchedEffect(Unit) {
        snapshotFlow {
            trackPagingItems.loadState
        }.collectLatest {
            Logger.d("PlaylistScreen", "loadState: ${trackPagingItems.loadState}")
            viewModel.setLazyTrackPagingItems(trackPagingItems)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            firstTimeGetLocalPlaylist = true
        }
    }

    val onPlaylistItemClick: (videoId: String) -> Unit = { videoId ->
        viewModel.onUIEvent(
            LocalPlaylistUIEvent.ItemClick(
                videoId = videoId,
            ),
        )
    }
    val onItemMoreClick: (videoId: String) -> Unit = { videoId ->
        currentItem = trackPagingItems.itemSnapshotList.findLast { it?.first?.videoId == videoId }?.first
        if (currentItem != null) {
            itemBottomSheetShow = true
        }
    }
    val onPlaylistMoreClick: () -> Unit = {
        playlistBottomSheetShow = true
    }

    LaunchedEffect(key1 = shouldShowSuggestions) {
        if (shouldShowSuggestions && suggestedTracks.isEmpty() && uiState.syncState != LocalPlaylistEntity.YouTubeSyncState.NotSynced) {
            viewModel.getSuggestions(uiState.id)
        }
    }

    LaunchedEffect(key1 = id) {
        if (id != uiState.id) {
            Logger.w("PlaylistScreen", "new id: $id")
            viewModel.setOffset(0)
            viewModel.removeListSuggestion()
            viewModel.updatePlaylistState(id, true)
            firstTimeGetLocalPlaylist = true
        }
    }
    LaunchedEffect(key1 = uiState) {
        shouldShowSuggestButton =
            !uiState.ytPlaylistId.isNullOrEmpty() &&
            uiState.syncState == LocalPlaylistEntity.YouTubeSyncState.Synced
    }
    LaunchedEffect(key1 = firstItemVisible) {
        shouldHideTopBar = !firstItemVisible
    }
    val paletteState = rememberPaletteState()
    var bitmap by remember {
        mutableStateOf<ImageBitmap?>(null)
    }
    // Track which thumbnail we've already extracted a palette from.
    // Prevents palette flash when LazyColumn recycles the header item on scroll —
    // AsyncImage re-mount fires onSuccess again, but we skip the regenerate.
    var paletteGeneratedFor by remember {
        mutableStateOf<String?>(null)
    }
    val currentThumbnail = uiState.thumbnail

    LaunchedEffect(bitmap) {
        val bm = bitmap
        if (bm != null && paletteGeneratedFor != currentThumbnail) {
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

    // Loading dialog
    val showLoadingDialog by viewModel.showLoadingDialog.collectAsStateWithLifecycle()
    if (showLoadingDialog.first) {
        LoadingDialog(
            true,
            showLoadingDialog.second,
        )
    }
    val coroutineScope = rememberCoroutineScope()
    val dragDropState =
        rememberDragDropState(lazyState) { from, to ->
            coroutineScope.launch {
                Logger.d(TAG, "onMove from $from to $to")
                viewModel.changeLocalPlaylistItemPosition(from - 1, to - 1)
                trackPagingItems.refresh()
            }
        }
    var overscrollJob by remember { mutableStateOf<Job?>(null) }
    if (uiState.loadState == LocalPlaylistState.PlaylistLoadState.Loading) {
        CollectionDetailLoading(onBack = { navController.navigateUp() })
        return
    }
    if (uiState.loadState == LocalPlaylistState.PlaylistLoadState.Error) {
        CollectionDetailError(onBack = { navController.navigateUp() }, onRetry = { viewModel.updatePlaylistState(id, true) })
        return
    }
    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(GratifyColors.Background)

                .pointerInput(changingOrder) {
                    if (!changingOrder) return@pointerInput
                    val onDrag: (change: androidx.compose.ui.input.pointer.PointerInputChange, offset: Offset) -> Unit =
                        { change, offset ->
                            Logger.d(TAG, "onDrag $offset")
                            change.consume()
                            dragDropState.onDrag(offset = offset)

                            if (overscrollJob?.isActive != true) {
                                dragDropState
                                    .checkForOverScroll()
                                    .takeIf { it != 0f }
                                    ?.let {
                                        overscrollJob =
                                            coroutineScope.launch {
                                                dragDropState.state.animateScrollBy(
                                                    it * 1.3f,
                                                    tween(easing = FastOutLinearInEasing),
                                                )
                                            }
                                    }
                                    ?: run { overscrollJob?.cancel() }
                            }
                        }
                    val onDragStart: (Offset) -> Unit = { offset ->
                        Logger.d(TAG, "onDragStart $offset")
                        dragDropState.onDragStart(offset)
                    }
                    val onDragEnd: () -> Unit = {
                        Logger.d(TAG, "onDragEnd")
                        dragDropState.onDragInterrupted(true)
                        overscrollJob?.cancel()
                    }
                    val onDragCancel: () -> Unit = {
                        Logger.d(TAG, "onDragCancel")
                        dragDropState.onDragInterrupted()
                        overscrollJob?.cancel()
                    }
                    if (getPlatform() == Platform.Desktop) {
                        // Desktop: normal drag (click-and-drag), no long press needed
                        detectDragGestures(
                            onDrag = onDrag,
                            onDragStart = onDragStart,
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragCancel,
                        )
                    } else {
                        // Android: long press then drag (touch-friendly)
                        detectDragGesturesAfterLongPress(
                            onDrag = onDrag,
                            onDragStart = onDragStart,
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragCancel,
                        )
                    }
                },
        state = lazyState,
    ) {
        item(key = "collection-header", contentType = "header") {
            CollectionDetailHeader(
                title = uiState.title,
                kind = stringResource(Res.string.playlist),
                subtitle = uiState.creatorName?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.your_playlist),
                metadata = stringResource(Res.string.album_length, uiState.trackCount.toString(), ""),
                accent = paletteState.palette?.getColorFromPalette() ?: playlistTitleGradient(uiState.title).first(),
                onBack = { navController.navigateUp() },
                isPlaying = isPlaying && playingPlaylistId == LOCAL_PLAYLIST_ID + uiState.id,
                canPlay = uiState.trackCount > 0 && !uiState.isUnavailable,
                onPlay = {
                    if (playingPlaylistId == LOCAL_PLAYLIST_ID + uiState.id) sharedViewModel.onUIEvent(UIEvent.PlayPause)
                    else viewModel.onUIEvent(LocalPlaylistUIEvent.PlayClick)
                },
                onShuffle = { viewModel.onUIEvent(LocalPlaylistUIEvent.ShuffleClick) },
                artwork = { modifier ->
                    if (isAutoAssignedOrNullThumbnail(uiState.thumbnail, uiState.ytPlaylistId) && uiState.top4Tracks.isNotEmpty()) {
                        PlaylistCollageThumbnail(tracks = uiState.top4Tracks, placeholderTitle = uiState.title,
                            onSuccessFirstCell = { bitmap = it.image.toImageBitmap() }, modifier = modifier)
                    } else {
                        AsyncImage(model = ImageRequest.Builder(LocalPlatformContext.current).data(uiState.thumbnail).size(512, 512).crossfade(true).build(),
                            placeholder = painterPlaylistThumbnail(uiState.title, style = typo().labelMedium, 250.dp to 250.dp),
                            error = painterPlaylistThumbnail(uiState.title, style = typo().labelMedium, 250.dp to 250.dp),
                            fallback = painterPlaylistThumbnail(uiState.title, style = typo().labelMedium, 250.dp to 250.dp),
                            contentDescription = uiState.title, contentScale = ContentScale.Fit,
                            onSuccess = { bitmap = it.result.image.toImageBitmap() }, modifier = modifier)
                    }
                },
                actions = {
                    CollectionDownloadAction(downloadState, enabled = uiState.trackCount > 0 && !uiState.isUnavailable, onClick = {
                        if (downloadState != DownloadState.STATE_DOWNLOADED) viewModel.downloadFullPlaylist()
                    })
                    if (shouldShowSuggestButton) CollectionDetailAction(Icons.Rounded.AutoAwesome, stringResource(Res.string.suggest),
                        selected = shouldShowSuggestions, onClick = { shouldShowSuggestions = !shouldShowSuggestions })
                    if (!uiState.isReadOnly) CollectionDetailAction(Icons.Rounded.MoreHoriz, stringResource(Res.string.detail_options), onClick = onPlaylistMoreClick)
                },
            )
            if (uiState.isUnavailable) Text(stringResource(Res.string.detail_unavailable), color = GratifyColors.TextSecondary,
                style = typo().bodyMedium, modifier = Modifier.fillMaxWidth().padding(16.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { sortBottomSheetShow = true }) {
                    Icon(Icons.AutoMirrored.Sharp.Sort, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.sort_by) + ": " + stringResource(uiState.filterState.displayNameRes()), color = GratifyColors.TextSecondary)
                }
                Spacer(Modifier.weight(1f))
                if (uiState.filterState == FilterState.CustomOrder && !uiState.isReadOnly && !uiState.isUnavailable) {
                    TextButton(onClick = { changingOrder = !changingOrder }) {
                        Text(stringResource(if (changingOrder) Res.string.detail_done else Res.string.detail_reorder))
                    }
                }
            }
            AnimatedVisibility(visible = shouldShowSuggestions) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.suggest), style = typo().titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.reloadSuggestion() }) { Text(stringResource(Res.string.reload)) }
                    }
                    if (suggestionsLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else suggestedTracks.forEach { track ->
                        SuggestItems(track = track, isPlaying = isPlaying && playingTrack?.videoId == track.videoId,
                            onAddClickListener = { viewModel.addSuggestTrackToListTrack(track) },
                            onClickListener = { viewModel.onUIEvent(LocalPlaylistUIEvent.SuggestionsItemClick(track.videoId)) })
                    }
                }
            }
            if (uiState.trackCount == 0 && trackPagingItems.loadState.refresh !is LoadState.Loading) CollectionDetailEmpty()
        }
        items(count = trackPagingItems.itemCount, key = { index ->
            val item = trackPagingItems[index]
            (item?.first?.videoId ?: "") + "item_$index" + item?.second?.inPlaylist + item?.second?.position
        }) { index ->
            val item = trackPagingItems[index]?.first
            if (item != null) {
                val content = @Composable { mod: Modifier ->
                    if (playingTrack?.videoId == item.videoId && isPlaying) {
                        SongFullWidthItems(collectionStyle = true,
                            isPlaying = true,
                            shouldShowDragHandle = changingOrder,
                            songEntity = item,
                            onMoreClickListener = { onItemMoreClick(it) },
                            onClickListener = {
                                Logger.w("PlaylistScreen", "index: $index")
                                onPlaylistItemClick(it)
                            },
                            onAddToQueue = {
                                sharedViewModel.addListToQueue(
                                    arrayListOf(item.toTrack()),
                                )
                            },
                            modifier = mod,
                        )
                    } else {
                        SongFullWidthItems(collectionStyle = true,
                            isPlaying = false,
                            shouldShowDragHandle = changingOrder,
                            songEntity = item,
                            onMoreClickListener = { onItemMoreClick(it) },
                            onClickListener = {
                                Logger.w("PlaylistScreen", "index: $index")
                                onPlaylistItemClick(it)
                            },
                            onAddToQueue = {
                                sharedViewModel.addListToQueue(
                                    arrayListOf(item.toTrack()),
                                )
                            },
                            modifier = mod,
                        )
                    }
                }
                if (changingOrder) {
                    DraggableItem(
                        dragDropState,
                        index + 1,
                        Modifier.animateItem(),
                    ) {
                        content(Modifier)
                    }
                } else {
                    Column(modifier = Modifier.animateItem()) {
                        content(Modifier)
                    }
                }
            }
        }
        trackPagingItems.apply {
            item {
                AnimatedVisibility(loadState.refresh is LoadState.Loading || loadState.append is LoadState.Loading) {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(modifier = Modifier.height(15.dp))
                        CenterLoadingBox(modifier = Modifier.size(80.dp))
                        Spacer(modifier = Modifier.height(15.dp))
                    }
                }
            }
        }
        item {
            EndOfPage()
        }
    }
    if (itemBottomSheetShow && currentItem != null) {
        val track = currentItem ?: return
        NowPlayingBottomSheet(
            onDelete = if (uiState.isReadOnly) null else { { viewModel.deleteItem(uiState.id, track) } },
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
        LocalPlaylistBottomSheet(
            isBottomSheetVisible = playlistBottomSheetShow,
            onDismiss = { playlistBottomSheetShow = false },
            title = uiState.title,
            ytPlaylistId = uiState.ytPlaylistId,
            isPublic = uiState.isPublic,
            isReadOnly = uiState.isReadOnly,
            onEditTitle =
                { newTitle ->
                    viewModel.updatePlaylistTitle(newTitle, uiState.id)
                },
            onEditThumbnail =
                { thumbUri ->
                    viewModel.updatePlaylistThumbnail(thumbUri, uiState.id)
                },
            onAddToQueue = {
                viewModel.addAllToQueue()
            },
            onSync = {
                if (uiState.syncState == LocalPlaylistEntity.YouTubeSyncState.Synced) {
                    showUnsyncAlertDialog = true
                } else {
                    showSyncAlertDialog = true
                }
            },
            onUpdatePlaylist = {
                viewModel.updateListTrackSynced(uiState.id)
            },
            onDelete = {
                playlistBottomSheetShow = false
                viewModel.deletePlaylist(uiState.id) {
                    navController.popBackStack()
                }
            },
            onShareToPublic = {
                viewModel.sharePlaylistToPublic()
            },
            onHideFromPublic = {
                viewModel.unsharePlaylistFromPublic()
            }
        )
    }
    if (showSyncAlertDialog) {
        AlertDialog(
            title = { Text(text = stringResource(Res.string.warning)) },
            text = { Text(text = stringResource(Res.string.sync_playlist_warning)) },
            onDismissRequest = { showSyncAlertDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.syncPlaylistWithYouTubePlaylist(uiState.id)
                    showSyncAlertDialog = false
                }) {
                    Text(text = stringResource(Res.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSyncAlertDialog = false
                }) {
                    Text(text = stringResource(Res.string.cancel))
                }
            },
        )
    }
    if (showUnsyncAlertDialog) {
        AlertDialog(
            title = { Text(text = stringResource(Res.string.warning)) },
            text = { Text(text = stringResource(Res.string.unsync_playlist_warning)) },
            onDismissRequest = { showUnsyncAlertDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.unsyncPlaylistWithYouTubePlaylist(uiState.id)
                    showUnsyncAlertDialog = false
                }) {
                    Text(text = stringResource(Res.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUnsyncAlertDialog = false
                }) {
                    Text(text = stringResource(Res.string.cancel))
                }
            },
        )
    }
    if (sortBottomSheetShow) {
        SortPlaylistBottomSheet(
            selectedState = uiState.filterState,
            onDismiss = { sortBottomSheetShow = false },
            onSortChanged = {
                viewModel.onUIEvent(LocalPlaylistUIEvent.ChangeFilter(it))
                sortBottomSheetShow = false
            },
        )
    }
    AnimatedVisibility(visible = shouldHideTopBar, enter = fadeIn(), exit = fadeOut()) {
        CollectionDetailTopBar(uiState.title, onBack = { navController.navigateUp() }, actions = {
            if (!uiState.isReadOnly) CollectionDetailAction(Icons.Rounded.MoreHoriz, stringResource(Res.string.detail_options), onClick = onPlaylistMoreClick)
        })
    }
}
