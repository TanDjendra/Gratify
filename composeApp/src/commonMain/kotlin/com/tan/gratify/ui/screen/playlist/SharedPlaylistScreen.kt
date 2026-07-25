package com.tan.gratify.ui.screen.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.kmpalette.rememberPaletteState
import com.tan.common.Config
import com.tan.domain.data.entities.SharedPlaylist
import com.tan.domain.data.entities.SharedPlaylistTrack
import com.tan.domain.data.model.browse.album.Track
import com.tan.domain.data.model.searchResult.songs.Artist
import com.tan.domain.data.model.searchResult.songs.Thumbnail
import com.tan.domain.data.model.social.CloudPlaylistDto
import com.tan.domain.data.model.social.CloudPlaylistItemDto
import com.tan.domain.mediaservice.handler.PlaylistType
import com.tan.domain.mediaservice.handler.QueueData
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.repository.LocalPlaylistRepository
import com.tan.domain.repository.SharedPlaylistRepository
import com.tan.domain.repository.SocialRepository
import com.tan.gratify.Platform
import com.tan.gratify.expect.ui.layerBackdrop
import com.tan.gratify.expect.ui.rememberBackdrop
import com.tan.gratify.expect.ui.toImageBitmap
import com.tan.gratify.extension.angledGradientBackground
import com.tan.gratify.extension.getColorFromPalette
import com.tan.gratify.extension.getScreenSizeInfo
import com.tan.gratify.getPlatform
import com.tan.gratify.ui.component.CenterLoadingBox
import com.tan.gratify.ui.component.LiquidGlassIconButton
import com.tan.gratify.ui.component.PlaylistCollageThumbnail
import com.tan.gratify.ui.component.RippleIconButton
import com.tan.gratify.ui.component.SongFullWidthItems
import com.tan.gratify.ui.component.isAutoAssignedOrNullThumbnail
import com.tan.gratify.ui.component.liquidGlass
import com.tan.gratify.ui.component.painterPlaylistThumbnail
import com.tan.gratify.ui.component.playlistTitleGradient
import com.tan.gratify.ui.theme.md_theme_dark_background
import com.tan.gratify.ui.theme.typo
import com.tan.gratify.viewModel.SharedViewModel
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.baseline_arrow_back_ios_new_24
import gratify.composeapp.generated.resources.baseline_downloaded
import gratify.composeapp.generated.resources.baseline_playlist_add_24
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedPlaylistScreen(
    playlistId: String,
    navController: NavController,
    sharedViewModel: SharedViewModel = koinInject(),
    sharedPlaylistRepository: SharedPlaylistRepository = koinInject(),
    socialRepository: SocialRepository = koinInject(),
    localPlaylistRepository: LocalPlaylistRepository = koinInject(),
    supabase: io.github.jan.supabase.SupabaseClient = koinInject()
) {
    val scope = rememberCoroutineScope()
    var playlist by remember { mutableStateOf<SharedPlaylist?>(null) }
    var tracks by remember { mutableStateOf<List<SharedPlaylistTrack>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val currentUserId = remember {
        try { supabase.auth.currentUserOrNull()?.id } catch(e: Exception) { null }
    }

    val dataStoreManager: DataStoreManager = koinInject()

    val paletteState = rememberPaletteState()
    val hazeState = rememberHazeState(blurEnabled = true)
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(bitmap) {
        val bm = bitmap
        if (bm != null) {
            paletteState.generate(bm)
        }
    }

    val screenInfo = getScreenSizeInfo()
    val isMobilePortrait = getPlatform() == Platform.Android && screenInfo.wDP < screenInfo.hDP
    val mutedPaletteBg =
        run {
            val palette = paletteState.palette
            val base =
                if (palette != null) {
                    palette.getColorFromPalette()
                } else {
                    val titleColors = playlistTitleGradient(playlist?.title ?: "")
                    if (titleColors.size >= 2) {
                        lerp(titleColors[0], titleColors[1], 0.5f)
                    } else {
                        titleColors.firstOrNull() ?: md_theme_dark_background
                    }
                }
            lerp(base, md_theme_dark_background, 0.3f)
        }

    LaunchedEffect(playlistId) {
        isLoading = true
        try {
            val fetchedPlaylistResult = sharedPlaylistRepository.getSharedPlaylist(playlistId)
            val fetchedPlaylist = fetchedPlaylistResult.getOrNull()
            val fetchedTracksFlow = sharedPlaylistRepository.getSharedPlaylistTracks(playlistId)
            val fetchedTracks = fetchedTracksFlow.first().getOrNull() ?: emptyList()

            if (fetchedPlaylist != null && fetchedTracks.isNotEmpty()) {
                playlist = fetchedPlaylist
                tracks = fetchedTracks
            } else {
                try {
                    val dtos = supabase.postgrest["cloud_playlists"]
                        .select {
                            filter {
                                eq("id", playlistId)
                            }
                        }.decodeList<CloudPlaylistDto>()
                    val dto = dtos.firstOrNull()
                    if (dto != null) {
                        playlist = SharedPlaylist(
                            id = dto.id ?: playlistId,
                            userId = dto.userId ?: "",
                            title = dto.title,
                            thumbnailUrl = dto.thumbnailUrl,
                            creatorName = null,
                            createdAt = null
                        )
                        val itemDtos = supabase.postgrest["cloud_playlist_items"]
                            .select {
                                filter {
                                    eq("playlist_id", playlistId)
                                }
                            }.decodeList<CloudPlaylistItemDto>()
                        val trackList = mutableListOf<SharedPlaylistTrack>()
                        itemDtos.forEachIndexed { idx, item ->
                            trackList.add(
                                SharedPlaylistTrack(
                                    id = item.id ?: "${playlistId}_$idx",
                                    playlistId = playlistId,
                                    videoId = item.videoId,
                                    title = item.title,
                                    artists = item.artist,
                                    durationSeconds = item.duration,
                                    createdAt = null
                                )
                            )
                        }
                        tracks = trackList
                    }
                } catch(ex: Exception) {
                    ex.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CenterLoadingBox(modifier = Modifier.size(48.dp))
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isMobilePortrait) mutedPaletteBg else Color.Black)
            .hazeSource(hazeState),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item(contentType = "header") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .background(Color.Transparent)
            ) {
                if (!isMobilePortrait) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .angledGradientBackground(playlistTitleGradient(playlist?.title ?: ""), 25f)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color(0x75000000), Color.Black)
                                    )
                                )
                        )
                    }
                }
                Column(Modifier.background(Color.Transparent)) {
                    if (!isMobilePortrait) {
                        Row(
                            modifier = Modifier
                                .wrapContentWidth()
                                .padding(16.dp)
                                .windowInsetsPadding(WindowInsets.statusBars)
                        ) {
                            RippleIconButton(resId = Res.drawable.baseline_arrow_back_ios_new_24) {
                                navController.navigateUp()
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.Start) {
                        if (isMobilePortrait) {
                            val artworkBackdrop = rememberBackdrop()
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((screenInfo.hDP / 2).dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize().layerBackdrop(artworkBackdrop)) {
                                    if (isAutoAssignedOrNullThumbnail(playlist?.thumbnailUrl) && tracks.isNotEmpty()) {
                                        PlaylistCollageThumbnail(
                                            tracks = tracks.take(4).map { it.videoId },
                                            placeholderTitle = playlist?.title ?: "",
                                            onSuccessFirstCell = { res ->
                                                bitmap = res.image.toImageBitmap()
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalPlatformContext.current)
                                                .data(playlist?.thumbnailUrl)
                                                .diskCachePolicy(CachePolicy.ENABLED)
                                                .build(),
                                            placeholder = painterPlaylistThumbnail(playlist?.title ?: "", style = typo().labelMedium, 250.dp to 250.dp),
                                            error = painterPlaylistThumbnail(playlist?.title ?: "", style = typo().labelMedium, 250.dp to 250.dp),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            onSuccess = {
                                                bitmap = it.result.image.toImageBitmap()
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                            .align(Alignment.BottomCenter)
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(
                                                        Color.Transparent,
                                                        Color.Transparent,
                                                        mutedPaletteBg.copy(alpha = 0.5f),
                                                        mutedPaletteBg
                                                    )
                                                )
                                            )
                                    )
                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp)
                                            .padding(bottom = 16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = playlist?.title ?: "Shared Playlist",
                                            style = typo().titleLarge,
                                            color = Color.White,
                                            maxLines = 2,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Playlist dari ${playlist?.creatorName ?: "Anonim"}",
                                            style = typo().titleSmall,
                                            color = Color.White,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                        .windowInsetsPadding(WindowInsets.statusBars),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RippleIconButton(
                                        resId = Res.drawable.baseline_arrow_back_ios_new_24,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        navController.navigateUp()
                                    }
                                    Spacer(Modifier.weight(1f))
                                    if (playlist != null && currentUserId == playlist?.userId) {
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    sharedPlaylistRepository.deleteSharedPlaylist(playlistId)
                                                    navController.navigateUp()
                                                }
                                            },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            if (isAutoAssignedOrNullThumbnail(playlist?.thumbnailUrl) && tracks.isNotEmpty()) {
                                PlaylistCollageThumbnail(
                                    tracks = tracks.take(4).map { it.videoId },
                                    placeholderTitle = playlist?.title ?: "",
                                    onSuccessFirstCell = { res ->
                                        bitmap = res.image.toImageBitmap()
                                    },
                                    modifier = Modifier
                                        .height(250.dp)
                                        .wrapContentWidth()
                                        .align(Alignment.CenterHorizontally)
                                        .clip(RoundedCornerShape(8.dp)),
                                )
                            } else {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalPlatformContext.current)
                                        .data(playlist?.thumbnailUrl)
                                        .diskCachePolicy(CachePolicy.ENABLED)
                                        .build(),
                                    placeholder = painterPlaylistThumbnail(playlist?.title ?: "", style = typo().labelMedium, 250.dp to 250.dp),
                                    error = painterPlaylistThumbnail(playlist?.title ?: "", style = typo().labelMedium, 250.dp to 250.dp),
                                    contentDescription = null,
                                    contentScale = ContentScale.FillHeight,
                                    onSuccess = {
                                        bitmap = it.result.image.toImageBitmap()
                                    },
                                    modifier = Modifier
                                        .height(250.dp)
                                        .wrapContentWidth()
                                        .align(Alignment.CenterHorizontally)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }
                            Spacer(modifier = Modifier.size(16.dp))
                            Text(
                                text = playlist?.title ?: "Shared Playlist",
                                style = typo().titleLarge,
                                color = Color.White,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                            Text(
                                text = "Playlist dari ${playlist?.creatorName ?: "Anonim"}",
                                style = typo().titleSmall,
                                color = Color(0xC4FFFFFF),
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }

                        // Middle Action Row (Shuffle, Play, Save to Library)
                        var isSaving by remember { mutableStateOf(false) }
                        var isSaved by remember { mutableStateOf(false) }
                        LaunchedEffect(playlistId) {
                            val existing = localPlaylistRepository.getLocalPlaylistBySourceSharedId(playlistId)
                            if (existing != null) isSaved = true
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .clickable {
                                        if (tracks.isNotEmpty()) {
                                            val shuffled = tracks.shuffled().map { it.toTrack() }
                                            sharedViewModel.setQueueData(
                                                QueueData.Data(
                                                    listTracks = shuffled.toCollection(ArrayList()),
                                                    firstPlayedTrack = shuffled.first(),
                                                    playlistId = "SHARED_$playlistId",
                                                    playlistName = playlist?.title ?: "Shared Playlist",
                                                    playlistType = PlaylistType.PLAYLIST,
                                                    continuation = null
                                                )
                                            )
                                            sharedViewModel.loadMediaItemFromTrack(shuffled.first(), type = Config.SONG_CLICK)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = "Shuffle",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .height(48.dp)
                                    .widthIn(min = 120.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .clickable {
                                        if (tracks.isNotEmpty()) {
                                            val domainTracks = tracks.map { it.toTrack() }
                                            sharedViewModel.setQueueData(
                                                QueueData.Data(
                                                    listTracks = domainTracks.toCollection(ArrayList()),
                                                    firstPlayedTrack = domainTracks.first(),
                                                    playlistId = "SHARED_$playlistId",
                                                    playlistName = playlist?.title ?: "Shared Playlist",
                                                    playlistType = PlaylistType.PLAYLIST,
                                                    continuation = null
                                                )
                                            )
                                            sharedViewModel.loadMediaItemFromTrack(domainTracks.first(), type = Config.SONG_CLICK)
                                        }
                                    }
                                    .padding(horizontal = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Play",
                                        color = Color.Black,
                                        style = typo().labelLarge
                                    )
                                }
                            }

                            if (playlist?.userId != currentUserId) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(if (isSaved) Color(0x33FF4D6D) else Color.White.copy(alpha = 0.12f))
                                        .clickable(enabled = !isSaving && tracks.isNotEmpty()) {
                                            if (!isSaved) {
                                                // LIKE ❤️: simpan ke Pustaka + catat save (naikkan hitungan love).
                                                isSaving = true
                                                scope.launch {
                                                    try {
                                                        val newLocalId = localPlaylistRepository.saveSharedPlaylistToLibrary(
                                                            sharedPlaylistId = playlistId,
                                                            title = playlist?.title ?: "Shared Playlist",
                                                            thumbnail = playlist?.thumbnailUrl,
                                                            tracks = tracks.map { it.toTrack() },
                                                            creatorName = playlist?.creatorName
                                                        )
                                                        currentUserId?.let { uid ->
                                                            sharedPlaylistRepository.recordPlaylistSave(playlistId, uid)
                                                        }
                                                        isSaved = true
                                                        // Optimis: naikkan angka love di layar.
                                                        playlist = playlist?.copy(addCount = (playlist?.addCount ?: 0) + 1)
                                                        sharedViewModel.makeToast(
                                                            if (newLocalId == -1L) "Playlist sudah ada di Pustaka"
                                                            else "Disukai & ditambahkan ke Pustaka ❤️"
                                                        )
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                        sharedViewModel.makeToast("Gagal menyukai playlist")
                                                    } finally {
                                                        isSaving = false
                                                    }
                                                }
                                            } else {
                                                // UNLIKE: hapus save + hapus salinan lokal (turunkan hitungan).
                                                isSaving = true
                                                scope.launch {
                                                    try {
                                                        currentUserId?.let { uid ->
                                                            sharedPlaylistRepository.removePlaylistSave(playlistId, uid)
                                                        }
                                                        val local = localPlaylistRepository.getLocalPlaylistBySourceSharedId(playlistId)
                                                        if (local != null) {
                                                            localPlaylistRepository.deleteLocalPlaylist(local.id, "Dihapus").collect {}
                                                        }
                                                        isSaved = false
                                                        // Optimis: turunkan angka love di layar (jangan sampai minus).
                                                        playlist = playlist?.copy(addCount = maxOf((playlist?.addCount ?: 1) - 1, 0))
                                                        sharedViewModel.makeToast("Batal suka, dihapus dari Pustaka")
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                        sharedViewModel.makeToast("Gagal membatalkan")
                                                    } finally {
                                                        isSaving = false
                                                    }
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isSaved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = if (isSaved) "Batalkan suka" else "Sukai playlist",
                                        tint = if (isSaved) Color(0xFFFF4D6D) else Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${tracks.size} trek",
                                style = typo().bodyMedium,
                                color = Color(0xC4FFFFFF)
                            )
                            // Jumlah orang yang menyukai/menyimpan playlist (add_count, dijaga
                            // trigger DB saat baris masuk ke shared_playlist_saves).
                            val saveCount = playlist?.addCount ?: 0
                            Text(
                                text = "  •  ",
                                style = typo().bodyMedium,
                                color = Color(0x66FFFFFF)
                            )
                            Icon(
                                imageVector = Icons.Rounded.Favorite,
                                contentDescription = "Disukai",
                                tint = Color(0xFFFF4D6D),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "$saveCount",
                                style = typo().bodyMedium,
                                color = Color(0xC4FFFFFF)
                            )
                        }
                    }
                }
            }
        }

        itemsIndexed(tracks) { idx, track ->
            val domainTrack = track.toTrack()
            SongFullWidthItems(
                track = domainTrack,
                isPlaying = false,
                onClickListener = {
                    val domainTracks = tracks.map { it.toTrack() }
                    sharedViewModel.setQueueData(
                        QueueData.Data(
                            listTracks = domainTracks.toCollection(ArrayList()),
                            firstPlayedTrack = domainTrack,
                            playlistId = "SHARED_$playlistId",
                            playlistName = playlist?.title ?: "Shared Playlist",
                            playlistType = PlaylistType.PLAYLIST,
                            continuation = null
                        )
                    )
                    sharedViewModel.loadMediaItemFromTrack(domainTrack, type = Config.SONG_CLICK)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
