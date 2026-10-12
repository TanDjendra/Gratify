package com.tan.gratify.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tan.gratify.ui.theme.GratifyColors
import com.tan.gratify.ui.theme.typo
import com.tan.gratify.viewModel.InAppNotificationKind
import com.tan.gratify.viewModel.SnackbarEvent
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.notification
import gratify.composeapp.generated.resources.popup_dismiss
import org.jetbrains.compose.resources.stringResource

/** Keeps Material's queue, timeout and accessibility handling with Gratify's card UI. */
data class InAppNotificationVisuals(val event: SnackbarEvent) : SnackbarVisuals {
    override val message = event.message
    override val actionLabel = event.actionLabel
    override val withDismissAction = true
    override val duration = SnackbarDuration.Long
}

@Composable
fun InAppNotificationHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        val event = (data.visuals as? InAppNotificationVisuals)?.event
            ?: SnackbarEvent(message = data.visuals.message, actionLabel = data.visuals.actionLabel)
        InAppNotificationCard(event, onDismiss = data::dismiss, onAction = data::performAction)
    }
}

@Composable
fun InAppNotificationCard(
    event: SnackbarEvent,
    onDismiss: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (event.kind == InAppNotificationKind.ERROR) GratifyColors.Error else GratifyColors.Accent
    val notificationLabel = stringResource(Res.string.notification)
    val title = event.title ?: notificationLabel
    Surface(
        modifier = modifier.widthIn(max = 440.dp).fillMaxWidth().semantics { paneTitle = notificationLabel },
        shape = RoundedCornerShape(24.dp),
        color = GratifyColors.SurfaceRaised,
        contentColor = GratifyColors.TextPrimary,
        border = BorderStroke(1.dp, GratifyColors.TextPrimary.copy(alpha = 0.10f)),
        shadowElevation = 16.dp,
    ) {
        Box {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 12.dp)) {
                Row(
                    modifier = Modifier.padding(end = 36.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(Modifier.size(5.dp).background(accent, CircleShape))
                    Text("GRATIFY", style = typo().labelSmall.copy(letterSpacing = 1.6.sp), color = GratifyColors.TextSecondary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(44.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                        if (event.kind == InAppNotificationKind.FOLLOWER) {
                            UserAvatar(event.avatarUrl, event.avatarName, Modifier.fillMaxSize(), borderColor = accent.copy(alpha = 0.3f))
                        } else {
                            Box(Modifier.fillMaxSize().background(accent.copy(alpha = 0.12f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = when (event.kind) {
                                        InAppNotificationKind.SUCCESS -> Icons.Rounded.Check
                                        InAppNotificationKind.ERROR -> Icons.Rounded.ErrorOutline
                                        InAppNotificationKind.MUSIC -> Icons.Rounded.MusicNote
                                        else -> Icons.Rounded.NotificationsNone
                                    },
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(23.dp),
                                )
                            }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = typo().titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            event.message,
                            modifier = Modifier.padding(top = 4.dp),
                            style = typo().bodyMedium.copy(fontSize = 13.sp, lineHeight = 20.sp),
                            color = GratifyColors.TextSecondary,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!event.actionLabel.isNullOrBlank()) {
                            TextButton(
                                onClick = onAction,
                                modifier = Modifier.padding(top = 4.dp).heightIn(min = 48.dp),
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = accent),
                            ) {
                                Text(event.actionLabel, modifier = Modifier.weight(1f, fill = false), style = typo().labelMedium.copy(fontWeight = FontWeight.Bold))
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        } else {
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(48.dp)) {
                Icon(Icons.Rounded.Close, stringResource(Res.string.popup_dismiss), tint = GratifyColors.TextSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}
