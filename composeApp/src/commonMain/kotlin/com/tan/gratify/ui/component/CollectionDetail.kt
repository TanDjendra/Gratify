package com.tan.gratify.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tan.domain.data.entities.DownloadState
import com.tan.gratify.ui.theme.GratifyColors
import com.tan.gratify.ui.theme.typo
import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Shared layout for albums and every playlist source. The artwork is never cropped by the header. */
@Composable
fun CollectionDetailHeader(
    title: String,
    kind: String,
    subtitle: String,
    metadata: String,
    accent: Color,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    isPlaying: Boolean,
    canPlay: Boolean,
    artwork: @Composable (Modifier) -> Unit,
    onSubtitleClick: (() -> Unit)? = null,
    description: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().background(
            Brush.verticalGradient(listOf(lerp(accent, GratifyColors.Background, 0.45f), GratifyColors.Background)),
        ),
    ) {
        val wide = maxWidth >= 600.dp
        val coverSize = if (wide) 224.dp else (maxWidth * 0.62f).coerceIn(160.dp, 260.dp)
        val info: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (wide) Text(kind, style = typo().labelMedium, color = GratifyColors.TextPrimary)
                Text(title, style = if (wide) typo().displayMedium else typo().headlineLarge,
                    color = GratifyColors.TextPrimary, maxLines = 4, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = typo().labelLarge, color = GratifyColors.TextPrimary,
                        modifier = if (onSubtitleClick != null) Modifier.clickable(onClick = onSubtitleClick).padding(vertical = 4.dp) else Modifier)
                }
                Text(metadata, style = typo().bodySmall, color = GratifyColors.TextSecondary)
                description?.invoke()
            }
        }
        Column {
            Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                CollectionDetailAction(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.detail_back), onClick = onBack)
            }
            if (wide) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Bottom) {
                    artwork(Modifier.size(coverSize).shadow(16.dp, RoundedCornerShape(4.dp)).clip(RoundedCornerShape(4.dp)))
                    Box(Modifier.weight(1f)) { info() }
                }
            } else {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp), contentAlignment = Alignment.Center) {
                    artwork(Modifier.size(coverSize).shadow(16.dp, RoundedCornerShape(4.dp)).clip(RoundedCornerShape(4.dp)))
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { info() }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = if (wide) 16.dp else 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                actions()
                Spacer(Modifier.weight(1f))
                CollectionDetailAction(Icons.Rounded.Shuffle, stringResource(Res.string.shuffle), enabled = canPlay, onClick = onShuffle)
                FilledIconButton(onClick = onPlay, enabled = canPlay,
                    modifier = Modifier.padding(start = 4.dp, end = 8.dp).size(56.dp), shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = GratifyColors.Accent,
                        contentColor = GratifyColors.OnAccent, disabledContainerColor = GratifyColors.SurfaceHighest,
                        disabledContentColor = GratifyColors.TextSecondary)) {
                    Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        stringResource(if (isPlaying) Res.string.detail_pause else Res.string.detail_play), Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
fun CollectionDetailAction(icon: ImageVector, label: String, selected: Boolean = false,
    enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(26.dp),
            tint = if (!enabled) GratifyColors.Outline else if (selected) GratifyColors.Accent else GratifyColors.TextSecondary)
    }
}

@Composable
fun CollectionSaveAction(saved: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    CollectionDetailAction(if (saved) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircleOutline,
        stringResource(if (saved) Res.string.detail_remove_saved else Res.string.detail_save), saved, enabled, onClick)
}

@Composable
fun CollectionDownloadAction(state: Int, enabled: Boolean = true, onClick: () -> Unit) {
    val downloadingLabel = stringResource(Res.string.downloading)
    if (state == DownloadState.STATE_DOWNLOADING) {
        Box(Modifier.size(48.dp).semantics { contentDescription = downloadingLabel }, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), color = GratifyColors.Accent, strokeWidth = 2.dp)
        }
    } else {
        CollectionDetailAction(if (state == DownloadState.STATE_DOWNLOADED) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
            stringResource(if (state == DownloadState.STATE_DOWNLOADED) Res.string.downloaded else Res.string.download),
            selected = state == DownloadState.STATE_DOWNLOADED, enabled = enabled, onClick = onClick)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = typo().titleMedium) },
        navigationIcon = { CollectionDetailAction(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.detail_back), onClick = onBack) },
        actions = actions, colors = TopAppBarDefaults.topAppBarColors(containerColor = GratifyColors.Background))
}

@Composable
fun CollectionDetailLoading(onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().background(GratifyColors.Background)) {
        item {
            Column {
                Row(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 8.dp)) {
                    CollectionDetailAction(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.detail_back), onClick = onBack)
                }
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(224.dp).clip(RoundedCornerShape(4.dp)).background(GratifyColors.SurfaceRaised))
                }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.fillMaxWidth(0.7f).height(28.dp).background(GratifyColors.SurfaceRaised))
                    Text(stringResource(Res.string.detail_loading), color = GratifyColors.TextSecondary, style = typo().bodySmall)
                    repeat(5) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp).background(GratifyColors.SurfaceRaised))
                            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.fillMaxWidth(0.8f).height(16.dp).background(GratifyColors.SurfaceRaised))
                                Box(Modifier.fillMaxWidth(0.5f).height(12.dp).background(GratifyColors.Surface))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CollectionDetailError(onBack: () -> Unit, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().background(GratifyColors.Background)) {
        Row(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 8.dp)) {
            CollectionDetailAction(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.detail_back), onClick = onBack)
        }
        Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Icon(Icons.Rounded.LibraryMusic, null, Modifier.size(48.dp), tint = GratifyColors.TextSecondary)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(Res.string.detail_load_failed), style = typo().headlineSmall, color = GratifyColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(Res.string.detail_retry_hint), style = typo().bodyMedium, color = GratifyColors.TextSecondary)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
        }
    }
}

@Composable
fun CollectionDetailEmpty(isFiltered: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.MusicNote, null, Modifier.size(32.dp), tint = GratifyColors.TextSecondary)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(if (isFiltered) Res.string.detail_no_matches else Res.string.playlist_is_empty), style = typo().titleMedium, color = GratifyColors.TextPrimary)
        Text(stringResource(if (isFiltered) Res.string.detail_search_hint else Res.string.detail_empty_hint), style = typo().bodySmall, color = GratifyColors.TextSecondary)
    }
}
