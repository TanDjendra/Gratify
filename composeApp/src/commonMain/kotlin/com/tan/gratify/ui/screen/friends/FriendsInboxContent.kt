package com.tan.gratify.ui.screen.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tan.domain.data.model.social.*
import com.tan.gratify.ui.component.ScreenHeader
import com.tan.gratify.ui.component.UserAvatar
import com.tan.gratify.ui.theme.*
import com.tan.gratify.viewModel.*
import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

// PUBLIC is retained only to safely restore old saved navigation; it never opens a story feed.
enum class FriendsInboxTab { ACTIVITY, MAIN, REQUESTS, PUBLIC }
internal fun notesInboxTab(name: String): FriendsInboxTab =
    FriendsInboxTab.entries.firstOrNull { it.name == name && it != FriendsInboxTab.PUBLIC } ?: FriendsInboxTab.ACTIVITY

@Composable
fun FriendsInboxHeader(name: String, onDrawer: () -> Unit, onNewMessage: () -> Unit, onFind: () -> Unit, profileImage: String? = null) {
    ScreenHeader(stringResource(Res.string.friends), name, profileImage, onDrawer, actions = {
        IconButton(onClick = onFind) { Icon(Icons.Rounded.PersonAddAlt, stringResource(Res.string.friends_find)) }
        IconButton(onClick = onNewMessage) { Icon(Icons.AutoMirrored.Rounded.Chat, stringResource(Res.string.friends_new_message)) }
    })
}

internal fun MusicStory.isActive(now: Long = Clock.System.now().toEpochMilliseconds()) =
    runCatching { Instant.parse(expiresAt).toEpochMilliseconds() > now }.getOrDefault(false)

