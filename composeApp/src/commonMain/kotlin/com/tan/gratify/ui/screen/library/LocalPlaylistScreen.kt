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
import com.tan.gratify.ui.navigation.openPlaylistSongPicker
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
    ownerMember:com.tan.gratify.viewModel.WorkspaceMember?=null,
    collaborationBusy:Boolean=false,
    canInvite:Boolean=false,
    onInvite:(()->Unit)?=null,
) {

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val shareTitle = stringResource(Res.string.share_url)
    val editStatus by viewModel.editStatus.collectAsStateWithLifecycle()
    var showEditPlaylist by rememberSaveable(id) { mutableStateOf(false) }
    var showVisibility by rememberSaveable(id) { mutableStateOf(false) }
    var visibilityWasBusy by remember(id) { mutableStateOf(false) }
    var confirmDelete by rememberSaveable(id) { mutableStateOf(false) }
    var draftCover by rememberSaveable(id) { mutableStateOf<String?>(null) }
    val coverPicker = com.tan.gratify.expect.ui.photoPickerResult { uri ->
        if (showEditPlaylist && uri != null) draftCover = uri
    }
    fun editPlaylist() { viewModel.clearEditError(); draftCover = null; showEditPlaylist = true }
    LaunchedEffect(uiState.visibilityBusy) {
        if (uiState.visibilityBusy) visibilityWasBusy = true
        else if (visibilityWasBusy) {
            if (uiState.visibilityKnown && !uiState.visibilityFailed) showVisibility = false
            visibilityWasBusy = false
        }
    }
    val additionsModel: com.tan.gratify.viewModel.AddSongsToPlaylistViewModel = koinViewModel(key = "playlist-discovery-$id")
    val additions by additionsModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(id, uiState.id, uiState.trackCount, uiState.isReadOnly, uiState.isUnavailable) {
        if (uiState.id == id && !uiState.isReadOnly && !uiState.isUnavailable) {
            if (additionsModel.uiState.value.targetPlaylistId == null) additionsModel.bindTargetPlaylist(id)
            else if (additionsModel.uiState.value.pendingTrackIds.isEmpty() &&
                uiState.trackCount != additionsModel.uiState.value.existingTrackIds.size) additionsModel.loadTargetPlaylist()
        }
    }
    LaunchedEffect(additions.membershipChangeCount) {
        if (additions.membershipChangeCount > 0) viewModel.updatePlaylistState(id, true)
    }
    val playlistEntry = remember(navController, id) { navController.currentBackStackEntry }
    val tracksChanged by (playlistEntry?.savedStateHandle?.getStateFlow("playlist_tracks_changed", false)
        ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsStateWithLifecycle()
    LaunchedEffect(tracksChanged) {
        if (tracksChanged) {
            viewModel.updatePlaylistState(id, true)
            additionsModel.loadTargetPlaylist()
            playlistEntry?.savedStateHandle?.set("playlist_tracks_changed", false)
        }
    }

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
                compactPlaylist = !uiState.isReadOnly,
                onEdit = if (!uiState.isReadOnly && !uiState.isUnavailable) ::editPlaylist else null,
                title = uiState.title,
                kind = stringResource(Res.string.playlist),
                subtitle = uiState.creatorName?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.your_playlist),
                subtitleContent=if(!uiState.isReadOnly&&onInvite!=null) ({PlaylistMemberRow(
                    listOfNotNull(ownerMember),canInvite&&!collaborationBusy,onInvite,onInvite)}) else null,
                metadata = stringResource(Res.string.album_length, uiState.trackCount.toString(), "") +
                    if (uiState.isReadOnly || uiState.isPublic) " • ${uiState.addCount} " + stringResource(Res.string.detail_saves) else "",
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
                    if (uiState.isReadOnly) CollectionDetailAction(Icons.Rounded.Favorite,
                        stringResource(Res.string.detail_remove_saved), selected=true, onClick={ viewModel.deletePlaylist(uiState.id) { navController.navigateUp() } })
                    if (uiState.trackCount > 0) CollectionDownloadAction(downloadState, enabled = uiState.trackCount > 0 && !uiState.isUnavailable, onClick = {
                        if (downloadState != DownloadState.STATE_DOWNLOADED) viewModel.downloadFullPlaylist()
                    })
                    if (shouldShowSuggestButton) CollectionDetailAction(Icons.Rounded.AutoAwesome, stringResource(Res.string.suggest),
                        selected = shouldShowSuggestions, onClick = { shouldShowSuggestions = !shouldShowSuggestions })
                    if (!uiState.isReadOnly) CollectionDetailAction(Icons.Rounded.MoreHoriz, stringResource(Res.string.detail_options), onClick = onPlaylistMoreClick)
                },
            )
            if (!uiState.isReadOnly && !uiState.isUnavailable) OwnedPlaylistVisibility(uiState.isPublic, uiState.visibilityKnown) {
                showVisibility = true
            }
            OwnedPlaylistAddSongsAction(uiState.isReadOnly, uiState.isUnavailable) {
                navController.openPlaylistSongPicker(uiState.id, uiState.title)
            }
            if (uiState.isUnavailable) Text(stringResource(Res.string.detail_unavailable), color = GratifyColors.TextSecondary,
                style = typo().bodyMedium, modifier = Modifier.fillMaxWidth().padding(16.dp))
            if (uiState.trackCount > 0) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { sortBottomSheetShow = true }, enabled = !changingOrder) {
                    Icon(Icons.AutoMirrored.Sharp.Sort, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.sort_by) + ": " + stringResource(uiState.filterState.displayNameRes()), color = GratifyColors.TextSecondary)
                }
                Spacer(Modifier.weight(1f))
                if (!uiState.isReadOnly && !uiState.isUnavailable) {
                    TextButton(onClick = {
                        if (!changingOrder && uiState.filterState != FilterState.CustomOrder)
                            viewModel.onUIEvent(LocalPlaylistUIEvent.ChangeFilter(FilterState.CustomOrder))
                        changingOrder = !changingOrder
                    }) {
                        Text(stringResource(if (changingOrder) Res.string.detail_done else Res.string.playlist_manage_edit_songs))
                    }
                }
            }
            if (changingOrder) Text(stringResource(Res.string.playlist_manage_edit_songs_hint),
                color = GratifyColors.TextSecondary, style = typo().bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
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
            if (uiState.trackCount == 0 && trackPagingItems.loadState.refresh !is LoadState.Loading && uiState.isReadOnly) CollectionDetailEmpty()
        }
        items(count = trackPagingItems.itemCount, key = { index ->
            val item = trackPagingItems[index]
            item?.first?.videoId ?: "placeholder_$index"
        }) { index ->
            val item = trackPagingItems[index]?.first
            if (item != null) {
                val content = @Composable { mod: Modifier ->
                    if (playingTrack?.videoId == item.videoId && isPlaying) {
                        SongFullWidthItems(collectionStyle = true,
                            isPlaying = true,
                            shouldShowDragHandle = changingOrder,
                            songEntity = item,
                            onMoreClickListener = if (changingOrder) null else { videoId -> onItemMoreClick(videoId) },
                            rightView = if (changingOrder) ({
                                IconButton(onClick = { viewModel.deleteItem(id, item) }) {
                                    Icon(Icons.Rounded.RemoveCircleOutline, stringResource(Res.string.playlist_builder_remove_song, item.title), tint = GratifyColors.Error)
                                }
                            }) else null,
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
                            onMoreClickListener = if (changingOrder) null else { videoId -> onItemMoreClick(videoId) },
                            rightView = if (changingOrder) ({
                                IconButton(onClick = { viewModel.deleteItem(id, item) }) {
                                    Icon(Icons.Rounded.RemoveCircleOutline, stringResource(Res.string.playlist_builder_remove_song, item.title), tint = GratifyColors.Error)
                                }
                            }) else null,
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
        if (!uiState.isReadOnly && !uiState.isUnavailable && !changingOrder) item("owned-recommendations") {
            PlaylistRecommendationsContent(additions, additionsModel::appendTrack, additionsModel::loadRecommendations)
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
    if (playlistBottomSheetShow && !uiState.isReadOnly && !uiState.isUnavailable) {
        PlaylistOptionsSheet(uiState.title, uiState.trackCount, uiState.isPublic, uiState.visibilityKnown, uiState.ytPlaylistId,
            onDismiss = { playlistBottomSheetShow = false },
            onAdd = { playlistBottomSheetShow = false; navController.openPlaylistSongPicker(id, uiState.title) },
            onEdit = { playlistBottomSheetShow = false; editPlaylist() },
            onVisibility = { playlistBottomSheetShow = false; showVisibility = true },
            onQueue = { playlistBottomSheetShow = false; viewModel.addAllToQueue() },
            onSync = {
                playlistBottomSheetShow = false
                if (uiState.syncState == LocalPlaylistEntity.YouTubeSyncState.Synced) showUnsyncAlertDialog = true
                else showSyncAlertDialog = true
            },
            onRefresh = { playlistBottomSheetShow = false; viewModel.updateListTrackSynced(id) },
            onDelete = { playlistBottomSheetShow = false; confirmDelete = true },
            onOpenPublic = uiState.sharedPlaylistId?.takeIf { it.isNotBlank() }?.let { sharedId -> {
                playlistBottomSheetShow = false
                navController.navigate(com.tan.gratify.ui.navigation.destination.list.SharedPlaylistDestination(sharedId))
            } }, onShareYouTube = uiState.ytPlaylistId?.let { youtubeId -> {
                com.tan.gratify.expect.shareUrl(shareTitle, "https://gratify.org/app/playlist?list=${youtubeId.removePrefix("VL")}")
            } })
    }
    if (showEditPlaylist && !uiState.isReadOnly) PlaylistEditDialog(uiState.title, editStatus.saving, editStatus.failed,
        onSave = { viewModel.savePlaylistDetails(it, draftCover, id) { showEditPlaylist = false; draftCover = null } },
        onCover = { coverPicker.launch() }, onDismiss = { showEditPlaylist = false; draftCover = null },
        coverPreview = draftCover ?: uiState.thumbnail, coverChanged = draftCover != null)
    if (showVisibility && !uiState.isReadOnly) PlaylistVisibilityDialog(uiState.isPublic, uiState.visibilityKnown,
        uiState.visibilityBusy, uiState.visibilityFailed,
        onConfirm = { if (uiState.isPublic) viewModel.unsharePlaylistFromPublic() else viewModel.sharePlaylistToPublic() },
        onRetry = { viewModel.updatePlaylistState(id) }, onDismiss = { showVisibility = false })
    if (confirmDelete) PlaylistDeleteDialog(uiState.title, uiState.isPublic,
        onDelete = { confirmDelete = false; viewModel.deletePlaylist(id) { navController.navigateUp() } },
        onDismiss = { confirmDelete = false })
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
