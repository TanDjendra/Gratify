package com.tan.gratify.ui.screen.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.tan.common.Config
import com.tan.domain.data.model.browse.album.Track
import com.tan.domain.data.model.searchResult.songs.Artist
import com.tan.domain.data.model.searchResult.songs.Thumbnail
import com.tan.domain.mediaservice.handler.PlaylistType
import com.tan.domain.mediaservice.handler.QueueData
import com.tan.domain.repository.*
import com.tan.gratify.ui.component.*
import com.tan.gratify.ui.navigation.destination.list.YouTubePlaylistDestination
import com.tan.gratify.ui.navigation.destination.login.LoginDestination
import com.tan.gratify.ui.theme.*
import com.tan.gratify.viewModel.SharedViewModel
import com.tan.gratify.viewModel.UIEvent
import com.tan.gratify.viewModel.YouTubeCollectionsState
import org.koin.compose.koinInject

private fun YouTubeLibraryItem.toTrack() = Track(album = null, artists = listOf(Artist(name = author, id = null)),
    duration = durationSeconds?.let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" },
    durationSeconds = durationSeconds, isAvailable = true, isExplicit = false, likeStatus = null,
    thumbnails = thumbnail?.let { listOf(Thumbnail(url = it, width = 320, height = 180)) },
    title = title, videoId = id, videoType = "MUSIC_VIDEO_TYPE_OMV", category = null,
    feedbackTokens = null, resultType = "song")

@Composable
fun YouTubePlaylistScreen(data: YouTubePlaylistDestination, navController: NavController,
    repository: YouTubeLibraryRepository = koinInject(), sharedViewModel: SharedViewModel = koinInject()) {
    val scope = rememberCoroutineScope()
    val source = remember(data) { YouTubeCollectionsState(repository, scope, data.playlistId, data.ownerId) }
    val page by source.state.collectAsStateWithLifecycle()
    val connection by repository.connection.collectAsStateWithLifecycle()
    val controller by sharedViewModel.controllerState.collectAsStateWithLifecycle()
    val currentQueue by sharedViewModel.getQueueDataState().collectAsStateWithLifecycle()
    val queueId = "YOUTUBE_${data.ownerId}_${data.playlistId}"
    val tracks = remember(page.items) { page.items.map { it.toTrack() } }
    val authorized = connection.ownerId == data.ownerId && connection.status == YouTubeConnectionStatus.CONNECTED
    val play: (Int, Boolean) -> Unit = { index, shuffle ->
        if (authorized && tracks.isNotEmpty()) {
            val queue = if (shuffle) tracks.shuffled() else tracks
            val first = queue[if (shuffle) 0 else index]
            sharedViewModel.setQueueData(QueueData.Data(listTracks = queue.toCollection(ArrayList()),
                firstPlayedTrack = first, playlistId = queueId,
                playlistName = data.title, playlistType = PlaylistType.PLAYLIST, continuation = null))
            sharedViewModel.loadMediaItemFromTrack(first, type = Config.PLAYLIST_CLICK, index = queue.indexOf(first))
        }
    }
    LazyColumn(Modifier.fillMaxSize().background(GratifyColors.Background),
        contentPadding = PaddingValues(bottom = 140.dp)) {
        item {
            CollectionDetailHeader(title = if (authorized) data.title else "Koleksi YouTube", kind = "PLAYLIST YOUTUBE", subtitle = "Akses baca saja",
                metadata = "${tracks.size} video dimuat", accent = GratifyColors.Accent,
                onBack = { navController.navigateUp() }, onPlay = {
                    if (authorized && currentQueue?.data?.playlistId == queueId) sharedViewModel.onUIEvent(UIEvent.PlayPause)
                    else play(0, false)
                }, onShuffle = { play(0, true) },
                isPlaying = authorized && controller.isPlaying && currentQueue?.data?.playlistId == queueId,
                canPlay = authorized && tracks.isNotEmpty(),
                description = {
                    if (authorized && data.playlistId == "liked")
                        Text("Hingga 1.000 video disukai terbaru tersedia dari YouTube.", style = typo().bodySmall, color = GratifyColors.TextSecondary)
                },
                artwork = { modifier -> AsyncImage(if (authorized) data.thumbnail ?: page.items.firstOrNull()?.thumbnail else null,
                    contentDescription = if (authorized) data.title else null, modifier = modifier.background(GratifyColors.SurfaceHighest), contentScale = ContentScale.Crop) })
        }
        if (!authorized) item {
            Column(Modifier.padding(16.dp)) {
                Text("Hubungkan akun Google yang sesuai untuk membuka koleksi ini.", color = GratifyColors.TextSecondary)
                TextButton(onClick = { navController.navigate(LoginDestination) }) { Text("Koneksi YouTube") }
            }
        } else {
            itemsIndexed(page.items, key = { _, item -> item.id }) { index, item ->
                Row(Modifier.fillMaxWidth().clickable { play(index, false) }.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AsyncImage(item.thumbnail, contentDescription = null, modifier = Modifier.size(52.dp), contentScale = ContentScale.Crop)
                    Column(Modifier.weight(1f)) {
                        Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = typo().bodyLarge, color = GratifyColors.TextPrimary)
                        Text(item.author, maxLines = 1, overflow = TextOverflow.Ellipsis, style = typo().bodySmall, color = GratifyColors.TextSecondary)
                    }
                    tracks[index].duration?.let { Text(it, color = GratifyColors.TextSecondary, style = typo().bodySmall) }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    if (page.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    page.error?.let { Text(it, color = GratifyColors.TextSecondary) }
                    if (page.error != null || page.nextPageToken != null)
                        TextButton(onClick = source::loadMore, enabled = !page.loading) { Text(if (page.error != null) "Coba kembali" else "Muat video lainnya") }
                    if (page.initialized && page.items.isEmpty()) Text("Belum ada video yang tersedia dalam koleksi ini.", color = GratifyColors.TextSecondary)
                }
            }
        }
    }
}