/** Music is the entry point; conversations and discovery keep their existing destinations. */
@Composable
fun FriendsInboxContent(activity: FriendsActivityState, social: FriendsSocialState,
    profileName: String, profileImage: String?, tab: FriendsInboxTab, onTab: (FriendsInboxTab) -> Unit,
    onStory: (List<MusicStory>) -> Unit, onCreateStory: () -> Unit, onEditNote: () -> Unit,
    onChat: (String) -> Unit, onProfile: (String) -> Unit, onPlay: (String) -> Unit,
    onFindFriends: () -> Unit, onRetry: () -> Unit, onMoreStories: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp), listState: LazyListState = rememberLazyListState(),
    onLogin: () -> Unit = onFindFriends,
) {
    var now by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(30_000); now = Clock.System.now().toEpochMilliseconds() } }
    val friends = activity.friendsList.map { item ->
        if (item.userProfile.nowPlayingTitle != null) friendActivityAt(item.userProfile, now).copy(artworkUrl = item.artworkUrl) else item
    }
    val visibleTab = notesInboxTab(tab.name)
    val requests = social.conversations.filter { !it.accepted && it.requestFrom != social.userId && it.lastBody != null }
    val conversations = social.conversations.filter { it.accepted || it.requestFrom == social.userId }
    val contacts = friends.filter { f -> conversations.none { it.peerId == f.userProfile.id } }
    val signedOut = social.userId == null && activity.currentUserId == null && !social.loading && !activity.isLoading
    val messageTab = visibleTab == FriendsInboxTab.MAIN || visibleTab == FriendsInboxTab.REQUESTS
    val unread = conversations.sumOf { it.unreadCount.coerceAtLeast(0) }
    val listening = friends.filter(FriendActivityItem::isOnline)
    val quiet = friends.filterNot(FriendActivityItem::isOnline)
    Box(Modifier.fillMaxSize().background(GratifyColors.Background), contentAlignment = Alignment.TopCenter) {
        LazyColumn(state = listState, modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
            contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item("tabs") {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(FriendsInboxTab.ACTIVITY, FriendsInboxTab.MAIN), key = { it.name }) { destination ->
                        val label = stringResource(when (destination) {
                            FriendsInboxTab.ACTIVITY -> Res.string.friends_tab_activity
                            FriendsInboxTab.MAIN -> Res.string.friends_tab_messages
                            else -> Res.string.friends_tab_explore
                        })
                        FilterChip(selected = visibleTab == destination || destination == FriendsInboxTab.MAIN && messageTab,
                            onClick = { onTab(destination) }, modifier = Modifier.heightIn(min = 48.dp),
                            label = { Text(if (destination == FriendsInboxTab.MAIN && unread > 0) "$label · ${unread.coerceAtMost(99)}" else label) },
                            colors = FilterChipDefaults.filterChipColors(containerColor = GratifyColors.SurfaceRaised,
                                selectedContainerColor = GratifyColors.Accent, selectedLabelColor = GratifyColors.OnAccent),
                            border = null, shape = CircleShape)
                    }
                }
            }
            if (signedOut) {
                item("sign-in") { InboxNotice(stringResource(Res.string.friends_sign_in_title),
                    stringResource(Res.string.friends_sign_in_hint), stringResource(Res.string.friends_sign_in), onLogin) }
            } else {
                if (visibleTab == FriendsInboxTab.ACTIVITY) {
                    item("my-note") {
                        FriendsNoteCard(profileName, profileImage, activity.myNote, true, onEditNote)
                    }
                    val notes = friends.filter { !it.userProfile.note.isNullOrBlank() }
                    if (notes.isNotEmpty()) item("notes-heading") {
                        Text(stringResource(Res.string.friends_notes_heading), Modifier.padding(horizontal = 16.dp).semantics { heading() },
                            style = typo().titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    items(notes, key = { "note-${it.userProfile.id}" }) { friend ->
                        FriendsNoteCard(friend.userProfile.displayName ?: stringResource(Res.string.friends_user),
                            friend.userProfile.avatarUrl, friend.userProfile.note, false) { onProfile(friend.userProfile.id) }
                    }
                    item("activity-heading") {
                        FriendsSectionHeading(stringResource(Res.string.friends_listening), onRetry, activity.isLoading)
                    }
                    if (activity.error != null) item("activity-error") {
                        InboxNotice(stringResource(Res.string.friends_activity_failed), stringResource(Res.string.friends_retry_hint),
                            stringResource(Res.string.retry), onRetry)
                    }
                    if (activity.isLoading && friends.isEmpty()) item("activity-loading") { FriendsInboxLoading() }
                    if (listening.isEmpty() && friends.isNotEmpty() && !activity.isLoading && activity.error == null) item("quiet") {
                        Text(stringResource(Res.string.friends_no_listening), Modifier.padding(horizontal = 16.dp),
                            style = typo().bodyMedium, color = GratifyColors.TextSecondary)
                    }
                    items(listening, key = { "live-${it.userProfile.id}" }) { friend ->
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            FriendListItem(friend, { onProfile(friend.userProfile.id) }, { onPlay(friend.videoId) },
                                onMessage = { onChat(friend.userProfile.id) }, showNote = false)
                        }
                    }
                    if (quiet.isNotEmpty()) item("friends-heading") {
                        Text(stringResource(Res.string.friends_your_people), Modifier.padding(horizontal = 16.dp, vertical = 4.dp).semantics { heading() },
                            style = typo().titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    items(quiet, key = { "friend-${it.userProfile.id}" }) { friend ->
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            FriendListItem(friend.copy(videoId = ""), { onProfile(friend.userProfile.id) }, {},
                                onMessage = { onChat(friend.userProfile.id) }, showNote = false)
                        }
                    }
                    if (friends.isEmpty() && !activity.isLoading && activity.error == null) item("empty-friends") {
                        InboxNotice(stringResource(Res.string.friends_empty_title), stringResource(Res.string.friends_empty_hint),
                            stringResource(Res.string.friends_find), onFindFriends)
                    }
                    if (social.error != null) item("social-error") {
                        InboxNotice(stringResource(Res.string.friends_social_failed), stringResource(Res.string.friends_retry_hint),
                            stringResource(Res.string.retry), onRetry)
                    }
                } else {
                    if (messageTab) item("message-tabs") {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { FilterChip(selected = visibleTab == FriendsInboxTab.MAIN, onClick = { onTab(FriendsInboxTab.MAIN) },
                                modifier = Modifier.heightIn(min = 48.dp), label = { Text(stringResource(Res.string.friends_inbox)) })
                            }
                            item { FilterChip(selected = visibleTab == FriendsInboxTab.REQUESTS, onClick = { onTab(FriendsInboxTab.REQUESTS) },
                                modifier = Modifier.heightIn(min = 48.dp), label = { Text(stringResource(Res.string.friends_requests) +
                                    if (requests.isNotEmpty()) " · ${requests.size}" else "") })
                            }
                        }
                    }
                    item("page-heading") { FriendsSectionHeading(stringResource(Res.string.friends_tab_messages), onRetry, social.loading) }
                    if (social.error != null || activity.error != null && messageTab) item("error") {
                        InboxNotice(stringResource(Res.string.friends_social_failed), stringResource(Res.string.friends_retry_hint),
                            stringResource(Res.string.retry), onRetry)
                    }
                    if (social.loading && social.conversations.isEmpty()) item("loading") { FriendsInboxLoading() }
                    when (visibleTab) {
                        FriendsInboxTab.MAIN -> {
                            items(conversations, key = { "chat-${it.id}" }) { c ->
                                InboxRow(c.peerName ?: stringResource(Res.string.friends_user), c.peerAvatar,
                                    c.lastBody?.let { if (c.lastSenderId == social.userId) stringResource(Res.string.friends_you_said, it) else it }
                                        ?: stringResource(Res.string.friends_start_chat), unread = c.unreadCount,
                                    status = if (!c.accepted) stringResource(Res.string.friends_waiting)
                                        else if (c.lastSenderId == social.userId && c.peerReadAt?.let { read -> c.lastAt?.let { last -> read >= last } } == true)
                                            stringResource(Res.string.friends_seen) else null,
                                    onClick = { onChat(c.peerId) }, onAvatar = { onProfile(c.peerId) })
                            }
                            if (contacts.isNotEmpty()) item("contact-heading") { Text(stringResource(Res.string.friends_your_people),
                                Modifier.padding(horizontal = 16.dp).semantics { heading() }, style = typo().titleMedium) }
                            items(contacts, key = { "contact-${it.userProfile.id}" }) { f ->
                                InboxRow(f.userProfile.displayName ?: stringResource(Res.string.friends_user), f.userProfile.avatarUrl,
                                    if (f.isOnline) stringResource(Res.string.friends_listening_to, f.songTitle) else stringResource(Res.string.friends_start_chat),
                                    onClick = { onChat(f.userProfile.id) }, onAvatar = { onProfile(f.userProfile.id) })
                            }
                            if (conversations.isEmpty() && contacts.isEmpty() && !social.loading && social.error == null && activity.error == null) item("empty") {
                                InboxNotice(stringResource(Res.string.friends_messages_empty), stringResource(Res.string.friends_messages_hint),
                                    stringResource(Res.string.friends_find), onFindFriends)
                            }
                        }
                        FriendsInboxTab.REQUESTS -> {
                            items(requests, key = { "request-${it.id}" }) { c -> InboxRow(c.peerName ?: stringResource(Res.string.friends_user),
                                c.peerAvatar, c.lastBody.orEmpty(), unread = c.unreadCount, onClick = { onChat(c.peerId) }, onAvatar = { onProfile(c.peerId) }) }
                            if (requests.isEmpty() && !social.loading && social.error == null) item("no-requests") {
                                InboxNotice(stringResource(Res.string.friends_requests_empty), stringResource(Res.string.friends_requests_hint))
                            }
                        }
                        FriendsInboxTab.PUBLIC -> Unit
                        FriendsInboxTab.ACTIVITY -> Unit
                    }
                }
            }
            item("footer-space") { Spacer(Modifier.height(12.dp)) }
        }
    }
}

