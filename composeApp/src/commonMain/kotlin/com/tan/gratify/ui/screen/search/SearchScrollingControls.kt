package com.tan.gratify.ui.screen.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.roundToInt

/** Keeps the input mounted, so external People requests can focus it even after browsing scroll. */
@Composable
fun SearchScrollingControls(visible: Boolean, content: @Composable () -> Unit) {
    val fraction by animateFloatAsState(if (visible) 1f else 0f, tween(180))
    Layout(content = content, modifier = Modifier.fillMaxWidth().clipToBounds().graphicsLayer { alpha = fraction }) { measurables, constraints ->
        val child = measurables.single().measure(constraints.copy(minHeight = 0))
        val height = (child.height * fraction).roundToInt()
        layout(child.width, height) { child.placeRelative(0, height - child.height) }
    }
}
