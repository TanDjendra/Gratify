package com.tan.gratify.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tan.domain.mediaservice.handler.ControlState
import com.tan.domain.mediaservice.handler.RepeatState
import com.tan.gratify.ui.theme.GratifyColors
import com.tan.gratify.viewModel.UIEvent
import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun PlayerControlLayout(
    controllerState: ControlState,
    isSmallSize: Boolean = false,
    onUIEvent: (UIEvent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(if (isSmallSize) 48.dp else 80.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaybackControlIcon(Icons.Rounded.Shuffle, stringResource(Res.string.shuffle),
            tint = if (controllerState.isShuffle) GratifyColors.Accent else GratifyColors.TextSecondary,
            onClick = { onUIEvent(UIEvent.Shuffle) })
        PlaybackControlIcon(Icons.Rounded.SkipPrevious, stringResource(Res.string.ui_previous_track),
            enabled = controllerState.isPreviousAvailable, onClick = { onUIEvent(UIEvent.Previous) })
        IconButton(
            onClick = { onUIEvent(UIEvent.PlayPause) },
            modifier = Modifier.size(if (isSmallSize) 48.dp else 64.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (isSmallSize) Color.Transparent else GratifyColors.Accent,
                contentColor = if (isSmallSize) GratifyColors.TextPrimary else GratifyColors.OnAccent,
            ),
        ) {
            Icon(if (controllerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                stringResource(if (controllerState.isPlaying) Res.string.ui_pause else Res.string.ui_play),
                modifier = Modifier.size(if (isSmallSize) 30.dp else 36.dp))
        }
        PlaybackControlIcon(Icons.Rounded.SkipNext, stringResource(Res.string.ui_next_track),
            enabled = controllerState.isNextAvailable, onClick = { onUIEvent(UIEvent.Next) })
        PlaybackControlIcon(
            if (controllerState.repeatState == RepeatState.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            stringResource(when (controllerState.repeatState) {
                RepeatState.None -> Res.string.repeat_off
                RepeatState.All -> Res.string.repeat_all
                RepeatState.One -> Res.string.repeat_one
            }),
            tint = if (controllerState.repeatState == RepeatState.None) GratifyColors.TextSecondary else GratifyColors.Accent,
            onClick = { onUIEvent(UIEvent.Repeat) },
        )
    }
}

@Composable
private fun PlaybackControlIcon(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    tint: Color = GratifyColors.TextPrimary,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.iconButtonColors(contentColor = tint,
            disabledContentColor = GratifyColors.TextSecondary.copy(alpha = 0.4f))) {
        Icon(icon, description, modifier = Modifier.size(24.dp))
    }
}
