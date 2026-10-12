// Ported from SimpMusic 62e0073ba818d899b22c75909deb9b034516d5db (GPL-3.0).
package com.tan.gratify.ui.screen.player.simp.content.applemusic

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.tan.domain.data.model.metadata.Line
import com.tan.gratify.extension.ParsedRichSyncLine
import com.tan.gratify.extension.parseRichSyncWords
import com.tan.gratify.extension.displayText
import com.tan.gratify.ui.screen.player.simp.component.rememberLyricLayoutDirection
import com.tan.gratify.ui.screen.player.simp.content.NowPlayingContentState
import com.tan.gratify.ui.screen.player.simp.content.stripRichSyncTimestamps
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * The line being sung, directly over the progress bar — BitChord's strip. Tapping it opens the
 * Lyrics tab.
 *
 * Displays the complete current line, including lyrics with provider word timestamps.
 *
 * Before the first line starts the slot holds three dots rather than nothing, so an intro does not
 * leave a gap between the title and the bar.
 *
 * Always one line tall, empty or not, so the controls below it never move when a line ends or a
 * track has no synced lyrics.
 */
@Composable
internal fun AppleMusicLyricStrip(
    state: NowPlayingContentState,
    typography: AppleMusicTypography,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lyrics = state.screenData.lyricsData?.lyrics
    val syncType = lyrics?.syncType
    val synced = lyrics?.lines != null && syncType != null && syncType != "UNSYNCED"
    // The index is -1 only until the first line starts: once a line has been reached it is held
    // until the next one, and the last is held to the end of the track.
    val line = if (synced) lyrics.lines?.getOrNull(state.currentLyricLineIndex) else null

    AnimatedContent(
        // Keyed on the line itself: the index alone would miss a lyrics swap that lands on the same
        // index, and the text alone would merge two identical lines sung back to back.
        targetState =
            when {
                line != null -> StripContent.Sung(line)
                synced -> StripContent.Waiting
                else -> null
            },
        transitionSpec = {
            // The new line rises into place as the old one rises away, a third of a line each way —
            // the direction lyrics travel in the Lyrics tab, so the two read as the same motion.
            val enter =
                fadeIn(tween(if (state.lyricsAnimationsEnabled) LINE_CHANGE_MS else 0, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(if (state.lyricsAnimationsEnabled) LINE_CHANGE_MS else 0, easing = FastOutSlowInEasing)) { (it * 0.35f).toInt() }
            val exit =
                fadeOut(tween(if (state.lyricsAnimationsEnabled) LINE_CHANGE_MS else 0, easing = FastOutSlowInEasing)) +
                    slideOutVertically(tween(if (state.lyricsAnimationsEnabled) LINE_CHANGE_MS else 0, easing = FastOutSlowInEasing)) { -(it * 0.35f).toInt() }
            (enter togetherWith exit).using(SizeTransform(clip = false))
        },
        label = "appleMusicLyricStrip",
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(
                    enabled = synced,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
    ) { shown ->
        val lineText = (shown as? StripContent.Sung)?.line?.displayText().orEmpty()
        val lineDirection = rememberLyricLayoutDirection(lineText)
        CompositionLocalProvider(LocalLayoutDirection provides lineDirection) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
                // Every branch starts from a one-line Text, which is what keeps the slot the same height.
                when (shown) {
                    is StripContent.Sung -> {
                        StripText(text = shown.line.displayText(), typography = typography)
                    }

                    StripContent.Waiting -> {
                        StripText(text = "", typography = typography)
                        WaitingDots()
                    }

                    null -> StripText(text = "", typography = typography)
                }
            }
        }
    }
}

// What the strip is showing: a line being sung, or the wait before the first one.
private sealed interface StripContent {
    data class Sung(
        val line: Line,
    ) : StripContent

    data object Waiting : StripContent
}

@Composable
private fun StripText(
    text: String,
    typography: AppleMusicTypography,
) {
    Text(
        text = text,
        style = typography.lyricStrip.copy(textDirection = TextDirection.ContentOrLtr),
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, animationMode = MarqueeAnimationMode.Immediately),
    )
}

// Three dots in a row, centred on the line's height by the Box around them.
@Composable
private fun WaitingDots() {
    Row(horizontalArrangement = Arrangement.spacedBy(WAITING_DOT_GAP)) {
        repeat(3) {
            Box(modifier = Modifier.size(WAITING_DOT_SIZE).clip(CircleShape).background(UNSUNG))
        }
    }
}

