package com.tan.gratify.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tan.domain.data.model.home.Content
import com.tan.gratify.ui.theme.*
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.holder
import org.jetbrains.compose.resources.painterResource

@Composable
fun QuickDiscoveryCard(content: Content, modifier: Modifier = Modifier, onPlay: () -> Unit) {
    Row(modifier.clip(GratifyShapes.small).background(GratifyColors.SurfaceRaised)
        .clickable(onClick = onPlay).heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = content.thumbnails.lastOrNull()?.url, contentDescription = null,
            placeholder = painterResource(Res.drawable.holder), error = painterResource(Res.drawable.holder),
            contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp))
        Text(content.title, style = typo().labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = GratifyColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp))
    }
}
