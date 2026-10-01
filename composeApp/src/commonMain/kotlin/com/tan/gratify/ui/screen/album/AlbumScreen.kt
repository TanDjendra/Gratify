package com.tan.gratify.ui.screen.album

import androidx.compose.runtime.*
import androidx.compose.material3.*
import gratify.composeapp.generated.resources.*
import androidx.compose.foundation.layout.*
import com.tan.gratify.ui.component.*
import androidx.compose.material.icons.rounded.*

import com.tan.gratify.ui.theme.GratifyColors

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.tan.gratify.expect.ui.toImageBitmap
import com.tan.gratify.extension.getColorFromPalette
import com.tan.gratify.ui.navigation.destination.list.AlbumDestination
import com.tan.gratify.ui.navigation.destination.list.ArtistDestination
import com.tan.gratify.ui.theme.md_theme_dark_background
import com.tan.gratify.ui.theme.typo
import com.tan.gratify.viewModel.AlbumViewModel
import com.tan.gratify.viewModel.LocalPlaylistState
import com.tan.gratify.viewModel.SharedViewModel
import com.tan.gratify.viewModel.UIEvent
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    browseId: String,
    navController: NavController,
    viewModel: AlbumViewModel = koinViewModel(),
    sharedViewModel: SharedViewModel = koinInject(),
) {
    val uriHandler = LocalUriHandler.current

    val playingVideoId by viewModel.nowPlayingVideoId.collectAsStateWithLifecycle()

    val isPlaying by sharedViewModel.controllerState.collectAsStateWithLifecycle()
    val queueData by sharedViewModel.getQueueDataState().collectAsStateWithLifecycle()
    val playingPlaylistId by remember {
        derivedStateOf {
            queueData?.data?.playlistId
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showBottomSheet by rememberSaveable { mutableStateOf(false) }
    var albumBottomSheetShow by rememberSaveable { mutableStateOf(false) }
    var chosenSong: Track? by remember { mutableStateOf(null) }

    LaunchedEffect(browseId) {
        viewModel.updateBrowseId(browseId)
    }

    val lazyState = rememberLazyListState()
    val firstItemVisible by remember {
        derivedStateOf {
            lazyState.firstVisibleItemIndex == 0
        }
    }
    var shouldHideTopBar by rememberSaveable { mutableStateOf(false) }
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

    LaunchedEffect(bitmap) {
        val bm = bitmap
        if (bm != null && paletteGeneratedFor != uiState.thumbnail) {
            paletteState.generate(bm)
            paletteGeneratedFor = uiState.thumbnail
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { paletteState.palette }
            .distinctUntilChanged()
            .collectLatest {
                viewModel.setBrush(listOf(it.getColorFromPalette(), md_theme_dark_background))
            }
    }

    Crossfade(uiState.loadState) {
        when (it) {
            LocalPlaylistState.PlaylistLoadState.Success -> {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(GratifyColors.Background)
                            ,
                    state = lazyState,
                ) {
                    item(key = "collection-header", contentType = "header") {
                        CollectionDetailHeader(
                            title = uiState.title,
                            kind = stringResource(Res.string.album),
                            subtitle = uiState.artist.name,
                            metadata = listOf(uiState.year, stringResource(Res.string.album_length, uiState.listTrack.size.toString(), uiState.length)).filter { it.isNotBlank() }.joinToString(" • "),
                            accent = uiState.colors.firstOrNull() ?: GratifyColors.SurfaceRaised,
                            onBack = { navController.navigateUp() },
                            isPlaying = isPlaying.isPlaying && playingPlaylistId == browseId.replaceFirst("VL", ""),
                            canPlay = uiState.listTrack.isNotEmpty(),
                            onPlay = {
                                if (playingPlaylistId == browseId.replaceFirst("VL", "")) sharedViewModel.onUIEvent(UIEvent.PlayPause)
                                else uiState.listTrack.firstOrNull()?.let(viewModel::playTrack)
                            },
                            onShuffle = { viewModel.shuffle() },
                            onSubtitleClick = uiState.artist.id?.let { id -> { navController.navigate(ArtistDestination(channelId = id)) } },
                            artwork = { modifier ->
                                AsyncImage(model = ImageRequest.Builder(LocalPlatformContext.current).data(uiState.thumbnail).size(512, 512).crossfade(true).build(),
                                    placeholder = painterResource(Res.drawable.holder), error = painterResource(Res.drawable.holder),
                                    contentDescription = uiState.title, contentScale = ContentScale.Fit,
                                    onSuccess = { bitmap = it.result.image.toImageBitmap() }, modifier = modifier)
                            },
                            description = if (uiState.description.isNullOrBlank()) null else ({
                                DescriptionView(text = uiState.description.orEmpty(), limitLine = 2, onTimeClicked = {}, onURLClicked = { uriHandler.openUri(it) })
                            }),
                            actions = {
                                CollectionSaveAction(uiState.liked, onClick = { viewModel.setAlbumLike() })
                                CollectionDownloadAction(uiState.downloadState, enabled = uiState.listTrack.isNotEmpty(), onClick = {
                                    if (uiState.downloadState != DownloadState.STATE_DOWNLOADED) viewModel.downloadFullAlbum()
                                })
                                CollectionDetailAction(Icons.Rounded.MoreHoriz, stringResource(Res.string.detail_options), onClick = { albumBottomSheetShow = true })
                            },
                        )
                    }
                    if (uiState.listTrack.isEmpty()) item { CollectionDetailEmpty() }
                    items(count = uiState.listTrack.size, key = { index ->
                        val item = uiState.listTrack.getOrNull(index)
                        item?.videoId + "item_$index"
                    }) { index ->
                        val item = uiState.listTrack.getOrNull(index)
                        if (item != null) {
                            Column(modifier = Modifier.animateItem()) {
                                SongFullWidthItems(collectionStyle = true,
                                    isPlaying = isPlaying.isPlaying && item.videoId == playingVideoId,
                                    index = index,
                                    track = item,
                                    onMoreClickListener = {
                                        chosenSong = item
                                        showBottomSheet = true
                                    },
                                    onClickListener = {
                                        viewModel.playTrack(item)
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
                    item(contentType = "other_version") {
                        AnimatedVisibility(uiState.otherVersion.isNotEmpty()) {
                            Column {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = stringResource(Res.string.other_version),
                                    style = typo().labelMedium,
                                    modifier =
                                        Modifier.padding(
                                            horizontal = 24.dp,
                                            vertical = 8.dp,
                                        ),
                                )
                                LazyRow(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                ) {
                                    items(uiState.otherVersion) { album ->
                                        HomeItemContentPlaylist(
                                            onClick = {
                                                navController.navigate(
                                                    AlbumDestination(
                                                        browseId = album.browseId,
                                                    ),
                                                )
                                            },
                                            data = album,
                                            thumbSize = 180.dp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item {
                        EndOfPage()
                    }
                }
                AnimatedVisibility(visible = shouldHideTopBar, enter = fadeIn(), exit = fadeOut()) {
                    CollectionDetailTopBar(uiState.title, onBack = { navController.navigateUp() }, actions = {
                        CollectionDetailAction(Icons.Rounded.MoreHoriz, stringResource(Res.string.detail_options), onClick = { albumBottomSheetShow = true })
                    })
                }
                if (showBottomSheet) {
                    NowPlayingBottomSheet(
                        onDismiss = {
                            showBottomSheet = false
                            chosenSong = null
                        },
                        navController = navController,
                        song = chosenSong?.toSongEntity(),
                    )
                }
                if (albumBottomSheetShow) {
                    PlaylistBottomSheet(
                        onDismiss = { albumBottomSheetShow = false },
                        playlistId = browseId,
                        playlistName = uiState.title,
                        isYourYouTubePlaylist = false,
                        onSaveToLocal = {},
                        onAddToQueue = {
                            sharedViewModel.addListToQueue(
                                uiState.listTrack.toCollection(arrayListOf()),
                            )
                        },
                    )
                }
            }

            LocalPlaylistState.PlaylistLoadState.Error -> {
                CollectionDetailError(onBack = { navController.navigateUp() }, onRetry = { viewModel.updateBrowseId(browseId) })
            }
            LocalPlaylistState.PlaylistLoadState.Loading -> {
                CollectionDetailLoading(onBack = { navController.navigateUp() })
            }
        }
    }
}
