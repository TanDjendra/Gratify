package com.tan.gratify.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tan.gratify.ui.theme.GratifyColors
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.holder
import org.jetbrains.compose.resources.painterResource

/** Used by the live home feed and its sample-data preview. */
@Composable
fun HomeSectionHeading(
    title: String,
    subtitle: String? = null,
    artworkUrl: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = if (onClick == null) modifier else modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(artworkUrl, contentDescription = null,
                placeholder = painterResource(Res.drawable.holder),
                error = painterResource(Res.drawable.holder),
                fallback = painterResource(Res.drawable.holder),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(36.dp).clip(MaterialTheme.shapes.extraLarge))
        }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = GratifyColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(title, style = MaterialTheme.typography.headlineMedium,
                color = GratifyColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