@Composable
private fun SweptLine(
    parsed: ParsedRichSyncLine,
    playhead: State<Long>,
    typography: AppleMusicTypography,
) {
    // The words joined the way the line is shown, with each word's character range kept, so the
    // playhead can be turned into an x position on the laid-out text.
    val text = remember(parsed) { parsed.displayText() }
    val ranges =
        remember(parsed) {
            var start = 0
            parsed.words.mapIndexed { index, word ->
                if (index > 0) start += word.separatorBefore.length
                val range = start until start + word.text.length
                start += word.text.length
                range
            }
        }
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    // The marquee sits on the Box, not on each Text, so the dim and bright copies scroll as one and
    // the sweep stays over the word it belongs to.
    Box(modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, animationMode = MarqueeAnimationMode.Immediately)) {
        Text(
            text = text,
            style = typography.lyricStrip.copy(color = UNSUNG, textDirection = TextDirection.ContentOrLtr),
            maxLines = 1,
            softWrap = false,
            onTextLayout = { layout = it },
        )
        Text(
            text = text,
            style = typography.lyricStrip.copy(textDirection = TextDirection.ContentOrLtr),
            maxLines = 1,
            softWrap = false,
            modifier =
                Modifier.drawWithContent {
                    val measured = layout ?: return@drawWithContent
                    val sweepX = sweepX(parsed, ranges, measured, playhead.value) ?: return@drawWithContent
                    if (measured.getParagraphDirection(0) == ResolvedTextDirection.Rtl) {
                        clipRect(left = sweepX) { this@drawWithContent.drawContent() }
                    } else {
                        clipRect(right = sweepX) { this@drawWithContent.drawContent() }
                    }
                },
        )
    }
}

// Where the light has got to: every word already sung, plus the part of the current one its own
// duration says has gone by. A word runs until the next one starts; the last runs to the line's end.
private fun sweepX(
    parsed: ParsedRichSyncLine,
    ranges: List<IntRange>,
    layout: TextLayoutResult,
    positionMs: Long,
): Float? {
    val words = parsed.words
    val current = words.indexOfLast { it.startTimeMs <= positionMs }
    if (current < 0) return null
    val range = ranges[current]
    val startMs = words[current].startTimeMs
    val endMs =
        words.getOrNull(current + 1)?.startTimeMs
            ?: parsed.lineEndTimeMs.takeIf { it != Long.MAX_VALUE && it > startMs }
            ?: (startMs + LAST_WORD_FALLBACK_MS)
    val progress = ((positionMs - startMs).toFloat() / (endMs - startMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
    val lastIndex = (layout.layoutInput.text.length).coerceAtLeast(1)
    val left = layout.getHorizontalPosition(range.first.coerceIn(0, lastIndex), usePrimaryDirection = true)
    val right = layout.getHorizontalPosition((range.last + 1).coerceIn(0, lastIndex), usePrimaryDirection = true)
    return left + (right - left) * progress
}

/**
 * The playhead the sweep is drawn from. Positions arrive every 50 ms, a frame or two late and never
 * on a frame. Restarting from each one — rememberSmoothPlayhead, which a word-at-a-time wipe can
 * afford — makes a continuous sweep stall and then jump by that much several times a second. This
 * one keeps its own time on the frame clock and only leans toward the reports, so it moves at a
 * steady speed and never steps back. A report further off than [SWEEP_SNAP_MS] is a seek, and it
 * goes straight there. While [running] is false it simply follows the reports.
 */
@Composable
private fun rememberSweepPlayhead(
    rawMs: Long,
    running: Boolean,
): State<Long> {
    val playhead = remember { mutableLongStateOf(rawMs) }
    val latestRaw by rememberUpdatedState(rawMs)
    LaunchedEffect(running) {
        if (!running) {
            snapshotFlow { latestRaw }.collect { playhead.longValue = it }
            return@LaunchedEffect
        }
        var reported = latestRaw
        var reportedAt = -1L
        var lastFrame = -1L
        var position = playhead.longValue.toDouble()
        while (true) {
            withFrameNanos { now ->
                if (reportedAt < 0L || latestRaw != reported) {
                    reported = latestRaw
                    reportedAt = now
                }
                // Where the last report puts the song by now. Capped, so a player that has stalled
                // (no new report while it buffers) cannot drag the light on without it.
                val target = reported + ((now - reportedAt) / 1_000_000.0).coerceAtMost(SWEEP_MAX_EXTRAPOLATION_MS)
                val step = if (lastFrame < 0L) 0.0 else (now - lastFrame) / 1_000_000.0
                lastFrame = now
                position =
                    if (abs(target - position) > SWEEP_SNAP_MS) {
                        target
                    } else {
                        // Close a share of the gap each frame, in proportion to the frame's length so
                        // 60 Hz and 120 Hz settle alike; max() keeps it from ever running backwards.
                        val advanced = position + step
                        max(position, advanced + (target - advanced) * (step / SWEEP_CATCH_UP_MS).coerceAtMost(1.0))
                    }
                playhead.longValue = position.roundToLong()
            }
        }
    }
    return playhead
}

private fun Line.displayText(): String = words.stripRichSyncTimestamps()

// The unsung part of a word-synced line: present enough to read ahead, clearly not lit yet.
private val UNSUNG = Color.White.copy(alpha = 0.45f)

private val WAITING_DOT_SIZE = 5.dp
private val WAITING_DOT_GAP = 4.dp

// BitChord's line change: long enough to read as travel, short enough that fast lines do not queue.
private const val LINE_CHANGE_MS = 340

// A line's end time is often absent; the last word is then given this long to light.
private const val LAST_WORD_FALLBACK_MS = 600L

// How the sweep's own clock tracks the reported position: the time it takes to close most of a
// gap, the largest gap it closes gradually rather than jumping, and how far past the last report
// it will run on its own.
private const val SWEEP_CATCH_UP_MS = 150.0
private const val SWEEP_SNAP_MS = 500.0
private const val SWEEP_MAX_EXTRAPOLATION_MS = 250.0
