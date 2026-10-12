// Adapted from SimpMusic (GPL-3.0) to Gratify's control sizes and accent.
package com.tan.gratify.ui.screen.player.simp.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tan.gratify.ui.theme.GratifyColors
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.baseline_favorite_24
import gratify.composeapp.generated.resources.baseline_favorite_border_24
import gratify.composeapp.generated.resources.like
import gratify.composeapp.generated.resources.liked
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun HeartCheckBox(
    size: Int = 24,
    checked: Boolean,
    tint: Color = Color.White,
    onStateChange: (() -> Unit)? = null,
) {
    val pulse = rememberHeartBurstState()
    val label = stringResource(if (checked) Res.string.liked else Res.string.like)
    Box(
        modifier = Modifier
            .size(maxOf(size, 48).dp)
            .heartBurst(pulse)
            .clip(CircleShape)
            .semantics { contentDescription = label }
            .toggleable(value = checked, enabled = onStateChange != null, role = Role.Checkbox) {
                if (!checked) pulse.fire()
                onStateChange?.invoke()
            },
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(checked, animationSpec = tween(160), label = "gratifyHeart") { liked ->
            Icon(
                painter = painterResource(if (liked) Res.drawable.baseline_favorite_24 else Res.drawable.baseline_favorite_border_24),
                contentDescription = null,
                tint = if (liked) GratifyColors.Accent else tint,
                modifier = Modifier.size(size.dp).graphicsLayer {
                    val scale = 1f + 0.1f * sin(pulse.progress.value * PI).toFloat()
                    scaleX = scale
                    scaleY = scale
                },
            )
        }
    }
}
