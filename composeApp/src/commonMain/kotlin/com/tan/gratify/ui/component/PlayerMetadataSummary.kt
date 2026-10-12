package com.tan.gratify.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tan.gratify.ui.theme.*
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.holder
import org.jetbrains.compose.resources.painterResource

@Composable
fun PlayerMetadataSummary(title: String, artist: String, artwork: String?, details: List<Pair<String, String>>) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(artwork, null, placeholder = painterResource(Res.drawable.holder),
                error = painterResource(Res.drawable.holder), contentScale = ContentScale.Crop,
                modifier = Modifier.size(80.dp).clip(GratifyShapes.medium))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = typo().titleLarge, color = GratifyColors.TextPrimary,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(artist, style = typo().bodyMedium, color = GratifyColors.TextSecondary,
                    modifier = Modifier.padding(top = 4.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(Modifier.clip(GratifyShapes.large).background(GratifyColors.SurfaceRaised).padding(16.dp)) {
            details.forEachIndexed { index, (label, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(label, style = typo().bodySmall, color = GratifyColors.TextSecondary, modifier = Modifier.weight(0.4f))
                    Text(value, style = typo().bodyMedium, color = GratifyColors.TextPrimary, modifier = Modifier.weight(0.6f))
                }
                if (index != details.lastIndex) HorizontalDivider(color = GratifyColors.Divider)
            }
        }
    }
}
