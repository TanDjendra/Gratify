package com.tan.gratify.ui.screen.library

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tan.domain.utils.PlaylistFeaturePolicy

/** Handles retained links/back-stack entries without accepting archived invitations. */
@Composable fun ArchivedPlaylistFeatureScreen(onBack:()->Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center,
        horizontalAlignment=Alignment.CenterHorizontally) {
        Text(PlaylistFeaturePolicy.archivedMessage)
        Spacer(Modifier.height(16.dp))
        Button(onClick=onBack){Text("Kembali")}
    }
}
