package com.tan.gratify.ui.screen.library

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import com.tan.gratify.ui.theme.GratifyColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun OwnedPlaylistAddSongsAction(readOnly: Boolean, unavailable: Boolean, onAdd: () -> Unit) {
    if (!readOnly && !unavailable) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
        Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = GratifyColors.TextPrimary, contentColor = GratifyColors.Background),
            modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth().heightIn(min = 56.dp)) {
            Icon(Icons.Rounded.Add, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.playlist_flow_add_title))
        }
        }
    }
}
