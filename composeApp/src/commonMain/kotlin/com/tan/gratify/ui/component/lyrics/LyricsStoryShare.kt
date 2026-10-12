package com.tan.gratify.ui.component.lyrics

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.tan.domain.data.model.metadata.Line
import com.tan.domain.data.model.social.MusicStoryDraft
import com.tan.domain.data.model.social.StoryTrack
import com.tan.domain.mediaservice.handler.MediaPlayerHandler
import com.tan.gratify.ui.navigation.destination.friends.FriendsDestination
import com.tan.gratify.viewModel.SharedViewModel
import com.tan.gratify.ui.navigation.openMainTab

internal data class LyricsStorySource(val track: StoryTrack, val lines: List<Line>, val positionMs: Long,
    val synced: Boolean, val open: (MusicStoryDraft) -> Unit)
internal val LocalLyricsStorySource = staticCompositionLocalOf<LyricsStorySource?> { null }

/** No text is silently truncated. The existing story service supports 200-character captions. */
internal fun lyricsStoryDraft(track: StoryTrack, lines: List<Line>, range: IntRange,
    positionMs: Long, synced: Boolean): MusicStoryDraft? {
    if (track.videoId.isBlank() || track.title.isBlank() || range.isEmpty() || range.count() > 3 || range.first < 0 || range.last >= lines.size) return null
    val caption = range.joinToString("\n") { stripRichLyricsTimestamps(lines[it].words) }
    if (caption.isBlank() || caption.length > 200 || range.any { lines[it].words.isBlank() }) return null
    val start = if (synced) lines[range.first].startTimeMs.toLongOrNull() ?: positionMs else positionMs
    val end = lines.getOrNull(range.last + 1)?.startTimeMs?.toLongOrNull()?.takeIf { it > start }
        ?: lines[range.last].endTimeMs.toLongOrNull()?.takeIf { it > start }
    val wanted = when {
        synced && end != null && end - start > 15000 -> 30
        synced && end != null && end - start <= 8000 -> 8
        else -> 15
    }
    val seconds = if (track.durationMs <= 0) wanted else listOf(30, 15, 8).firstOrNull { it <= wanted && it * 1000L <= track.durationMs } ?: return null
    val maxStart = if (track.durationMs > 0) (track.durationMs - seconds * 1000L).coerceAtLeast(0) else Long.MAX_VALUE
    return MusicStoryDraft(track, start.coerceIn(0, maxStart), seconds, caption)
}

@Composable
internal fun LyricsStoryShareHost(shared: SharedViewModel, player: MediaPlayerHandler,
    navController: NavController, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLyricsStorySource provides null, content = content)
}
