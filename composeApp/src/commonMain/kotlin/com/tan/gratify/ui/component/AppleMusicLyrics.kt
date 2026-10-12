package com.tan.gratify.ui.component

import com.tan.gratify.extension.displayText
import com.tan.gratify.extension.displayWordGroups

// Focus and word lighting adapted from SimpMusic's Apple Music lyrics presentation (GPL-3.0).
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.TextUnit
import java.text.Bidi
import kotlin.math.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tan.gratify.extension.ParsedRichSyncLine
import com.tan.gratify.ui.theme.typo
import kotlin.math.abs

internal fun appleWordProgress(nowMs: Long, startMs: Long, endMs: Long): Float =
    ((nowMs - startMs).toDouble() / (endMs - startMs).coerceAtLeast(1L)).toFloat().coerceIn(0f, 1f)

internal fun appleWordEnd(line: ParsedRichSyncLine, index: Int): Long {
    val start = line.words[index].startTimeMs
    return line.words.getOrNull(index + 1)?.startTimeMs?.takeIf { it > start }
        ?: line.lineEndTimeMs.takeIf { it != Long.MAX_VALUE && it > start }
        ?: (start + (line.words.getOrNull(index - 1)?.let { start - it.startTimeMs }
            ?.coerceIn(100L, 3000L) ?: 500L))
}

@Composable
internal fun Modifier.appleLyricsFocus(distance: Int, hasActiveLine: Boolean, unsynced: Boolean,
    animationsEnabled: Boolean = true): Modifier {
    // SimpMusic's proportional depth, pre-roll and unsynced rules.
    val depth = abs(distance) + if (distance < 0) 1 else 0
    val fontDp = with(LocalDensity.current) { 28.sp.toDp() }
    val targetRadius = if (unsynced || !hasActiveLine || distance == 0 || !animationsEnabled) 0.dp
        else fontDp * (depth * .095f).coerceAtMost(.45f)
    val targetAlpha = when {
        unsynced -> 1f; !hasActiveLine -> .6f; distance == 0 -> 1f
        !animationsEnabled -> .65f; else -> (1f - depth * .25f).coerceAtLeast(.25f)
    }
    if (!animationsEnabled) return graphicsLayer { alpha = targetAlpha }
    val radius = animateDpAsState(targetRadius, tween(400), label = "appleLyricsDepth")
    val opacity = animateFloatAsState(targetAlpha, tween(400), label = "appleLyricsFocus")
    return graphicsLayer {
        alpha = opacity.value
        val pixels = radius.value.toPx()
        renderEffect = if (pixels > 0f) BlurEffect(pixels, pixels, TileMode.Decal) else null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppleMusicLyricsLine(
    text: String,
    translation: String?,
    parsed: ParsedRichSyncLine?,
    nowMs: Long,
    active: Boolean,
    isPlaying: Boolean,
    translationColor: Color,
    modifier: Modifier = Modifier,
    animationsEnabled: Boolean = true,
) {
    val headline = typo().headlineLarge
    val lyricStyle = remember(headline) { headline.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold) }
    Column(modifier.fillMaxWidth().padding(vertical = 20.dp)) {
        if (parsed == null || !animationsEnabled) {
            Text(parsed?.displayText() ?: text, style = lyricStyle, color = if (active) Color.White else Color(0xFF9B9B9B))
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                parsed.displayWordGroups().forEach { group ->
                    Row {
                        group.forEach { index ->
                            val word = parsed.words[index]
                            val end = remember(parsed, index) { appleWordEnd(parsed, index) }
                            key(word.startTimeMs, index) {
                                SimpMusicAnimatedWord(word.text, LocalLayoutDirection.current, index,
                                    word.startTimeMs, end, nowMs, rememberUpdatedState(nowMs),
                                    isActive = active && nowMs in word.startTimeMs until end,
                                    isPast = active && nowMs >= end, isCurrent = active,
                                    customFontSize = 28.sp, glow = Shadow(Color.White),
                                    isLastWord = index == parsed.words.lastIndex,
                                    pendingColorOverride = Color(0xFF8E8E8E), isPlaying = isPlaying)
                            }
                        }
                    }
                }
            }
        }
        if (!translation.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(translation, style = typo().bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
                color = if (active) Color.White.copy(alpha = .78f) else Color(0xFF9B9B9B))
        }
    }
}

