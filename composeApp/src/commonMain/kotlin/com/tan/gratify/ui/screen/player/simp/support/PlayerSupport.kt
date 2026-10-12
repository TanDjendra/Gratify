package com.tan.gratify.ui.screen.player.simp.support
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.animation.core.spring
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
val LocalIsDarkTheme = staticCompositionLocalOf { true }
val LocalLiquidGlassEnabled = staticCompositionLocalOf { true }
suspend fun LazyListState.animateScrollAndAnchorItemTop(
    index: Int,
    /**
     * Extra pixels added to the scroll. Negative leaves that much of the PREVIOUS content visible
     * above [index] — used to expose exactly one physical text row, which is not the same as
     * scrolling to the previous item: a lyric line that wraps is one item but several rows.
     */
    extraOffsetPx: Float = 0f,
) {
    if (index < 0) return
    val initiallyVisible = this.layoutInfo.visibleItemsInfo.any { it.index == index }
    if (!initiallyVisible) {
        this.scrollToItem(index)
    }
    withFrameNanos { }
    val itemInfo =
        this.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    this.animateScrollBy(
        value = (itemInfo.offset - this.layoutInfo.viewportStartOffset).toFloat() + extraOffsetPx,
        // A spring, not a tween. A tween covers a fixed distance in a fixed time, so a one-line
        // step and a six-line jump after a seek both take 650ms — the short one crawls, the long
        // one races. A spring is driven by the distance itself, which is why Apple's page settles
        // the same way whether it moved a little or a lot. Damping just under 1 keeps it soft
        // without bouncing, and low stiffness is what makes it read as gliding rather than
        // snapping into place.
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 180f),
    )
}

fun smoothScrimBrush(
    from: Color,
    to: Color,
    startFraction: Float = 0f,
    endFraction: Float = 1f,
    startY: Float = 0f,
    endY: Float = Float.POSITIVE_INFINITY,
    steps: Int = 24,
): Brush =
    Brush.verticalGradient(
        colorStops =
            Array(steps + 1) { i ->
                val t = i / steps.toFloat()
                val position = startFraction + (endFraction - startFraction) * t
                position to androidx.compose.ui.graphics.lerp(from, to, t * t * (3f - 2f * t))
            },
        startY = startY,
        endY = endY,
    )


val LocalPlayerPlatform = staticCompositionLocalOf { com.tan.gratify.getPlatform() }
@androidx.compose.runtime.Composable fun playerPlatform() = LocalPlayerPlatform.current

val LocalPlayerVideo = staticCompositionLocalOf<(@androidx.compose.runtime.Composable (androidx.compose.ui.Modifier) -> Unit)?> { null }
val LocalPreviewVolume = staticCompositionLocalOf<com.tan.gratify.ui.screen.player.simp.platform.DeviceVolumeController?> { null }
