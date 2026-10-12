package com.tan.gratify.ui.screen.playlist

import com.tan.domain.data.model.searchResult.songs.Artist
import com.tan.domain.data.model.searchResult.songs.Thumbnail
import androidx.compose.runtime.*
import androidx.compose.material3.*
import com.tan.gratify.ui.component.*
import com.tan.domain.utils.toSongEntity
import com.tan.gratify.viewModel.UIEvent
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.rounded.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

import com.tan.gratify.ui.theme.GratifyColors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.kmpalette.rememberPaletteState
import com.tan.common.Config
import com.tan.domain.data.entities.SharedPlaylist
import com.tan.domain.data.entities.SharedPlaylistTrack
import com.tan.domain.data.model.browse.album.Track
import com.tan.domain.data.model.social.CloudPlaylistDto
import com.tan.domain.data.model.social.CloudPlaylistItemDto
import com.tan.domain.mediaservice.handler.PlaylistType
import com.tan.domain.mediaservice.handler.QueueData
import com.tan.domain.repository.LocalPlaylistRepository
import com.tan.domain.repository.SharedPlaylistRepository
import com.tan.domain.repository.SocialRepository
import com.tan.gratify.expect.ui.toImageBitmap
import com.tan.gratify.extension.getColorFromPalette
import com.tan.gratify.ui.theme.typo
import com.tan.gratify.viewModel.SharedViewModel
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

fun SharedPlaylistTrack.toTrack(): Track {
    return Track(
        album = null,
        artists = listOf(Artist(name = this.artists, id = null)),
        duration = this.durationSeconds?.let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" },
        durationSeconds = this.durationSeconds,
        isAvailable = true,
        isExplicit = false,
        likeStatus = null,
        thumbnails = listOf(Thumbnail(url = "https://i.ytimg.com/vi/${this.videoId}/mqdefault.jpg", width = 320, height = 180)),
        title = this.title,
        videoId = this.videoId,
        videoType = "MUSIC_VIDEO_TYPE_ATV",
        category = null,
        feedbackTokens = null,
        resultType = "song",
        year = null
    )
}

