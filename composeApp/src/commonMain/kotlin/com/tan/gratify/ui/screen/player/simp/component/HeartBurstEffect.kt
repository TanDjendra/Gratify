// Adapted from SimpMusic (GPL-3.0); Gratify uses a single accent pulse instead of glitter.
package com.tan.gratify.ui.screen.player.simp.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
import com.tan.gratify.ui.theme.GratifyColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** One bounded pulse per like tap. Loading a liked track never fires it. */
@Stable
class HeartBurstState internal constructor(private val scope: CoroutineScope) {
    internal val progress = Animatable(1f)
    private var animation: Job? = null

    fun fire() {
        animation?.cancel()
        animation = scope.launch {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
        }
    }
}

@Composable
fun rememberHeartBurstState(): HeartBurstState {
    val scope = rememberCoroutineScope()
    return remember(scope) { HeartBurstState(scope) }
}

/** A soft green ring stays inside the touch target, so it cannot cover adjacent controls. */
fun Modifier.heartBurst(state: HeartBurstState): Modifier = drawWithContent {
    val progress = state.progress.value
    if (progress < 1f) {
        drawCircle(
            color = GratifyColors.Accent,
            radius = size.minDimension * (0.28f + 0.18f * progress),
            alpha = 0.24f * (1f - progress),
            style = Stroke(width = 1.5f * density),
        )
    }
    drawContent()
}
