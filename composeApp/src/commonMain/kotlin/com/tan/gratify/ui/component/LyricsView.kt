package com.tan.gratify.ui.component

import com.tan.gratify.ui.component.lyrics.toShareLyricsLines
import com.tan.gratify.ui.component.lyrics.stripRichLyricsTimestamps
import androidx.compose.ui.text.style.TextOverflow

import com.tan.gratify.ui.theme.GratifyColors
import com.tan.gratify.ui.theme.GratifyShapes

import androidx.compose.animation.Animatable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.tan.domain.data.model.streams.TimeLine
import com.tan.gratify.extension.KeepScreenOn
import com.tan.gratify.extension.ParsedRichSyncLine
import com.tan.gratify.extension.animateScrollAndCentralizeItem
import com.tan.gratify.extension.formatDuration
import com.tan.gratify.extension.hsvToColor
import com.tan.gratify.extension.parseRichSyncWords
import com.tan.gratify.ui.navigation.destination.list.ArtistDestination
import com.tan.gratify.ui.theme.typo
import com.tan.gratify.viewModel.NowPlayingScreenData
import com.tan.gratify.viewModel.SharedViewModel
import com.tan.gratify.viewModel.UIEvent
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.CupertinoMaterials
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import gratify.composeapp.generated.resources.lyrics_animation_on
import gratify.composeapp.generated.resources.lyrics_no_animation
import gratify.composeapp.generated.resources.share_lyrics
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.baseline_more_vert_24
import gratify.composeapp.generated.resources.crossfading
import gratify.composeapp.generated.resources.unavailable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val TAG = "LyricsView"

// Minimum wipe animation duration. Words shorter than this still wipe over MIN_WIPE_MS so the
// motion stays perceivable; the snap-to-1f on isPast catches up at the actual word end.
private const val MIN_WIPE_MS = 150

// Repeated lyrics palette tokens hoisted to file scope: avoids re-allocating
// the same Color() objects on every recomposition of every line item.
private val DimOriginalColor = GratifyColors.TextSecondary.copy(alpha = 0.78f)
private val DimTranslatedColor = GratifyColors.TextSecondary.copy(alpha = 0.7f)
private val DimRichPendingColor = GratifyColors.TextSecondary.copy(alpha = 0.8f)

private data class TimedLineIndex(
    val index: Int,
    val startTimeMs: Long,
)

/**
 * Returns the original line index of the last [TimedLineIndex] whose [TimedLineIndex.startTimeMs]
 * is `<= nowMs`. Assumes the receiver is sorted ascending by [TimedLineIndex.startTimeMs].
 *
 * Rules:
 *  - empty list -> -1
 *  - nowMs strictly before the first start time -> -1
 *  - nowMs after the last start time -> the last entry's original index (sticky last line)
 */
