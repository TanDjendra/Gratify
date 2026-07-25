package com.tan.gratify.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.tan.gratify.ui.theme.typo
import org.jetbrains.compose.resources.painterResource
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.baseline_history_24
import gratify.composeapp.generated.resources.baseline_settings_24
import gratify.composeapp.generated.resources.outline_notifications_24

@Composable
fun ProfileDrawerContent(
    profileImageUrl: String?,
    profileName: String,
    onViewProfileClick: () -> Unit,
    onRecentlyPlayedClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onCloseClick: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.fillMaxHeight().width(300.dp),
        drawerContainerColor = Color(0xFF121212)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // ── Profile Header ──────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onViewProfileClick() }
                    .padding(bottom = 4.dp)
            ) {
                UserAvatar(
                    imageUrl = profileImageUrl,
                    name = profileName,
                    modifier = Modifier.size(52.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = profileName,
                        style = typo().titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = "Lihat profil",
                        style = typo().bodySmall,
                        color = Color(0xFFB3B3B3),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(16.dp))

            // ── Menu Items ──────────────────────────────────────────────
            DrawerMenuItem(
                iconRes = Res.drawable.baseline_history_24,
                label = "Baru Diputar",
                onClick = onRecentlyPlayedClick
            )
            DrawerMenuItem(
                iconRes = Res.drawable.outline_notifications_24,
                label = "Info Terkini",
                onClick = onNotificationClick
            )
            DrawerMenuItem(
                iconRes = Res.drawable.baseline_settings_24,
                label = "Pengaturan dan privasi",
                onClick = onSettingsClick
            )
        }
    }
}

@Composable
private fun DrawerMenuItem(
    iconRes: org.jetbrains.compose.resources.DrawableResource,
    label: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = typo().bodyLarge,
            color = Color.White
        )
    }
}