// Glyph effects ported from SimpMusic 62e0073, GPL-3.0. Pause and seek also rebase each wipe.
private const val EMP_AMOUNT_REF_MS = 2000f
private const val EMP_BLUR_REF_MS = 3000f
private const val EMP_MIN_DURATION_MS = 1000f
private const val EMP_AMOUNT_GAIN = 0.6f
private const val EMP_BLUR_GAIN = 0.5f
private const val EMP_AMOUNT_CAP = 1.2f
private const val EMP_BLUR_CAP = 0.8f
// AMLL leans on the LAST word of a line — that is where a singer holds, so it earns extra.
private const val EMP_LAST_WORD_AMOUNT = 1.6f
private const val EMP_LAST_WORD_BLUR = 1.5f
// Both are fractions of the font size (AMLL expresses them in `em`), never fixed dp.
private const val EMP_SCALE_EM = 0.1f
private const val EMP_RISE_EM = 0.025f
private const val EMP_GLOW_RADIUS_EM = 0.3f

// The bloom every sung word carries; a held word replaces this with AMLL's larger emphasis glow.
// The flare on the word currently being sung. Bright, because it marks one word out of a line
// rather than washing over the whole sung half.
// The provider caption under the lyrics: quiet enough to sit beneath them, still readable on
// purpose — it is dimmed, never blurred.
// How far the travelling glow reaches, in characters. Below ~1 the light goes out between
// characters; far above it the whole word lifts at once.
private const val FLARE_REACH_CHARS = 3.5f

// How long a word's flare takes to fade in as it becomes the sung one, and to fade out again once
// the playhead has left it. Without this the flare was gated by a hard boolean, so the light died
// at every word boundary and was reborn on the next word — smooth WITHIN a word, broken BETWEEN
// them. Fading the gate makes the outgoing word's last characters still be glowing while the
// incoming word's first ones light up, and the two overlapping reads as one travelling light.
//
// Roughly one sung word long. Much shorter and the hand-over is not visible; much longer and half
// the line glows at once, which is a smear rather than a light.
private const val FLARE_FADE_MS = 2_500

// ...and how long it takes to come UP, which must be its own number. One tween served both
// directions until the release was lengthened for the wake, and that silently made the attack just
// as long: a word starting to be sung took two and a half seconds to reach full brightness, so the
// glow arrived long after the character had finished rising. Neighbouring words sat at different
// points on that slow climb, which is what read as flicker rather than one travelling light.
private const val FLARE_ATTACK_MS = 500

// Distance, in characters, over which the wake behind the playhead falls to about a third of full
// brightness. The decay is EXPONENTIAL, not linear, and that distinction is the whole point: a long
// linear ramp does not make a tail, it makes the whole word evenly bright, because the first
// character of a four-letter word still sits at ninety percent of it. An exponential keeps the
// light concentrated on the character being sung, drops away sharply behind it, and leaves a faint
// glow trailing for a long way — which is what a wake looks like.
private const val FLARE_TAIL_CHARS = 2.5f

// How far the character under the light lifts, as a fraction of the font size — Apple nudges the
// syllable being sung upward, and the eye reads the lift as the light having weight.
//
// Driven by the flare itself rather than by a second animation, so the rise travels with the light
// instead of chasing it. It is a graphicsLayer translation, which happens at DRAW time: the glyph
// moves without the line being measured again, so nothing reflows and no line ever changes height.
// No alpha goes through that layer — alpha below 1 would force an offscreen buffer the size of the
// glyph and slice off the very glow this is meant to accompany.
private const val CHAR_RISE_EM = 0.065f

// How long one character takes to travel from its resting line to the top, once the light has
// touched it. Deliberately longer than a character is sung for: the lift is NOT finished before the
// next character starts its own, so at any instant a stretch of the line is caught at different
// heights and the whole thing reads as a wave rolling behind the singing.
//
// This is why the lift cannot be computed from the word's progress. That number stops advancing the
// moment the word is done, so a word sung in less than this would freeze its characters half-risen.
// Each character needs a clock of its own, started when the light arrives and left to finish on its
// own time.
private const val CHAR_RISE_MS = 1_000

private const val FOOTER_ALPHA = 0.45f

private const val SUNG_BASE_GLOW_ALPHA = 0.95f
private const val SUNG_BASE_GLOW_EM = 0.26f


// makeEmpEasing(0.5): bezIn up to the midpoint, then 1 - bezOut back down — a bump that rises
// faster than it falls, which is why it reads as a voice pushing rather than a sine wobble.
private val EmpBezIn = CubicBezierEasing(0.2f, 0.4f, 0.58f, 1f)
private val EmpBezOut = CubicBezierEasing(0.3f, 0f, 0.58f, 1f)

