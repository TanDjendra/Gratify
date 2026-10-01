package com.tan.gratify.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.tan.domain.utils.connectArtists
import com.tan.gratify.Platform
import com.tan.gratify.expect.toggleMiniPlayer
import com.tan.gratify.getPlatform
import com.tan.gratify.ui.component.PlayerControlLayout
import com.tan.gratify.viewModel.SharedViewModel
import com.tan.gratify.viewModel.UIEvent
import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Playback state and progress are collected separately to keep the song row stable. */
@Composable
fun MiniPlayer(
    modifier: Modifier,
    sharedViewModel: SharedViewModel = koinInject(),
    onClose: () -> Unit,
    onClick: () -> Unit,
) {
    val playing by sharedViewModel.nowPlayingState.collectAsStateWithLifecycle()
    val controls by sharedViewModel.controllerState.collectAsStateWithLifecycle()
    val song = playing?.songEntity
    MiniPlayerSurface(
        modifier = modifier,
        title = song?.title.orEmpty(),
        artist = song?.artistName?.connectArtists().orEmpty(),
        artworkUrl = song?.thumbnails,
        isPlaying = controls.isPlaying,
        isLiked = controls.isLiked,
        onOpen = onClick,
        onPlayPause = { sharedViewModel.onUIEvent(UIEvent.PlayPause) },
        onToggleLike = { sharedViewModel.onUIEvent(UIEvent.ToggleLike) },
        wideControls = {
            Box(Modifier.width(260.dp)) { PlayerControlLayout(
                isSmallSize = true,
                controllerState = controls,
                onUIEvent = sharedViewModel::onUIEvent,
            ) }
        },
        wideActions = {
            if (getPlatform() == Platform.Desktop) {
                IconButton(onClick = { toggleMiniPlayer() }) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(Res.string.ui_open_player))
                }
            }
            IconButton(onClick = {
                sharedViewModel.onUIEvent(UIEvent.UpdateVolume(if (controls.volume > 0f) 0f else 1f))
            }) {
                Icon(
                    if (controls.volume > 0f) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    stringResource(Res.string.ui_volume),
                )
            }
            Slider(
                value = controls.volume.coerceIn(0f, 1f),
                onValueChange = { sharedViewModel.onUIEvent(UIEvent.UpdateVolume(it)) },
                modifier = Modifier.width(90.dp),
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, stringResource(Res.string.close_miniplayer))
            }
        },
        progress = { MiniPlayerProgress(sharedViewModel) },
    )
}

/** Shared compact player used on phones, tablets and desktop. */
@Composable
fun MiniPlayerSurface(
    modifier: Modifier = Modifier,
    title: String,
    artist: String,
    artworkUrl: String?,
    isPlaying: Boolean,
    isLiked: Boolean,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onToggleLike: () -> Unit,
    wideControls: @Composable () -> Unit = {},
    wideActions: @Composable RowScope.() -> Unit = {},
    progress: @Composable () -> Unit = {},
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        BoxWithConstraints {
            val wide = maxWidth >= 840.dp
            Box(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp).padding(bottom = if (wide) 18.dp else 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.weight(1f).clickable(onClick = onOpen),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AsyncImage(
                            model = artworkUrl,
                            contentDescription = null,
                            placeholder = painterResource(Res.drawable.holder),
                            error = painterResource(Res.drawable.holder),
                            fallback = painterResource(Res.drawable.holder),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(if (wide) 56.dp else 42.dp).clip(MaterialTheme.shapes.extraSmall),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (wide) wideControls()
                    IconButton(onClick = onToggleLike, modifier = Modifier.size(48.dp)) {
                        Icon(
                            if (isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            stringResource(Res.string.favorite),
                            tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(23.dp),
                        )
                    }
                    if (wide) {
                        wideActions()
                    } else {
                        IconButton(onClick = onPlayPause, modifier = Modifier.size(48.dp)) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                stringResource(if (isPlaying) Res.string.ui_pause else Res.string.ui_play),
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    }
                }
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) { progress() }
            }
        }
    }
}

@Composable
private fun MiniPlayerProgress(viewModel: SharedViewModel) {
    val timeline by viewModel.timeline.collectAsStateWithLifecycle()
    val progress = if (timeline.total > 0L) {
        (timeline.current.toFloat() / timeline.total).coerceIn(0f, 1f)
    } else 0f
    if (getPlatform() == Platform.Desktop) {
        Slider(value = progress, onValueChange = { viewModel.onUIEvent(UIEvent.UpdateProgress(it)) }, modifier = Modifier.fillMaxWidth().height(20.dp))
    } else LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().height(2.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        strokeCap = StrokeCap.Butt,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}