/** Text notes have no playback, story rings, viewer or expiration. */
@Composable
fun FriendsNoteCard(name: String, image: String?, note: String?, own: Boolean, onOpen: () -> Unit) {
    val title = if (own) stringResource(Res.string.friends_note_title) else name
    Surface(onClick = onOpen, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
        .semantics { contentDescription = if (own) "Edit catatan kamu" else "Catatan $name" },
        color = GratifyColors.SurfaceRaised, contentColor = GratifyColors.TextPrimary, shape = GratifyShapes.medium) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                UserAvatar(image, name, Modifier.size(32.dp), borderWidth = 0.dp)
                Text(title, Modifier.weight(1f), style = typo().titleSmall, fontWeight = FontWeight.SemiBold)
                if (own) Icon(Icons.Rounded.EditNote, null, Modifier.size(24.dp), tint = GratifyColors.Accent)
            }
            Text(note?.takeIf(String::isNotBlank) ?: stringResource(Res.string.friends_add_note), style = typo().bodyLarge,
                color = if (note.isNullOrBlank()) GratifyColors.TextSecondary else GratifyColors.TextPrimary)
            if (own) Text(stringResource(Res.string.friends_notes_persistent), style = typo().bodySmall, color = GratifyColors.TextSecondary)
        }
    }
}

@Composable
private fun FriendsSectionHeading(title: String, onRefresh: () -> Unit, loading: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).semantics { heading() }, style = typo().titleLarge, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onRefresh, enabled = !loading) { Icon(Icons.Rounded.Refresh,
            stringResource(Res.string.friends_refresh), Modifier.size(20.dp), tint = GratifyColors.TextSecondary) }
    }
}

@Composable
private fun FriendsInboxLoading() {
    val label = stringResource(Res.string.friends_loading)
    Column(Modifier.padding(16.dp).semantics { contentDescription = label }, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        repeat(3) { Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(GratifyColors.SurfaceRaised))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth(.5f).height(14.dp).background(GratifyColors.SurfaceRaised, GratifyShapes.small))
                Box(Modifier.fillMaxWidth(.8f).height(12.dp).background(GratifyColors.SurfaceRaised, GratifyShapes.small))
            }
        } }
    }
}

@Composable
private fun InboxRow(name: String, avatar: String?, preview: String, unread: Int = 0,
    status: String? = null, onClick: () -> Unit, onAvatar: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
        .semantics { contentDescription = "Pesan $name" }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(62.dp).clickable(onClick = onAvatar).semantics { contentDescription = "Lihat profil $name" }) {
            UserAvatar(avatar, name, Modifier.fillMaxSize(), borderWidth = 0.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = typo().titleMedium.copy(fontWeight = if(unread > 0) FontWeight.Bold else FontWeight.Medium), color = GratifyColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(preview, style = typo().bodyMedium, color = if(unread > 0) GratifyColors.TextPrimary else GratifyColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!status.isNullOrBlank()) Text(status, style = typo().labelSmall, color = GratifyColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (unread > 0) Box(Modifier.size(8.dp).background(GratifyColors.Accent, CircleShape))
    }
}

@Composable
internal fun InboxNotice(title: String, text: String, action: String? = null, onClick: () -> Unit = {}) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = typo().titleLarge, color = GratifyColors.TextPrimary)
        Text(text, style = typo().bodyMedium, color = GratifyColors.TextSecondary)
        if (action != null) TextButton(onClick = onClick) { Text(action, color = GratifyColors.Accent) }
    }
}