private fun empEasing(x: Float): Float =
    if (x < 0.5f) {
        EmpBezIn.transform((x / 0.5f).coerceIn(0f, 1f))
    } else {
        1f - EmpBezOut.transform(((x - 0.5f) / 0.5f).coerceIn(0f, 1f))
    }


private val DimOriginalColor = Color(0xFF9B9B9B)
private val DimRichPendingColor = Color(0xFF8E8E8E)
@Composable
private fun SimpMusicAnimatedWord(
    word: String,
    lineDirection: LayoutDirection,
    wordIndex: Int,
    wordStartTimeMs: Long,
    wordEndTimeMs: Long,
    currentTimeMs: Long,
    // The frame-interpolated playhead, taken as State and read ONLY inside the effect below. Taking
    // it as a plain Long would recompose every word in the line on every frame; a State read from a
    // suspend body is not a composition read at all.
    playheadMs: State<Long>,
    isActive: Boolean,
    isPast: Boolean,
    isCurrent: Boolean,
    customFontSize: TextUnit? = null,
    glow: Shadow? = null,
    // AMLL emphasises the closing word of a line more than the rest — that is where a singer holds.
    isLastWord: Boolean = false,
    // Null keeps Classic's DimRichPendingColor untouched.
    pendingColorOverride: Color? = null,
    isPlaying: Boolean = true,
) {
    val wordBidi =
        remember(word, lineDirection) {
            val fallback =
                if (lineDirection == LayoutDirection.Rtl) Bidi.DIRECTION_DEFAULT_RIGHT_TO_LEFT else Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT
            Bidi(word, fallback)
        }
    val wordDirection = if (wordBidi.baseIsLeftToRight()) TextDirection.Ltr else TextDirection.Rtl
    val style =
        typo().headlineLarge.copy(
            fontSize = customFontSize ?: typo().headlineLarge.fontSize,
            textDirection = wordDirection,
        )

    if (!isCurrent) {
        Text(text = word, style = style, color = pendingColorOverride ?: DimOriginalColor)
        return
    }

    // Wall-clock wipe driven by an Animatable.
    // - Future word (not active, not past): progress stays at 0.
    // - Active word: snap to current % then animateTo(1f) over the remaining duration of the
    //   word, in real wall-clock time. Independent of timeline emit rate, so wipe is smooth.
    // - Past word: snap to 1f.

    // The gap to the next word, and nothing else. Both the floor that used to sit here (100 ms) and
    // the one on the wipe below (150 ms) made a short word's animation outlive the word, so by the
    // time it finished the next two or three had started their own and the line looked like the
    // words were chasing each other. A syllable sung in 40 ms gets a 40 ms wipe; only the divide
    // needs protecting.
    val wordDurationMs = (wordEndTimeMs - wordStartTimeMs).coerceAtLeast(1L)
    val anim =
        remember(wordStartTimeMs, wordEndTimeMs) {
            val initial =
                ((currentTimeMs - wordStartTimeMs).toFloat() / wordDurationMs.toFloat())
                    .coerceIn(0f, 1f)
            androidx.compose.animation.core.Animatable(initial)
        }

    LaunchedEffect(wordStartTimeMs, wordEndTimeMs, isActive, isPast, currentTimeMs, isPlaying) {
        when {
            // Nothing to carry over any more: the wipe now runs for exactly the word's own span,
            // so it reaches 1f on the same frame this flips. Only a word shorter than one frame
            // arrives unfinished, and that one has no time to animate in anyway.
            isPast -> anim.snapTo(1f)
            isActive -> {
                // From the interpolated playhead, not from the last tick. isActive now flips within
                // a frame of the word's real start, so a tick-aged `now` would snap the wipe behind
                // where it belongs and then stretch it past the end of the word.
                val now = playheadMs.value
                val current =
                    ((now - wordStartTimeMs).toFloat() / wordDurationMs.toFloat())
                        .coerceIn(0f, 1f)
                anim.snapTo(current)
                // Exactly what is left of this word, so it finishes as the next one starts and no
                // two wipes are ever in flight at once.
                val remainingMs = (wordEndTimeMs - now).coerceAtLeast(0L).toInt().coerceAtLeast(1)
                if (isPlaying) anim.animateTo(1f, tween(remainingMs, easing = LinearEasing))
            }
            // Reset on seek, including seeks backwards inside the same line.
            else -> anim.snapTo(0f)
        }
    }

    val progress = anim.value

    // The word's position, resolved by STATE rather than read raw. The Animatable is what carries
    // the 100ms timeline ticks at frame rate — it snaps to the real position and then animates to
    // the end of the word in wall-clock time, so the wipe keeps moving between ticks instead of
    // stepping ten times a second.
    //
    // But it is only ever snapped on the isActive and isPast branches. A word that has not started
    // holds whatever it was constructed with, which is why reading it directly lit the first letter
    // of every upcoming word. Deciding 0 / anim / 1 from the state fixes that without giving up the
    // interpolation.
    val wordProgress =
        when {
            isPast -> 1f
            isActive -> progress
            else -> 0f
        }

    // AMLL's emphasis strengths, derived from the word's OWN duration.
    val emphasisDurationMs = max(EMP_MIN_DURATION_MS, wordDurationMs.toFloat())
    val amount =
        if (glow == null) {
            0f
        } else {
            val raw = emphasisDurationMs / EMP_AMOUNT_REF_MS
            val shaped = if (raw > 1f) sqrt(raw) else raw * raw * raw
            min(EMP_AMOUNT_CAP, shaped * EMP_AMOUNT_GAIN * if (isLastWord) EMP_LAST_WORD_AMOUNT else 1f)
        }
    val blurAmount =
        if (glow == null) {
            0f
        } else {
            val raw = emphasisDurationMs / EMP_BLUR_REF_MS
            val shaped = if (raw > 1f) sqrt(raw) else raw * raw * raw
            min(EMP_BLUR_CAP, shaped * EMP_BLUR_GAIN * if (isLastWord) EMP_LAST_WORD_BLUR else 1f)
        }
    // The flare's on/off, animated instead of switched. A word that has just been sung keeps its
    // light for FLARE_FADE_MS while the next word's comes up, so the hand-over happens across the
    // word boundary rather than at it. Settled words hold 0 and animate nothing.
    val flareGate by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        // The duration is picked from the direction being travelled: snap up with the singing, ebb
        // away slowly behind it.
        animationSpec =
            tween(
                durationMillis = if (!isPlaying) 0 else if (isActive) FLARE_ATTACK_MS else FLARE_FADE_MS,
                easing = LinearEasing,
            ),
        label = "flareGate",
    )

    val eased = if (amount <= 0f && blurAmount <= 0f) 0f else empEasing(wordProgress)
    val fontPx = with(LocalDensity.current) { style.fontSize.toPx() }
    val heldGlow =
        if (eased * blurAmount <= 0.01f) {
            null
        } else {
            glow?.copy(
                color = glow.color.copy(alpha = (eased * blurAmount).coerceIn(0f, 1f)),
                blurRadius = min(EMP_GLOW_RADIUS_EM, blurAmount * EMP_GLOW_RADIUS_EM) * fontPx,
            )
        }

    Box(
        modifier =
            Modifier.clearAndSetSemantics { contentDescription = word }.graphicsLayer {
                val scale = 1f + eased * EMP_SCALE_EM * amount
                scaleX = scale
                scaleY = scale
                // Rises slightly as it swells — AMLL's offsetY. Negative is upward.
                translationY = -eased * EMP_RISE_EM * amount * fontPx
            },
    ) {
        if (!wordBidi.isLeftToRight) {
            // Keep Arabic shaping and combining marks intact, including RTL words in an LTR line.
            ShapedLyricsWord(
                word = word,
                style = style,
                progress = wordProgress,
                isRtl = wordDirection == TextDirection.Rtl,
                restingColor = pendingColorOverride ?: DimRichPendingColor,
                glow = heldGlow ?: glow?.copy(blurRadius = SUNG_BASE_GLOW_EM * fontPx),
                flare = flareGate,
                fontPx = fontPx,
                isPlaying = isPlaying,
            )
        } else {
            val chars = word.toCharArray()
            val charCount = chars.size.coerceAtLeast(1)
            // A Latin word keeps its character order even inside an RTL line.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row {
                    chars.forEachIndexed { charIndex, ch ->
                        val charFrom = charIndex.toFloat() / charCount
                        val charTo = (charIndex + 1).toFloat() / charCount
                        val charProgress = ((wordProgress - charFrom) / (charTo - charFrom)).coerceIn(0f, 1f)
                        val charPast = wordProgress >= charTo
                        val charActive = isActive && wordProgress >= charFrom && wordProgress < charTo
                        // The flare is a CONTINUOUS falloff from the playhead, not an on/off per character:
                        // switching per character killed the light at every boundary and lit it again on the
                        // next one, which read as flicker. Fading by distance lets the brightness hand over
                        // between neighbours, so one travelling point of light moves across the word.
                        val charCenter = (charFrom + charTo) / 2f
                        val reach = (FLARE_REACH_CHARS / charCount).coerceAtLeast(0.0001f)
                        val charFlare =
                            if (flareGate <= 0f || glow == null) {
                                0f
                            } else {
                                // Ahead of the light, a short linear ramp so a character brightens as it
                                // is approached; behind it, an exponential wake.
                                val delta = wordProgress - charCenter
                                val shape =
                                    if (delta > 0f) {
                                        exp(-delta / (FLARE_TAIL_CHARS / charCount))
                                    } else {
                                        (1f - abs(delta) / reach).coerceIn(0f, 1f)
                                    }
                                shape * flareGate
                            }
                        // Lifted when the light touches it, over CHAR_RISE_MS, and LEFT THERE. Apple
                        // never brings the glyph back down — the sung half of a line simply sits higher
                        // than the half still to come — so there is no fall to animate, only an arrival.
                        //
                        // The trigger is instant, the travel is not: the light moves on long before the
                        // character has finished rising, which is exactly what makes the line ripple
                        // instead of stepping. Its own animation, on its own clock, so a word sung faster
                        // than the rise still completes.
                        val charRise by animateFloatAsState(
                            targetValue = if (glow != null && charProgress > 0f) 1f else 0f,
                            animationSpec = tween(if (isPlaying) CHAR_RISE_MS else 0, easing = FastOutSlowInEasing),
                            label = "charRise",
                        )
                        val restingColor = pendingColorOverride ?: DimRichPendingColor
                        Box(
                            modifier =
                                Modifier.graphicsLayer {
                                    translationY = -charRise * CHAR_RISE_EM * fontPx
                                },
                        ) {
                            // Glow: transparent ink, so only the Shadow lands and it follows the glyph
                            // outline instead of boxing the character.
                            //
                            val glowShadow = heldGlow ?: glow?.copy(blurRadius = SUNG_BASE_GLOW_EM * fontPx)
                            if (glowShadow != null) {
                                Text(
                                    text = ch.toString(),
                                    // Intensity goes into the Shadow's own alpha. NOT graphicsLayer.alpha:
                                    // any alpha below 1 forces Compose to render the node into an offscreen
                                    // layer first, and that layer is only as big as the Text's bounds — so
                                    // the bloom, which by definition spills outside them, comes back sliced
                                    // into a rectangle.
                                    //
                                    // The node is still composed unconditionally, at alpha 0 when the
                                    // playhead is far away. That is what stops the hand-over from
                                    // flickering: A fades down and B fades up, neither is ever added to or
                                    // removed from the tree.
                                    style =
                                        style.copy(
                                            shadow =
                                                glowShadow.copy(
                                                    color = glowShadow.color.copy(alpha = SUNG_BASE_GLOW_ALPHA * charFlare),
                                                ),
                                        ),
                                    color = Color.Transparent,
                                )
                            }
                            Text(
                                text = ch.toString(),
                                style = style,
                                color =
                                    when {
                                        charPast -> Color.White
                                        charActive -> lerp(restingColor, Color.White, charProgress)
                                        else -> restingColor
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
private fun ShapedLyricsWord(
    word: String,
    style: TextStyle,
    progress: Float,
    isRtl: Boolean,
    restingColor: Color,
    glow: Shadow?,
    flare: Float,
    fontPx: Float,
    isPlaying: Boolean,
) {
    val rise by animateFloatAsState(
        targetValue = if (glow != null && progress > 0f) 1f else 0f,
        animationSpec = tween(if (isPlaying) CHAR_RISE_MS else 0, easing = FastOutSlowInEasing),
        label = "shapedWordRise",
    )
    Box(Modifier.graphicsLayer { translationY = -rise * CHAR_RISE_EM * fontPx }) {
        Text(text = word, style = style, color = restingColor)
        if (glow != null) {
            Text(
                text = word,
                modifier = Modifier.clearAndSetSemantics {},
                style = style.copy(shadow = glow.copy(color = glow.color.copy(alpha = SUNG_BASE_GLOW_ALPHA * flare))),
                color = Color.Transparent,
            )
        }
        Text(
            text = word,
            style = style,
            color = Color.White,
            modifier =
                Modifier
                    .clearAndSetSemantics {}
                    .drawWithContent {
                        if (progress >= 1f) {
                            drawContent()
                        } else if (progress > 0f) {
                            clipRect(
                                left = if (isRtl) size.width * (1f - progress) else 0f,
                                right = if (isRtl) size.width else size.width * progress,
                            ) { this@drawWithContent.drawContent() }
                        }
                    },
        )
    }
}

