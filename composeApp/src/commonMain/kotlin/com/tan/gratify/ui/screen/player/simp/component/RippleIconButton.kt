// Ported from SimpMusic 62e0073ba818d899b22c75909deb9b034516d5db (GPL-3.0).
package com.tan.gratify.ui.screen.player.simp.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tan.gratify.ui.screen.player.simp.icon.Pause
import com.tan.gratify.ui.screen.player.simp.icon.PlayArrow
import com.tan.gratify.ui.screen.player.simp.icon.SimpIcons

@Composable
fun RippleIconButton(
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    fillMaxSize: Boolean = false,
    tint: Color = Color.White,
    contentDescription: String? = null,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            imageVector,
            contentDescription,
            tint = tint,
            modifier = if (fillMaxSize) Modifier.fillMaxSize().padding(4.dp) else Modifier,
        )
    }
}

@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    RippleIconButton(
        if (!isPlaying) {
            SimpIcons.PlayArrow
        } else {
            SimpIcons.Pause
        },
        modifier = modifier,
        tint = tint,
        onClick = onClick,
    )
}
