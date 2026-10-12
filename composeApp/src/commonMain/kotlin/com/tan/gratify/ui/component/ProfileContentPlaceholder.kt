package com.tan.gratify.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.tan.gratify.ui.theme.*

/** Privacy changes the contents while the profile's structure remains recognizable. */
@Composable
fun ProfileContentPlaceholder(title: String, message: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        if (title.isNotBlank()) {
            Text(title, style = typo().titleMedium, color = GratifyColors.TextPrimary)
            Spacer(Modifier.height(12.dp))
        }
        Text(message, style = typo().bodySmall, color = GratifyColors.TextSecondary,
            modifier = Modifier.fillMaxWidth().clip(GratifyShapes.medium).background(GratifyColors.Surface)
                .padding(16.dp))
    }
}
