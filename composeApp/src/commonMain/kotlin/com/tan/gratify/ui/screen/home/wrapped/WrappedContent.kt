package com.tan.gratify.ui.screen.home.wrapped

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tan.gratify.ui.component.LayerBackHandler
import com.tan.gratify.ui.theme.GratifyColors
import com.tan.gratify.viewModel.*
import gratify.composeapp.generated.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private val Lime = Color(0xFFD4F879)
private val Ink = Color(0xFF192315)
private val Lavender = Color(0xFFCABAFE)
private val Panel = Color(0xFF20231F)

/** Manual stories keep reading, navigation and sharing under the listener's control. */
@Composable
fun WrappedContent(
    state: WrappedState, innerPadding: PaddingValues = PaddingValues(), onBack: () -> Unit = {},
    onSelectYear: (Int) -> Unit = {}, onRefresh: () -> Unit = {}, onSettings: () -> Unit = {},
    onSavePlaylist: () -> Unit = {}, onOpenPlaylist: (Long) -> Unit = {},
    onShare: suspend (ImageBitmap) -> Boolean = { false },
) {
    var story by remember(state.year) { mutableStateOf(false) }
    var page by remember(state.year) { mutableStateOf(0) }
    var methodology by remember { mutableStateOf(false) }
    var sharing by remember(state.year) { mutableStateOf(false) }
    var shareError by remember(state.year) { mutableStateOf(false) }
    var shareReady by remember(state.year) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    val refreshDescription = stringResource(Res.string.wrapped_refresh)
    // A reload/account change clears the displayed story before new data arrives.
    LaunchedEffect(state.loading, state.error) { if (state.loading || state.error) { story = false; page = 0; shareError = false; shareReady = false } }
    LayerBackHandler(story) { story = false }
    Column(Modifier.fillMaxSize().background(GratifyColors.Background).padding(innerPadding), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (story) story = false else onBack() }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (story) Res.string.wrapped_close else Res.string.wrapped_back))
            }
            Text("WRAPPED ${state.year}", color = Lime, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 2)
            if (!story) TextButton(onClick = onRefresh, enabled = !state.loading && !state.savingPlaylist,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = refreshDescription }) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(24.dp))
            }
        }
        if (story) {
            Row(Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { index -> Box(Modifier.weight(1f).height(4.dp).background(if (index <= page) Lime else Color.White.copy(alpha = .18f), RoundedCornerShape(2.dp))) }
            }
            Text(stringResource(Res.string.wrapped_page, (page + 1).toString(), "7"), color = GratifyColors.TextSecondary,
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp))
        }
        Box(Modifier.weight(1f).widthIn(max = 760.dp).fillMaxWidth()) {
            if (!story) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    WrappedHero(state.year)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.years.forEach { year -> FilterChip(selected = state.year == year, onClick = { onSelectYear(year) },
                            enabled = !state.savingPlaylist, label = { Text(year.toString()) }, modifier = Modifier.heightIn(min = 48.dp)) }
                    }
                    if (state.provisional) Text(stringResource(Res.string.wrapped_provisional), color = Lime, style = MaterialTheme.typography.labelLarge)
                    if (!state.trackingEnabled && !state.loading) WrappedPanel {
                        Text(stringResource(Res.string.wrapped_recording_off), color = Color.White)
                        TextButton(onClick = onSettings, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(Res.string.wrapped_settings)) }
                    }
                    when {
                        state.loading -> { CircularProgressIndicator(color = Lime); Text(stringResource(Res.string.wrapped_loading), color = Color.White) }
                        state.error -> WrappedPanel {
                            Text(stringResource(Res.string.wrapped_error), color = Color.White)
                            Button(onClick = onRefresh, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(Res.string.wrapped_retry)) }
                        }
                        state.summary.totalSeconds == 0L -> WrappedPanel {
                            Text(stringResource(Res.string.wrapped_empty), color = Color.White, style = MaterialTheme.typography.headlineSmall)
                            Text(stringResource(Res.string.wrapped_empty_body, state.year.toString()), color = GratifyColors.TextSecondary)
                            Button(onClick = onSettings, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(Res.string.wrapped_settings)) }
                        }
                        else -> {
                            Button(onClick = { page = 0; story = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink)) { Text(stringResource(Res.string.wrapped_start)) }
                            WrappedPanel {
                                Text(listeningTime(state.summary.totalSeconds), color = Lime, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                                Text(stringResource(Res.string.wrapped_plays, state.summary.plays.toString()), color = Color.White)
                                Text(stringResource(Res.string.wrapped_tracks, state.summary.uniqueTracks.toString()), color = Color.White)
                                Text(stringResource(Res.string.wrapped_days, state.summary.activeDays.toString()), color = GratifyColors.TextSecondary)
                            }
                            if (state.metadataIncomplete) Text(stringResource(Res.string.wrapped_metadata), color = GratifyColors.TextSecondary)
                            PlaylistAction(state, onSavePlaylist, onOpenPlaylist)
                        }
                    }
                    Text(stringResource(Res.string.wrapped_scope), color = GratifyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { methodology = !methodology }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(Res.string.wrapped_method)) }
                    if (methodology) Text(stringResource(Res.string.wrapped_method_body), color = GratifyColors.TextSecondary)
                }
            } else {
                Crossfade(page, animationSpec = tween(180)) { currentPage ->
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        when (currentPage) {
                            0 -> { WrappedHero(state.year)
                                Text(stringResource(Res.string.wrapped_tracks, state.summary.uniqueTracks.toString()), color = Lime, style = MaterialTheme.typography.headlineMedium)
                                Text(stringResource(Res.string.wrapped_scope), color = GratifyColors.TextSecondary)
                                if (state.provisional) Text(stringResource(Res.string.wrapped_provisional), color = Lime) }
                            1 -> {
                                StoryHeading(stringResource(Res.string.wrapped_time))
                                VinylArt(Modifier.fillMaxWidth().height(160.dp), Lime)
                                Text(listeningTime(state.summary.totalSeconds), color = Lime, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
                                Text(stringResource(Res.string.wrapped_plays, state.summary.plays.toString()), color = Color.White, style = MaterialTheme.typography.headlineSmall)
                                Text(stringResource(Res.string.wrapped_days, state.summary.activeDays.toString()), color = GratifyColors.TextSecondary)
                            }
                            2 -> { StoryHeading(stringResource(Res.string.wrapped_songs_title)); RankList(state.tracks, false) }
                            3 -> { StoryHeading(stringResource(Res.string.wrapped_artists_title)); RankList(state.artists, true) }
                            4 -> {
                                StoryHeading(stringResource(Res.string.wrapped_habits))
                                val bands = listOf(Res.string.wrapped_night, Res.string.wrapped_morning, Res.string.wrapped_afternoon, Res.string.wrapped_evening)
                                Text(stringResource(bands[state.summary.clockBand.coerceIn(0, 3)]), color = Lavender, style = MaterialTheme.typography.headlineLarge)
                                Text(stringResource(Res.string.wrapped_streak, state.summary.longestStreak.toString()), color = Color.White, style = MaterialTheme.typography.headlineSmall)
                                Text(stringResource(Res.string.wrapped_days, state.summary.activeDays.toString()), color = GratifyColors.TextSecondary)
                                MonthlyListening(state.summary.monthlySeconds)
                            }
                            5 -> {
                                StoryHeading(stringResource(Res.string.wrapped_biggest))
                                VinylArt(Modifier.fillMaxWidth().height(180.dp), Lavender)
                                Text(displayDate(state.summary.biggestDay), color = Lavender, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                                Text(listeningTime(state.summary.biggestDaySeconds), color = Color.White, style = MaterialTheme.typography.headlineLarge)
                            }
                            6 -> {
                                StoryHeading(stringResource(Res.string.wrapped_final))
                                WrappedShareCard(state, Modifier.fillMaxWidth().drawWithContent {
                                    layer.record { this@drawWithContent.drawContent() }; drawLayer(layer)
                                })
                                if (shareError) Text(stringResource(Res.string.wrapped_share_error), color = MaterialTheme.colorScheme.error)
                                if (shareReady) Text(stringResource(Res.string.wrapped_share_ready), color = Lime)
                                Button(onClick = {
                                    if (!sharing) scope.launch {
                                        sharing = true; shareError = false; shareReady = false
                                        try { shareReady = onShare(layer.toImageBitmap()); shareError = !shareReady }
                                        catch (cancelled: CancellationException) { throw cancelled }
                                        catch (_: Exception) { shareError = true }
                                        finally { sharing = false }
                                    }
                                }, enabled = !sharing, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                                    Text(stringResource(if (sharing) Res.string.wrapped_sharing else Res.string.wrapped_share))
                                }
                                PlaylistAction(state, onSavePlaylist, onOpenPlaylist)
                            }
                        }
                    }
                }
            }
        }
        if (story) BoxWithConstraints(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                maxItemsInEachRow = if (maxWidth < 400.dp && LocalDensity.current.fontScale > 1.2f) 1 else 2,
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { page-- }, enabled = page > 0 && !sharing, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.wrapped_previous))
            }
            Button(onClick = { if (page < 6) page++ else story = false }, enabled = !sharing,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(if (page == 6) Res.string.wrapped_finish else Res.string.wrapped_next))
            }
        }
        }
    }
}