private fun List<TimedLineIndex>.activeIndexAt(nowMs: Long): Int {
    if (isEmpty()) return -1
    if (nowMs < first().startTimeMs) return -1
    // Binary search for the last item whose startTimeMs <= nowMs.
    var lo = 0
    var hi = size - 1
    var ans = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (this[mid].startTimeMs <= nowMs) {
            ans = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return if (ans >= 0) this[ans].index else -1
}

/**
 * Builds a [Map] from each ORIGINAL line index to its closest synced translated `words`
 * within [thresholdMs]. Two-pointer over both sorted lists; on ties an earlier translated
 * line (smaller startTimeMs in the sorted list) wins for determinism.
 *
 * Lines with invalid `startTimeMs` on either side are skipped.
 */
private fun buildSyncedTranslatedWordsByLineIndex(
    originalLines: List<com.tan.domain.data.model.metadata.Line>,
    translatedLines: List<com.tan.domain.data.model.metadata.Line>,
    thresholdMs: Long = 1000L,
): Map<Int, String> {
    if (originalLines.isEmpty() || translatedLines.isEmpty()) return emptyMap()

    // Sort translated entries by start time. We keep the original list order as a
    // tie-breaker via stable sort: the FIRST translated line in the SORTED list wins
    // when the time delta is equal.
    val sortedTranslated =
        translatedLines
            .mapNotNull { line ->
                val ts = line.startTimeMs.toLongOrNull() ?: return@mapNotNull null
                ts to line.words
            }.sortedBy { it.first }

    if (sortedTranslated.isEmpty()) return emptyMap()

    // Original lines paired with their parsed timestamp + original index, sorted by time.
    data class OriginalEntry(val index: Int, val ts: Long)

    val sortedOriginal =
        originalLines
            .mapIndexedNotNull { index, line ->
                val ts = line.startTimeMs.toLongOrNull() ?: return@mapIndexedNotNull null
                OriginalEntry(index, ts)
            }.sortedBy { it.ts }

    if (sortedOriginal.isEmpty()) return emptyMap()

    val result = HashMap<Int, String>(sortedOriginal.size)
    var j = 0
    for (orig in sortedOriginal) {
        // Advance j so that sortedTranslated[j] is the first translated entry with ts >= orig.ts,
        // or the last entry if everything is smaller.
        while (j + 1 < sortedTranslated.size && sortedTranslated[j + 1].first <= orig.ts) {
            j++
        }
        // Candidate window: j and j+1 (the next one), pick whichever is closer.
        val candA = sortedTranslated[j]
        val diffA = abs(candA.first - orig.ts)
        var bestTs = candA.first
        var bestWords = candA.second
        var bestDiff = diffA
        if (j + 1 < sortedTranslated.size) {
            val candB = sortedTranslated[j + 1]
            val diffB = abs(candB.first - orig.ts)
            // Tie-break: prefer earlier (smaller startTimeMs) translated line.
            if (diffB < bestDiff) {
                bestTs = candB.first
                bestWords = candB.second
                bestDiff = diffB
            }
        }
        if (bestDiff < thresholdMs) {
            result[orig.index] = bestWords
            // Suppress unused warning while keeping the chosen ts visible for future tweaks.
            @Suppress("UNUSED_VARIABLE")
            val _bt = bestTs
        }
    }
    return result
}

@Composable
fun LyricsView(
    lyricsData: NowPlayingScreenData.LyricsData,
    timeLine: StateFlow<TimeLine>,
    onLineClick: (Float) -> Unit,
    modifier: Modifier = Modifier,
    showScrollShadows: Boolean = false,
    backgroundColor: Color = GratifyColors.SurfaceRaised,
    hasBlurBackground: Boolean = false,
    accentColor: Color = Color.White.copy(alpha = 0.82f),
    appleMusicStyle: Boolean = true,
    isPlaying: Boolean = false,
    animationsEnabled: Boolean = false,
    onShareLyrics: ((Int) -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    val current by timeLine.collectAsStateWithLifecycle()

    val timedLineIndexes =
        remember(lyricsData.lyrics.lines) {
            lyricsData.lyrics.lines
                .orEmpty()
                .mapIndexedNotNull { index, line ->
                    line.startTimeMs.toLongOrNull()?.let { TimedLineIndex(index, it) }
                }.let { timed ->
                    if (timed.distinctBy { it.startTimeMs }.size <= 1) emptyList() else timed.sortedBy { it.startTimeMs }
                }
        }

    val currentLineIndex by remember(timedLineIndexes) {
        derivedStateOf {
            val now = current.current
            if (now <= 0L) -1 else timedLineIndexes.activeIndexAt(now)
        }
    }

    val syncedTranslatedWordsByLineIndex =
        remember(
            lyricsData.lyrics.lines,
            lyricsData.translatedLyrics?.first?.lines,
        ) {
            buildSyncedTranslatedWordsByLineIndex(
                originalLines = lyricsData.lyrics.lines.orEmpty(),
                translatedLines = lyricsData.translatedLyrics?.first?.lines.orEmpty(),
                thresholdMs = 1000L,
            )
        }
    LaunchedEffect(currentLineIndex, lyricsData.lyrics.syncType, animationsEnabled) {
        if (currentLineIndex > -1 &&
            (lyricsData.lyrics.syncType == "LINE_SYNCED" || lyricsData.lyrics.syncType == "RICH_SYNCED")
        ) {
            listState.animateScrollAndCentralizeItem(currentLineIndex)
        }
    }

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(lyricsData.lyrics.lines?.size ?: 0) { index ->
                val line = lyricsData.lyrics.lines?.getOrNull(index)
                // Translated lyrics: synced -> precomputed map by line index, unsynced -> by index.
                val translatedWords =
                    if (lyricsData.lyrics.syncType == "LINE_SYNCED" || lyricsData.lyrics.syncType == "RICH_SYNCED") {
                        syncedTranslatedWordsByLineIndex[index]
                    } else {
                        lyricsData.translatedLyrics
                            ?.first
                            ?.lines
                            ?.getOrNull(index)
                            ?.words
                    }

                line?.words?.let { words ->
                    when {
                        appleMusicStyle -> {
                            val unsynced = lyricsData.lyrics.syncType != "LINE_SYNCED" && lyricsData.lyrics.syncType != "RICH_SYNCED"
                            val parsed = remember(words, line.startTimeMs, line.endTimeMs, lyricsData.lyrics.syncType) {
                                if (lyricsData.lyrics.syncType == "RICH_SYNCED") parseRichSyncWords(words, line.startTimeMs, line.endTimeMs) else null
                            }
                            val plainText = remember(words, parsed) {
                                if (parsed == null) stripRichLyricsTimestamps(words) else words
                            }
                            val active = unsynced || index == currentLineIndex
                            AppleMusicLyricsLine(
                                text = plainText,
                                translation = translatedWords,
                                parsed = parsed,
                                nowMs = if (parsed != null && active) current.current else 0L,
                                active = active,
                                isPlaying = parsed != null && active && isPlaying,
                                translationColor = accentColor,
                                animationsEnabled = animationsEnabled,
                                modifier = Modifier.appleLyricsFocus(
                                    if (currentLineIndex >= 0) index - currentLineIndex else 0,
                                    hasActiveLine = currentLineIndex >= 0, unsynced = unsynced,
                                ).combinedClickable(
                                    onLongClick = onShareLyrics?.let { callback -> { callback(index) } },
                                    onClick = {
                                        if (!unsynced) line.startTimeMs.toFloatOrNull()?.let { start ->
                                            if (timeLine.value.total > 0) onLineClick((start * 100 / timeLine.value.total).coerceIn(0f, 100f))
                                        }
                                    },
                                )
                            )
                        }
                        // Rich sync: parse and use RichSyncLyricsLineItem
                        lyricsData.lyrics.syncType == "RICH_SYNCED" -> {
                            val parsedLine =
                                remember(words, line.startTimeMs, line.endTimeMs) {
                                    val result = parseRichSyncWords(words, line.startTimeMs, line.endTimeMs)
                                    result
                                }

                            if (parsedLine != null) {
                                RichSyncLyricsLineItem(
                                    parsedLine = parsedLine,
                                    translatedWords = translatedWords,
                                    accentColor = accentColor,
                                    currentTimeMs = current.current,
                                    isCurrent = index == currentLineIndex,
                                    modifier =
                                        Modifier
                                            .clickable {
                                                line.startTimeMs.toFloatOrNull()?.let { start ->
                                                    if (timeLine.value.total > 0) onLineClick((start * 100 / timeLine.value.total).coerceIn(0f, 100f))
                                                }
                                            },
                                )
                            } else {
                                // Fallback to regular line item if parsing fails
                                LyricsLineItem(
                                    originalWords = words,
                                    translatedWords = translatedWords,
                                    accentColor = accentColor,
                                    isBold = index <= currentLineIndex,
                                    isCurrent = index == currentLineIndex,
                                    modifier =
                                        Modifier
                                            .clickable {
                                                line.startTimeMs.toFloatOrNull()?.let { start ->
                                                    if (timeLine.value.total > 0) onLineClick((start * 100 / timeLine.value.total).coerceIn(0f, 100f))
                                                }
                                            },
                                )
                            }
                        }

                        // Line sync or unsynced: use existing LyricsLineItem
                        else -> {
                            LyricsLineItem(
                                originalWords = words,
                                translatedWords = translatedWords,
                                accentColor = accentColor,
                                isBold = index <= currentLineIndex || lyricsData.lyrics.syncType != "LINE_SYNCED",
                                isCurrent = index == currentLineIndex || lyricsData.lyrics.syncType != "LINE_SYNCED",
                                modifier =
                                    Modifier
                                        .clickable(enabled = lyricsData.lyrics.syncType == "LINE_SYNCED") {
                                            line.startTimeMs.toFloatOrNull()?.let { start ->
                                                    if (timeLine.value.total > 0) onLineClick((start * 100 / timeLine.value.total).coerceIn(0f, 100f))
                                                }
                                        },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LyricsLineItem(
    originalWords: String,
    translatedWords: String?,
    isBold: Boolean,
    isCurrent: Boolean = false,
    modifier: Modifier = Modifier,
    accentColor: Color = Color.White.copy(alpha = 0.82f),
) {
    Crossfade(targetState = isBold) {
        if (it) {
            Column(
                modifier = modifier,
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = originalWords,
                    style = typo().headlineLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                    color = if (isCurrent) Color.White else DimOriginalColor,
                )
                if (translatedWords != null) {
                    Text(
                        text = translatedWords,
                        style = typo().bodyMedium,
                        color = if (isCurrent) accentColor else DimTranslatedColor,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
    if (!isBold) {
        Column(
            modifier = modifier,
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = originalWords,
                style = typo().headlineLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                color = DimOriginalColor,
            )
            if (translatedWords != null) {
                Text(
                    text = translatedWords,
                    style = typo().bodyMedium,
                    color = DimTranslatedColor,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RichSyncLyricsLineItem(
    parsedLine: ParsedRichSyncLine,
    translatedWords: String?,
    currentTimeMs: Long,
    isCurrent: Boolean,
    customFontSize: TextUnit? = null,
    customPadding: Dp = 12.dp,
    modifier: Modifier = Modifier,
    accentColor: Color = Color.White.copy(alpha = 0.82f),
) {
    val currentWordIndex by remember(currentTimeMs, parsedLine.words) {
        derivedStateOf {
            if (!isCurrent) return@derivedStateOf -1
            parsedLine.words.indexOfLast { it.startTimeMs <= currentTimeMs }
        }
    }

    Column(
        modifier = modifier,
    ) {
        Spacer(modifier = Modifier.height(customPadding))

        // Original lyrics with rich sync highlighting - using FlowRow for word wrapping
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            parsedLine.words.forEachIndexed { index, wordTiming ->
                // Calculate word end time (start time of next word or line end time)
                // If last word and lineEndTimeMs is invalid (Long.MAX_VALUE), estimate based on previous word duration
                val wordEndTimeMs =
                    if (index < parsedLine.words.size - 1) {
                        parsedLine.words[index + 1].startTimeMs
                    } else if (parsedLine.lineEndTimeMs == Long.MAX_VALUE || parsedLine.lineEndTimeMs <= wordTiming.startTimeMs) {
                        // Estimate: use previous word duration or default 500ms
                        if (index > 0 && parsedLine.words[index - 1].startTimeMs < wordTiming.startTimeMs) {
                            val prevWordDuration = wordTiming.startTimeMs - parsedLine.words[index - 1].startTimeMs
                            wordTiming.startTimeMs + prevWordDuration
                        } else {
                            wordTiming.startTimeMs + 500L // Default 500ms if no reference
                        }
                    } else {
                        parsedLine.lineEndTimeMs
                    }
                AnimatedWord(
                    word = wordTiming.text,
                    wordIndex = index,
                    wordStartTimeMs = wordTiming.startTimeMs,
                    wordEndTimeMs = wordEndTimeMs,
                    currentTimeMs = currentTimeMs,
                    isActive = isCurrent && index == currentWordIndex,
                    isPast = isCurrent && index < currentWordIndex,
                    isCurrent = isCurrent,
                    customFontSize = customFontSize,
                )
            }
        }

        // Translated lyrics (line-level, no word sync)
        if (translatedWords != null) {
            Text(
                text = translatedWords,
                style = typo().bodyMedium,
                color = if (isCurrent) accentColor else DimTranslatedColor,
            )
        }

        Spacer(modifier = Modifier.height(customPadding))
    }
}

@Composable
private fun AnimatedWord(
    word: String,
    wordIndex: Int,
    wordStartTimeMs: Long,
    wordEndTimeMs: Long,
    currentTimeMs: Long,
    isActive: Boolean,
    isPast: Boolean,
    isCurrent: Boolean,
    customFontSize: TextUnit? = null,
) {
    val style =
        typo().headlineLarge.copy(
            fontSize = customFontSize ?: typo().headlineLarge.fontSize,
        )

    if (!isCurrent) {
        Text(text = word, style = style, color = DimOriginalColor)
        return
    }

    // Wall-clock wipe driven by an Animatable.
    // - Future word (not active, not past): progress stays at 0.
    // - Active word: snap to current % then animateTo(1f) over the remaining duration of the
    //   word, in real wall-clock time. Independent of timeline emit rate, so wipe is smooth.
    // - Past word: snap to 1f.
    val wordDurationMs = (wordEndTimeMs - wordStartTimeMs).coerceAtLeast(100L)
    val anim =
        remember(wordStartTimeMs, wordEndTimeMs) {
            val initial =
                ((currentTimeMs - wordStartTimeMs).toFloat() / wordDurationMs.toFloat())
                    .coerceIn(0f, 1f)
            androidx.compose.animation.core.Animatable(initial)
        }

    LaunchedEffect(wordStartTimeMs, wordEndTimeMs, isActive, isPast) {
        when {
            isPast -> anim.snapTo(1f)
            isActive -> {
                val now = currentTimeMs
                val current =
                    ((now - wordStartTimeMs).toFloat() / wordDurationMs.toFloat())
                        .coerceIn(0f, 1f)
                anim.snapTo(current)
                // Ensure minimum visible wipe duration so very short words don't flash.
                // Tradeoff: visual may finish ~MIN_WIPE_MS after the actual word end, but the
                // next isPast=true transition will snap to 1f so it stays consistent.
                val remainingMs =
                    (wordEndTimeMs - now).coerceAtLeast(0L).toInt().coerceAtLeast(MIN_WIPE_MS)
                anim.animateTo(1f, tween(remainingMs, easing = LinearEasing))
            }
            // Future word (not active, not past): keep current value.
            // Don't snap to 0 — playback position can jitter backwards by a few ms,
            // briefly flipping isActive false. Snapping would jerk the wipe back.
        }
    }

    val progress = anim.value

    Box {
        // Bottom layer: dimmed pending color, drawn for the whole word.
        Text(text = word, style = style, color = DimRichPendingColor)
        // Top layer: white, clipped horizontally so only the wiped portion shows.
        Text(
            text = word,
            style = style,
            color = Color.White,
            modifier =
                Modifier.drawWithContent {
                    clipRect(right = size.width * progress) {
                        this@drawWithContent.drawContent()
                    }
                },
        )
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@ExperimentalMaterial3Api
@ExperimentalFoundationApi
@Composable
fun FullscreenLyricsSheet(
    sharedViewModel: SharedViewModel,
    navController: NavController,
    color: Color = GratifyColors.SurfaceRaised,
    shouldHaze: Boolean,
    onDismiss: () -> Unit,
) {
    val screen by sharedViewModel.nowPlayingScreenData.collectAsStateWithLifecycle()
    val controls by sharedViewModel.controllerState.collectAsStateWithLifecycle()
    val motion by sharedViewModel.lyricsAnimationsEnabled.collectAsStateWithLifecycle()
    val handler: com.tan.domain.mediaservice.handler.MediaPlayerHandler = org.koin.compose.koinInject()
    val queueData by handler.queueData.collectAsStateWithLifecycle()
    val queue = queueData?.data?.listTracks.orEmpty()
    val now by sharedViewModel.nowPlayingState.collectAsStateWithLifecycle()
    val index = queue.indexOfFirst { it.videoId == now?.track?.videoId }
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = index.coerceAtLeast(0)) { queue.size.coerceAtLeast(1) }
    var queueSheet by remember { mutableStateOf(false) }
    var infoSheet by remember { mutableStateOf(false) }
    var moreSheet by remember { mutableStateOf(false) }
    PlayerModalSheet(onDismiss = onDismiss) { dismiss ->
        com.tan.gratify.ui.screen.player.AppleMusicPlayerContent(
            state = com.tan.gratify.ui.screen.player.AppleMusicPlayerState(
                screen, controls, sharedViewModel.timeline, queue, index, color,
                com.tan.gratify.ui.screen.player.AppleMusicPlayerTab.LYRICS, pager, lyricsAnimationsEnabled = motion),
            actions = com.tan.gratify.ui.screen.player.AppleMusicPlayerActions(
                sharedViewModel::onUIEvent, {}, handler::playMediaItemInMediaSource, sharedViewModel::setBitmap,
                dismiss, { moreSheet = true }, { infoSheet = true }, {
                    now?.track?.artists?.firstOrNull()?.id?.let { artist ->
                        dismiss(); navController.navigate(com.tan.gratify.ui.navigation.destination.list.ArtistDestination(artist))
                    }
                }, {}, {}, { queueSheet = true }, onLyricsAnimationsChange = sharedViewModel::setLyricsAnimationsEnabled),
            fullscreenLyrics = true,
        )
        if (queueSheet) QueueBottomSheet(onDismiss = { queueSheet = false })
        if (infoSheet) InfoPlayerBottomSheet(onDismiss = { infoSheet = false })
        if (moreSheet) NowPlayingBottomSheet(onDismiss = { moreSheet = false }, navController = navController,
            onNavigateToOtherScreen = dismiss, song = null, setSleepTimerEnable = true, changeMainLyricsProviderEnable = true)
    }
}
