package com.tan.gratify.ui.screen.friends

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tan.domain.data.model.metadata.Lyrics
import com.tan.domain.data.model.social.StoryTrack
import com.tan.gratify.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

internal data class StoryLyricLine(val startMs: Long, val endMs: Long, val text: String)
private val wordStamp = Regex("<\\d{1,6}:\\d{2}(?:\\.\\d{1,3})?>")

internal fun storyLyricExcerpt(lyrics: Lyrics?, startMs: Long, clipSeconds: Int): List<StoryLyricLine> {
    if (lyrics == null || lyrics.error) return emptyList()
    val lines = lyrics.lines.orEmpty()
    if (lyrics.syncType !in listOf("LINE_SYNCED", "RICH_SYNCED")) return lines.mapNotNull {
        val text = it.words.replace(wordStamp, "").replace(Regex("\\s+"), " ").trim()
        text.takeIf(String::isNotBlank)?.let { text -> StoryLyricLine(0, Long.MAX_VALUE, text) }
    }.take(4)
    val sorted = lines.mapNotNull { line -> line.startTimeMs.toLongOrNull()?.let { it to line } }.sortedBy { it.first }
    val end = startMs + clipSeconds * 1000L
    return sorted.mapIndexedNotNull { index, (time, line) ->
        val next = sorted.getOrNull(index + 1)?.first ?: Long.MAX_VALUE
        val until = line.endTimeMs.toLongOrNull()?.takeIf { it > time }?.coerceAtMost(next) ?: next
        val text = line.words.replace(wordStamp, "").replace(Regex("\\s+"), " ").trim()
        if (time < end && until > startMs && text.isNotBlank()) StoryLyricLine(time, until, text) else null
    }
}

internal fun storyElapsedMs(positionMs: Long, clipSeconds: Int, completed: Boolean) =
    if (completed) clipSeconds * 1000L else positionMs.coerceIn(0, clipSeconds * 1000L)

internal data class StoryLyricsPreviewState(val lyrics: Lyrics? = null, val loading: Boolean = true)

@Composable
internal fun rememberStoryLyrics(track: StoryTrack, load: (StoryTrack) -> Flow<Lyrics>, retry: Int = 0): StoryLyricsPreviewState {
    var state by remember(track.videoId, track.durationMs, retry) { mutableStateOf(StoryLyricsPreviewState()) }
    val currentLoad by rememberUpdatedState(load)
    LaunchedEffect(track.videoId, track.durationMs, retry) {
        try { currentLoad(track).collect { state = state.copy(lyrics = it) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* An unavailable lyric must not prevent sharing or playing music. */ }
        finally { state = state.copy(loading = false) }
    }
    return state
}

@Composable
internal fun StoryLyricsPreview(lyrics: Lyrics?, loading: Boolean, startMs: Long, clipSeconds: Int,
    positionMs: Long? = null, onRetry: () -> Unit = {}) {
    val excerpt = remember(lyrics, startMs, clipSeconds) { storyLyricExcerpt(lyrics, startMs, clipSeconds) }
    val synced = lyrics?.syncType in listOf("LINE_SYNCED", "RICH_SYNCED")
    val absolute = startMs + (positionMs ?: 0)
    val active = if (positionMs != null && synced) excerpt.indexOfLast { absolute >= it.startMs && absolute < it.endMs } else -1
    val first = if (active >= 0) (active - 1).coerceAtLeast(0) else 0
    Column(Modifier.fillMaxWidth().testTag("story-lyrics-preview"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Lirik potongan", style = typo().labelLarge, color = GratifyColors.TextSecondary)
        when {
            excerpt.isNotEmpty() -> {
                excerpt.drop(first).take(if (positionMs == null) 4 else 3).forEachIndexed { index, line ->
                    val highlighted = first + index == active
                    Text(line.text, style = typo().titleMedium.copy(fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium),
                        color = if (highlighted) GratifyColors.Accent else GratifyColors.TextPrimary.copy(alpha = if (active >= 0) .6f else 1f))
                }
                if (!synced) Text("Lirik ini belum memiliki penanda waktu.", style = typo().bodySmall, color = GratifyColors.TextSecondary)
            }
            loading -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Memuat lirik potongan…", style = typo().bodySmall, color = GratifyColors.TextSecondary)
            }
            else -> {
                Text(if (lyrics != null && synced) "Tidak ada lirik pada bagian ini." else "Lirik belum tersedia. Musik tetap bisa dibagikan.",
                    style = typo().bodySmall, color = GratifyColors.TextSecondary)
                if (lyrics == null) TextButton(onClick = onRetry) { Text("Coba muat lirik") }
            }
        }
    }
}