@Composable private fun WrappedHero(year: Int) {
    Column(Modifier.fillMaxWidth().background(Lime, RoundedCornerShape(28.dp)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("GRATIFY / WRAPPED", color = Ink, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(year.toString(), color = Ink, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            VinylArt(Modifier.size(80.dp), Ink)
        }
        Text(stringResource(Res.string.wrapped_title), color = Ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun VinylArt(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val center = Offset(size.width * .55f, size.height * .5f); val radius = minOf(size.width, size.height) * .45f
        repeat(7) { ring -> drawCircle(color.copy(alpha = 1f - ring * .09f), radius * (1f - ring * .12f), center, style = Stroke(3.dp.toPx())) }
        drawCircle(color, radius * .1f, center)
    }
}

@Composable private fun WrappedPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(20.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable private fun StoryHeading(title: String) {
    Text(title, color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
}

@Composable private fun RankList(ranks: List<WrappedRank>, artist: Boolean) {
    if (ranks.isEmpty()) Text(stringResource(if (artist) Res.string.wrapped_no_artists else Res.string.wrapped_no_ranks), color = GratifyColors.TextSecondary)
    ranks.forEachIndexed { index, rank ->
        Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("${index + 1}".padStart(2, '0'), color = if (artist) Lavender else Lime, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(rank.title.ifBlank { stringResource(if (artist) Res.string.wrapped_unknown_artist else Res.string.wrapped_unknown_song) },
                    color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (rank.subtitle.isNotBlank()) Text(rank.subtitle, color = GratifyColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (artist) listeningTime(rank.seconds) else stringResource(Res.string.wrapped_plays, rank.plays.toString()), color = if (artist) Lavender else Lime)
            }
        }
    }
}

@Composable private fun MonthlyListening(seconds: List<Long>) {
    val values = List(12) { seconds.getOrElse(it) { 0 }.coerceAtLeast(0) }
    val label = stringResource(Res.string.wrapped_months)
    val description = label + ": " + values.mapIndexed { i, value -> "${i + 1}: ${listeningTime(value)}" }.joinToString(", ")
    Text(label, color = GratifyColors.TextSecondary)
    Canvas(Modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = description }) {
        val max = values.maxOrNull()?.coerceAtLeast(1) ?: 1
        val slot = size.width / 12
        values.forEachIndexed { i, value ->
            val height = size.height * value / max
            drawRoundRect(Lavender.copy(alpha = if (value == max) 1f else .6f), Offset(i * slot + slot * .16f, size.height - maxOf(height, 2.dp.toPx())),
                androidx.compose.ui.geometry.Size(slot * .68f, maxOf(height, 2.dp.toPx())), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        }
    }
    Row(Modifier.fillMaxWidth()) { repeat(12) { Text((it + 1).toString(), Modifier.weight(1f), color = GratifyColors.TextSecondary, style = MaterialTheme.typography.labelSmall) } }
}

@Composable private fun PlaylistAction(state: WrappedState, onSave: () -> Unit, onOpen: (Long) -> Unit) {
    if (state.playlistAvailable) WrappedPanel {
        Text(stringResource(Res.string.wrapped_playlist_hint), color = GratifyColors.TextSecondary)
        if (state.playlistError) Text(stringResource(Res.string.wrapped_playlist_error), color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = { state.playlistId?.let(onOpen) ?: onSave() }, enabled = !state.savingPlaylist,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(when { state.savingPlaylist -> Res.string.wrapped_playlist_busy
                state.playlistId != null -> Res.string.wrapped_playlist_open; else -> Res.string.wrapped_playlist }))
        }
    }
}

/** Only this bounded recap card is exported; navigation and account controls stay outside the image. */
@Composable
fun WrappedShareCard(state: WrappedState, modifier: Modifier = Modifier) {
    Column(modifier.background(Lime, RoundedCornerShape(24.dp)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("GRATIFY / WRAPPED ${state.year}", color = Ink, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(listeningTime(state.summary.totalSeconds), color = Ink, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
        Text(stringResource(Res.string.wrapped_tracks, state.summary.uniqueTracks.toString()), color = Ink)
        if (state.tracks.isNotEmpty()) {
            Text(stringResource(Res.string.wrapped_top_song), color = Ink.copy(alpha = .7f), style = MaterialTheme.typography.labelLarge)
            Text(state.tracks.first().title.ifBlank { stringResource(Res.string.wrapped_unknown_song) }, color = Ink, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (state.artists.isNotEmpty()) {
            Text(stringResource(Res.string.wrapped_top_artist), color = Ink.copy(alpha = .7f), style = MaterialTheme.typography.labelLarge)
            Text(state.artists.first().title, color = Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        VinylArt(Modifier.fillMaxWidth().height(64.dp), Ink)
        if (state.provisional) Text(stringResource(Res.string.wrapped_provisional), color = Ink, style = MaterialTheme.typography.labelSmall)
        Text(stringResource(Res.string.wrapped_scope), color = Ink.copy(alpha = .7f), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun listeningTime(seconds: Long): String = stringResource(
    if (seconds < 60) Res.string.wrapped_seconds else Res.string.wrapped_minutes,
    (if (seconds < 60) seconds else seconds / 60).toString())

private fun displayDate(date: String?): String = date?.split('-')?.takeIf { it.size == 3 }?.let { "${it[2]}/${it[1]}/${it[0]}" }.orEmpty()
