// Ported from SimpMusic 62e0073ba818d899b22c75909deb9b034516d5db (GPL-3.0).
package com.tan.gratify.ui.screen.player.simp.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tan.gratify.ui.screen.player.simp.icon.Explicit
import com.tan.gratify.ui.screen.player.simp.icon.SimpIcons
import com.tan.gratify.ui.screen.player.simp.theme.typo
import org.jetbrains.compose.resources.stringResource
import gratify.composeapp.generated.resources.Res

@Composable
fun ExplicitBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = SimpIcons.Explicit,
            "Explicit",
            tint = Color.LightGray,
        )
    }
}

