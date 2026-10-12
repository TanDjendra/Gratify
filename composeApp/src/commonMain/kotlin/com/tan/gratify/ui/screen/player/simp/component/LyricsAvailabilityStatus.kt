package com.tan.gratify.ui.screen.player.simp.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LyricsAvailabilityStatus(loading: Boolean, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().testTag(if (loading) "lyrics-loading" else "lyrics-unavailable"), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        if (loading) CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 2.dp)
        Text(stringResource(if (loading) Res.string.lyrics_finding else Res.string.lyrics_missing),
            modifier = Modifier.padding(16.dp), color = Color.White)
        if (!loading) TextButton(onClick = onRetry) { Text(stringResource(Res.string.lyrics_retry), color = Color.White) }
    }
}
