package com.tan.gratify.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tan.domain.data.entities.NotificationEntity
import com.tan.domain.utils.notificationSocialLink
import com.tan.domain.utils.notificationWebLink
import com.tan.domain.utils.parseProfileUserId
import com.tan.gratify.extension.formatTimeAgo
import com.tan.gratify.ui.component.UserAvatar
import com.tan.gratify.ui.theme.*
import com.tan.gratify.viewModel.NotificationSyncProblem
import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

enum class NotificationCategory { ALL, FRIENDS, MUSIC, GRATIFY }
fun notificationCategory(row: NotificationEntity) = when (row.channelId) {
    "developer" -> NotificationCategory.GRATIFY
    "follower_activity", "social_message", "social_story" -> NotificationCategory.FRIENDS
    else -> NotificationCategory.MUSIC
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationInboxContent(
    rows: List<NotificationEntity>?, following: Set<String> = emptySet(), followingBusy: Set<String> = emptySet(),
    followingReady: Boolean = true, deleting: Set<Long> = emptySet(), refreshing: Boolean = false,
    readFailed: Boolean = false, syncProblems: Set<NotificationSyncProblem> = emptySet(), error: String? = null,
    notificationsEnabled: Boolean? = null,
    onBack: () -> Unit, onRefresh: () -> Unit, onSettings: () -> Unit = {}, onDismissError: () -> Unit = {},
    onDelete: (Long) -> Unit, onFollowBack: (String) -> Unit = {},
    onOpenProfile: (NotificationEntity, String) -> Unit = { _, _ -> }, onOpenSocial: (NotificationEntity) -> Unit = {},
    onOpenArtist: (String) -> Unit = {}, onOpenAlbum: (String) -> Unit = {}, onOpenLink: (String) -> Unit = {},
) {
    var category by remember { mutableStateOf(NotificationCategory.ALL) }
    val visible = rows.orEmpty().filter { category == NotificationCategory.ALL || notificationCategory(it) == category }
    Column(Modifier.fillMaxSize().background(GratifyColors.Background)) {
        TopAppBar(title = { Text(stringResource(Res.string.notification), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = typo().titleLarge.copy(fontWeight = FontWeight.Bold)) },
            navigationIcon = { IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.inbox_back))
            } }, actions = {
                IconButton(onClick = onRefresh, enabled = !refreshing, modifier = Modifier.size(48.dp)) {
                    if (refreshing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Refresh, stringResource(Res.string.inbox_refresh))
                }
                if (notificationsEnabled != null) IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Settings, stringResource(Res.string.inbox_settings))
                }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = GratifyColors.Background))
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(stringResource(Res.string.inbox_intro), style = typo().bodyMedium, color = GratifyColors.TextSecondary)
                }
                if (notificationsEnabled == false) item {
                    InboxNotice(stringResource(Res.string.inbox_alerts_off), stringResource(Res.string.inbox_alerts_off_hint),
                        stringResource(Res.string.inbox_settings), onSettings)
                }
                if (readFailed) item {
                    InboxNotice(stringResource(Res.string.inbox_read_failed), stringResource(Res.string.inbox_cached_hint),
                        stringResource(Res.string.inbox_retry), onRefresh, !refreshing)
                }
                if (syncProblems.isNotEmpty()) item {
                    Column(Modifier.fillMaxWidth().clip(GratifyShapes.medium).background(GratifyColors.Surface).padding(16.dp)) {
                        Text(stringResource(Res.string.inbox_sync_failed), style = typo().titleSmall)
                        syncProblems.sortedBy { it.ordinal }.forEach { problem ->
                            Text(stringResource(when (problem) {
                                NotificationSyncProblem.ANNOUNCEMENTS -> Res.string.inbox_announcements_failed
                                NotificationSyncProblem.SOCIAL -> Res.string.inbox_social_failed
                                NotificationSyncProblem.FOLLOWERS -> Res.string.inbox_followers_failed
                                NotificationSyncProblem.FOLLOWING -> Res.string.inbox_following_failed
                            }), style = typo().bodySmall, color = GratifyColors.TextSecondary,
                                modifier = Modifier.padding(top = 6.dp))
                        }
                        TextButton(onClick = onRefresh, enabled = !refreshing, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(Res.string.inbox_retry)) }
                    }
                }
                error?.let { message -> item {
                    InboxNotice(stringResource(Res.string.inbox_action_failed), message, stringResource(Res.string.inbox_dismiss), onDismissError)
                } }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(NotificationCategory.entries) { value ->
                            FilterChip(selected = category == value, onClick = { category = value },
                                modifier = Modifier.heightIn(min = 48.dp), label = { Text(stringResource(when (value) {
                                    NotificationCategory.ALL -> Res.string.inbox_all
                                    NotificationCategory.FRIENDS -> Res.string.inbox_friends
                                    NotificationCategory.MUSIC -> Res.string.inbox_music
                                    NotificationCategory.GRATIFY -> Res.string.inbox_gratify
                                })) })
                        }
                    }
                }
                if (rows == null) item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp)); Text(stringResource(Res.string.inbox_loading))
                    }
                } else if (visible.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.NotificationsNone, null, Modifier.size(40.dp), tint = GratifyColors.TextSecondary)
                        Text(stringResource(if (category == NotificationCategory.ALL) Res.string.inbox_empty else Res.string.inbox_category_empty),
                            style = typo().titleMedium, modifier = Modifier.padding(top = 16.dp))
                        Text(stringResource(Res.string.inbox_empty_hint), style = typo().bodyMedium,
                            color = GratifyColors.TextSecondary, modifier = Modifier.padding(top = 8.dp))
                        if (category != NotificationCategory.ALL) TextButton(onClick = { category = NotificationCategory.ALL }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(Res.string.inbox_show_all))
                        }
                    }
                }
                items(visible, key = { it.id }) { row ->
                    val pending = row.id in deleting
                    // The database owns removal. Explicit deletion avoids repeated callbacks during one drag.
                    NotificationInboxCard(row, row.single.firstOrNull()?.get("browseId") in following,
                        row.single.firstOrNull()?.get("browseId") in followingBusy, followingReady, pending,
                        { onDelete(row.id) }, onFollowBack, onOpenProfile, onOpenSocial, onOpenArtist, onOpenAlbum, onOpenLink)
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun InboxNotice(title: String, hint: String, action: String, onAction: () -> Unit, enabled: Boolean = true) {
    Column(Modifier.fillMaxWidth().clip(GratifyShapes.medium).background(GratifyColors.Surface).padding(16.dp)) {
        Text(title, style = typo().titleSmall)
        Text(hint, style = typo().bodySmall, color = GratifyColors.TextSecondary, modifier = Modifier.padding(top = 6.dp))
        TextButton(onClick = onAction, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
    }
}

@Composable
fun NotificationInboxCard(
    row: NotificationEntity, followed: Boolean = false, followBusy: Boolean = false, followingReady: Boolean = true,
    deleting: Boolean = false, onDelete: () -> Unit, onFollowBack: (String) -> Unit = {},
    onOpenProfile: (NotificationEntity, String) -> Unit = { _, _ -> }, onOpenSocial: (NotificationEntity) -> Unit = {},
    onOpenArtist: (String) -> Unit = {}, onOpenAlbum: (String) -> Unit = {}, onOpenLink: (String) -> Unit = {},
) {
    val details = row.single.firstOrNull().orEmpty()
    val category = notificationCategory(row)
    val profileId = parseProfileUserId(details["browseId"].orEmpty())
    val socialLink = notificationSocialLink(row)
    val webLink = notificationWebLink(details["browseId"])
    var linkFailed by remember(row.id, webLink) { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth(), shape = GratifyShapes.medium, color = GratifyColors.Surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(when {
                        row.channelId == "follower_activity" -> Res.string.inbox_follower
                        row.channelId == "social_story" -> Res.string.inbox_story
                        row.channelId == "social_message" -> Res.string.inbox_message
                        category == NotificationCategory.GRATIFY -> Res.string.inbox_announcement
                        else -> Res.string.new_release
                    }), style = typo().labelMedium, color = GratifyColors.Accent)
                    Text(row.time.formatTimeAgo(), style = typo().bodySmall, color = GratifyColors.TextSecondary)
                }
                IconButton(onClick = onDelete, enabled = !deleting, modifier = Modifier.size(48.dp)) {
                    if (deleting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.DeleteOutline, stringResource(Res.string.inbox_delete, row.name), tint = GratifyColors.TextSecondary)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .clickable(enabled = when (row.channelId) {
                    "follower_activity" -> profileId != null
                    "social_message", "social_story" -> socialLink != null
                    "developer" -> false
                    else -> row.channelId.isNotBlank()
                }, role = Role.Button, onClick = {
                    when (row.channelId) {
                        "follower_activity" -> profileId?.let { onOpenProfile(row, it) }
                        "social_message", "social_story" -> onOpenSocial(row)
                        else -> onOpenArtist(row.channelId)
                    }
                })) {
                if (category == NotificationCategory.FRIENDS) {
                    UserAvatar(row.thumbnail, row.name, Modifier.size(48.dp)); Spacer(Modifier.width(12.dp))
                }
                Text(row.name, style = typo().titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
            }
            when (row.channelId) {
                "developer" -> {
                    if (!details["title"].isNullOrBlank()) Text(details["title"].orEmpty(), style = typo().bodyMedium, color = GratifyColors.TextSecondary)
                    if (webLink != null) {
                        TextButton(onClick = { try { onOpenLink(webLink); linkFailed = false } catch (_: Exception) { linkFailed = true } },
                            modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(Res.string.inbox_open_link), modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp))
                        }
                        if (linkFailed) {
                            Text(stringResource(Res.string.inbox_link_failed), color = GratifyColors.Error, style = typo().bodySmall)
                            SelectionContainer { Text(webLink, style = typo().bodySmall, color = GratifyColors.TextSecondary) }
                        }
                    }
                }
                "follower_activity" -> {
                    Text(stringResource(Res.string.inbox_follower_body), style = typo().bodyMedium, color = GratifyColors.TextSecondary)
                    if (followed) Text(stringResource(Res.string.inbox_following), style = typo().labelLarge, color = GratifyColors.Accent)
                    else OutlinedButton(onClick = { profileId?.let(onFollowBack) }, enabled = !followBusy && followingReady && profileId != null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(if (followBusy) Res.string.inbox_follow_busy else if (!followingReady)
                            Res.string.inbox_follow_unavailable else Res.string.inbox_follow_back))
                    }
                }
                "social_message", "social_story" -> Text(details["title"].orEmpty(), style = typo().bodyMedium, color = GratifyColors.TextSecondary)
                else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(row.album + row.single) { release ->
                        Column(Modifier.width(128.dp).clickable(enabled = !release["browseId"].isNullOrBlank(), role = Role.Button,
                            onClick = { release["browseId"]?.let(onOpenAlbum) })) {
                            AsyncImage(release["thumbnails"], null, Modifier.size(128.dp).clip(GratifyShapes.small),
                                contentScale = ContentScale.Crop, placeholder = painterResource(Res.drawable.holder), error = painterResource(Res.drawable.holder))
                            Text(release["title"].orEmpty(), style = typo().bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
    }
}