@Composable
fun SharedPlaylistScreen(
    playlistId: String,
    navController: NavController,
    sharedViewModel: SharedViewModel = koinInject(),
    sharedPlaylistRepository: SharedPlaylistRepository = koinInject(),
    socialRepository: SocialRepository = koinInject(),
    localPlaylistRepository: LocalPlaylistRepository = koinInject(),
    supabase: io.github.jan.supabase.SupabaseClient = koinInject(),
) {
    val scope = rememberCoroutineScope()
    val authStatus by supabase.auth.sessionStatus.collectAsStateWithLifecycle()
    val currentUserId = remember(authStatus) { supabase.auth.currentUserOrNull()?.id }

    var playlist by remember(playlistId) { mutableStateOf<SharedPlaylist?>(null) }
    var tracks by remember(playlistId) { mutableStateOf<List<SharedPlaylistTrack>>(emptyList()) }
    var isLoading by remember(playlistId) { mutableStateOf(true) }
    var retry by remember(playlistId) { mutableStateOf(0) }
    var isSaved by remember(playlistId, currentUserId) { mutableStateOf(false) }
    var isSaving by remember(playlistId, currentUserId) { mutableStateOf(false) }
    var showDeleteDialog by remember(playlistId, currentUserId) { mutableStateOf(false) }
    var isDeleting by remember(playlistId, currentUserId) { mutableStateOf(false) }
    var chosenTrack by remember(playlistId, currentUserId) { mutableStateOf<Track?>(null) }
    var ownedLocalId by remember(playlistId, currentUserId) { mutableStateOf<Long?>(null) }
    val controller by sharedViewModel.controllerState.collectAsStateWithLifecycle()
    val queueData by sharedViewModel.getQueueDataState().collectAsStateWithLifecycle()
    val nowPlaying by sharedViewModel.nowPlayingState.collectAsStateWithLifecycle()
    val queueId = "SHARED_$playlistId"
    val domainTracks = remember(tracks) { tracks.map { it.toTrack() } }
    val paletteState = rememberPaletteState()
    var bitmap by remember(playlistId) { mutableStateOf<ImageBitmap?>(null) }
    var paletteGenerated by remember(playlistId) { mutableStateOf(false) }
    LaunchedEffect(bitmap) {
        if (bitmap != null && !paletteGenerated) {
            paletteState.generate(bitmap!!)
            paletteGenerated = true
        }
    }

    LaunchedEffect(playlistId, retry, currentUserId) {
        isLoading = true
        playlist = null
        tracks = emptyList()
        isSaved = false
        ownedLocalId = null
        try {
            withTimeout(20_000) {
                val result = sharedPlaylistRepository.getSharedPlaylist(playlistId)
                val resolved = result.getOrThrow()
                val sourceId = requireNotNull(resolved.id)
                tracks = sharedPlaylistRepository.getSharedPlaylistTracks(sourceId).first().getOrThrow()
                playlist = resolved
                isSaved = localPlaylistRepository.getLocalPlaylistBySourceSharedId(sourceId) != null
                if (resolved.userId == currentUserId && !resolved.clientSyncId.isNullOrBlank()) {
                    ownedLocalId = localPlaylistRepository.getAllLocalPlaylists().first()
                        .firstOrNull { it.sourceSharedPlaylistId == null && it.syncId == resolved.clientSyncId }?.id
                }
            }
        } catch (_: TimeoutCancellationException) {
            playlist = null
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            playlist = null
        } finally {
            isLoading = false
        }
    }
    if (isLoading) {
        CollectionDetailLoading(onBack = { navController.navigateUp() })
        return
    }
    val data = playlist
    if (data == null) {
        CollectionDetailError(onBack = { navController.navigateUp() }, onRetry = { retry++ })
        return
    }
    val playTrack: (Track, List<Track>) -> Unit = { first, queue ->
        sharedViewModel.setQueueData(QueueData.Data(listTracks = queue.toCollection(ArrayList()), firstPlayedTrack = first,
            playlistId = queueId, playlistName = data.title, playlistType = PlaylistType.PLAYLIST, continuation = null))
        sharedViewModel.loadMediaItemFromTrack(first, type = Config.PLAYLIST_CLICK,
            index = queue.indexOfFirst { it.videoId == first.videoId }.coerceAtLeast(0))
    }
    val onSave: () -> Unit = {
        if (!isSaving) {
            val owner = currentUserId
            if (owner == null) sharedViewModel.makeToast("Masuk untuk menyimpan playlist.")
            else {
            isSaving = true
            scope.launch {
                try {
                    val sourceId = requireNotNull(data.id)
                    if (!isSaved) {
                        localPlaylistRepository.saveSharedPlaylistToLibrary(sharedPlaylistId = sourceId, title = data.title,
                            thumbnail = data.thumbnailUrl, tracks = domainTracks, creatorName = data.creatorName)
                        isSaved = true
                    } else {
                        sharedPlaylistRepository.removePlaylistSave(sourceId, owner).getOrThrow()
                        localPlaylistRepository.getLocalPlaylistBySourceSharedId(sourceId)?.let {
                            val result = localPlaylistRepository.deleteLocalPlaylist(it.id, "Removed").first { it !is com.tan.domain.utils.LocalResource.Loading }
                            check(result is com.tan.domain.utils.LocalResource.Success) { "Local removal failed" }
                        }
                        isSaved = false
                    }
                    // Count only confirmed unique saves; never guess with +/- 1.
                    sharedPlaylistRepository.getSharedPlaylist(sourceId).getOrNull()?.let { playlist = it }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    isSaved = localPlaylistRepository.getLocalPlaylistBySourceSharedId(requireNotNull(data.id)) != null
                    sharedViewModel.makeToast("Gagal memperbarui koleksi. Coba lagi.")
                } finally { isSaving = false }
            }
            }
        }
    }
    val lazyState = rememberLazyListState()
    val collapsed by remember { derivedStateOf { lazyState.firstVisibleItemIndex > 0 } }
    Box(Modifier.fillMaxSize().background(GratifyColors.Background)) {
        LazyColumn(Modifier.fillMaxSize(), state = lazyState, contentPadding = PaddingValues(bottom = 120.dp)) {
            item(key = "collection-header", contentType = "header") {
                CollectionDetailHeader(title = data.title, kind = stringResource(Res.string.playlist),
                    subtitle = data.creatorName.orEmpty(),
                    metadata = stringResource(Res.string.album_length, tracks.size.toString(), "") + " • ${data.addCount} " + stringResource(Res.string.detail_saves),
                    accent = paletteState.palette?.getColorFromPalette() ?: playlistTitleGradient(data.title).first(),
                    onBack = { navController.navigateUp() }, isPlaying = controller.isPlaying && queueData?.data?.playlistId == queueId,
                    canPlay = domainTracks.isNotEmpty(),
                    onPlay = {
                        if (queueData?.data?.playlistId == queueId) sharedViewModel.onUIEvent(UIEvent.PlayPause)
                        else domainTracks.firstOrNull()?.let { playTrack(it, domainTracks) }
                    },
                    onShuffle = { domainTracks.shuffled().let { queue -> queue.firstOrNull()?.let { playTrack(it, queue) } } },
                    artwork = { modifier ->
                        if (isAutoAssignedOrNullThumbnail(data.thumbnailUrl) && tracks.isNotEmpty()) {
                            PlaylistCollageThumbnail(tracks = tracks.take(4).map { it.videoId }, placeholderTitle = data.title,
                                onSuccessFirstCell = { bitmap = it.image.toImageBitmap() }, modifier = modifier)
                        } else AsyncImage(model = ImageRequest.Builder(LocalPlatformContext.current).data(data.thumbnailUrl).size(512, 512).build(),
                            placeholder = painterPlaylistThumbnail(data.title, style = typo().labelMedium, 250.dp to 250.dp),
                            error = painterPlaylistThumbnail(data.title, style = typo().labelMedium, 250.dp to 250.dp),
                            contentDescription = data.title, contentScale = ContentScale.Fit,
                            onSuccess = { bitmap = it.result.image.toImageBitmap() }, modifier = modifier)
                    },
                    actions = {
                        if (data.userId != currentUserId) CollectionDetailAction(
                            if (isSaved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            stringResource(if (isSaved) Res.string.detail_remove_saved else Res.string.detail_save),
                            selected = isSaved, enabled = !isSaving, onClick = onSave)
                        else CollectionDetailAction(Icons.Rounded.VisibilityOff, stringResource(Res.string.hide_from_public), enabled = !isDeleting, onClick = { showDeleteDialog = true })
                    },
                )
            }
            ownedLocalId?.let { localId -> item("manage-owned") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.playlist_manage_owned_public_hint), color = GratifyColors.TextSecondary,
                        style = typo().bodyMedium)
                    OutlinedButton(onClick = { navController.navigate(com.tan.gratify.ui.navigation.destination.list.LocalPlaylistDestination(localId)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.playlist_manage_edit))
                    }
                }
            } }
            if (tracks.isEmpty()) item { CollectionDetailEmpty() }
            itemsIndexed(domainTracks, key = { index, track -> "${track.videoId}_$index" }, contentType = { _, _ -> "song" }) { _, track ->
                SongFullWidthItems(collectionStyle = true, track = track, isPlaying = controller.isPlaying && nowPlaying?.songEntity?.videoId == track.videoId,
                    onClickListener = { playTrack(track, domainTracks) }, onMoreClickListener = { chosenTrack = track },
                    onAddToQueue = { sharedViewModel.addListToQueue(arrayListOf(track)) }, modifier = Modifier.fillMaxWidth())
            }
        }
        androidx.compose.animation.AnimatedVisibility(collapsed, enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
            CollectionDetailTopBar(data.title, onBack = { navController.navigateUp() })
        }
    }
    chosenTrack?.let { track ->
        NowPlayingBottomSheet(onDismiss = { chosenTrack = null }, navController = navController, song = track.toSongEntity())
    }
    if (showDeleteDialog) AlertDialog(onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
        title = { Text(stringResource(Res.string.hide_from_public)) }, text = { Text(stringResource(Res.string.playlist_manage_public_hint)) },
        confirmButton = {
            TextButton(enabled = !isDeleting, onClick = {
                isDeleting = true
                scope.launch {
                    try {
                        withTimeout(20_000) { sharedPlaylistRepository.deleteSharedPlaylist(playlistId).getOrThrow() }
                        navController.navigateUp()
                    } catch (_: TimeoutCancellationException) { sharedViewModel.makeToast("Koneksi terlalu lama. Coba lagi.") }
                    catch (error: CancellationException) { throw error }
                    catch (_: Exception) { sharedViewModel.makeToast("Gagal menyembunyikan playlist. Coba lagi.") }
                    finally { isDeleting = false; showDeleteDialog = false }
                }
            }) { Text(stringResource(Res.string.hide_from_public)) }
        }, dismissButton = { TextButton(enabled = !isDeleting, onClick = { showDeleteDialog = false }) { Text(stringResource(Res.string.cancel)) } })
}
