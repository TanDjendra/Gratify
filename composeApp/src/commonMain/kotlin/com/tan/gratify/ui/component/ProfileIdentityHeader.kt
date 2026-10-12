package com.tan.gratify.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import com.tan.gratify.ui.theme.GratifyColors
import com.tan.gratify.ui.theme.typo
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.baseline_arrow_back_ios_new_24
import org.jetbrains.compose.resources.painterResource

/** Content determines height so large text never collides with the back button. */
@Composable
fun ProfileIdentityHeader(
    name: String, imageUrl: String?, followersLabel: String, followingLabel: String,
    countsClickable: Boolean, onBack: () -> Unit, onFollowers: () -> Unit, onFollowing: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)) {
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
            GratifyColors.AccentContainer.copy(alpha = 0.6f), GratifyColors.Divider.copy(alpha = 0.2f), GratifyColors.Background))))
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 4.dp)) {
            Icon(painterResource(Res.drawable.baseline_arrow_back_ios_new_24), contentDescription = "Kembali", tint = Color.White)
        }
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 64.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(imageUrl, name, Modifier.size(88.dp), borderWidth = 1.5.dp, borderColor = GratifyColors.Outline,
                    textStyle = typo().headlineLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 36.sp))
                Spacer(Modifier.width(16.dp))
                Text(name, style = typo().headlineSmall.copy(fontWeight = FontWeight.ExtraBold), color = Color.White,
                    modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(followersLabel, style = typo().bodySmall, color = GratifyColors.TextSecondary,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).clickable(enabled = countsClickable, role = Role.Button,
                        onClick = onFollowers).padding(vertical = 12.dp))
                Text(followingLabel, style = typo().bodySmall, color = GratifyColors.TextSecondary,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).clickable(enabled = countsClickable, role = Role.Button,
                        onClick = onFollowing).padding(vertical = 12.dp))
            }
        }
    }
}
