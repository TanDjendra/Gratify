package com.tan.gratify.ui.screen.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PeopleOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tan.gratify.ui.component.UserAvatar
import com.tan.gratify.ui.theme.*
import com.tan.gratify.viewModel.FriendActivityItem
import com.tan.gratify.viewModel.FriendsActivityState
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.holder
import gratify.composeapp.generated.resources.friends_listening
import gratify.composeapp.generated.resources.friends_activity_quiet
import gratify.composeapp.generated.resources.friends_activity_private
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun FriendsActivityContent(
    state: FriendsActivityState,
    profileName: String,
    profileImage: String?,
    contentPadding: PaddingValues = PaddingValues(vertical = 20.dp),
    listState: LazyListState = rememberLazyListState(),
    onEditNote: () -> Unit,
    onProfile: (String) -> Unit,
    onPlay: (String) -> Unit,
    onFindFriends: () -> Unit,
    onRetry: () -> Unit,
) {
    val listening = state.friendsList.filter { it.isOnline }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            modifier = Modifier.widthIn(max = 760.dp).fillMaxHeight().fillMaxWidth(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "intro") {
                Column(Modifier.padding(horizontal = GratifySpacing.Page), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Dengarkan bersama", style = typo().headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = GratifyColors.TextPrimary)
                    if (state.friendsList.isNotEmpty()) {
                        Text("${listening.size} sedang mendengarkan",
                            style = typo().bodyMedium, color = GratifyColors.TextSecondary)
                    } else {
                        Text("Musik dan kabar dari temanmu.", style = typo().bodyMedium,
                            color = GratifyColors.TextSecondary)
                    }
                }
            }
            item(key = "my-note") {
                FriendsNoteCard(profileName, profileImage,
                    state.myNote.takeIf { isNoteActive(it, state.myNoteUpdatedAt) }, onEditNote)
            }
            if (listening.isNotEmpty()) {
                item(key = "listening") { FriendsListeningRow(listening, onProfile) }
            }
            if (state.error != null) {
                item(key = "error") {
                    FriendsMessage("Aktivitas belum dapat dimuat", "Periksa koneksi lalu coba kembali.", "Coba lagi", onRetry)
                }
            }
            when {
                state.isLoading && state.friendsList.isEmpty() -> item(key = "loading") { FriendsLoadingState() }
                state.friendsList.isEmpty() && state.error == null -> item(key = "empty") {
                    FriendsMessage("Mulai dari satu teman", "Cari teman dan saling mengikuti untuk berbagi musik di sini.",
                        "Temukan teman", onFindFriends, empty = true)
                }
                state.friendsList.isNotEmpty() -> {
                    item(key = "activity-heading") {
                        Row(Modifier.fillMaxWidth().padding(horizontal = GratifySpacing.Page),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("Aktivitas teman", style = typo().titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = GratifyColors.TextPrimary, modifier = Modifier.weight(1f))
                            Text("${state.friendsList.size}", style = typo().labelLarge, color = GratifyColors.TextSecondary)
                        }
                    }
                    items(state.friendsList, key = { it.userProfile.id }, contentType = { "friend" }) { friend ->
                        Column(Modifier.padding(horizontal = GratifySpacing.Page), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            FriendListItem(friend, { onProfile(friend.userProfile.id) }, { onPlay(friend.videoId) })
                            HorizontalDivider(color = GratifyColors.Divider.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendsNoteCard(profileName: String, profileImage: String?, note: String?, onEdit: () -> Unit) {
    Row(Modifier.padding(horizontal = GratifySpacing.Page).fillMaxWidth().clip(GratifyShapes.medium)
        .background(GratifyColors.Surface).clickable(role = Role.Button, onClick = onEdit)
        .semantics { contentDescription = "Edit catatan kamu" }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        UserAvatar(profileImage, profileName, Modifier.size(40.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Catatan kamu", style = typo().labelSmall, color = GratifyColors.TextSecondary)
            Text(note ?: "Musik apa yang menemanimu?", style = typo().bodyMedium,
                color = if (note == null) GratifyColors.TextSecondary else GratifyColors.TextPrimary,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.Edit, contentDescription = null, tint = GratifyColors.TextSecondary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun FriendsListeningRow(friends: List<FriendActivityItem>, onProfile: (String) -> Unit) {
    val avatarColumnWidth = 96.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Sedang mendengarkan", style = typo().labelLarge, color = GratifyColors.TextSecondary,
            modifier = Modifier.padding(horizontal = GratifySpacing.Page))
        LazyRow(contentPadding = PaddingValues(horizontal = GratifySpacing.Page),
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(friends, key = { it.userProfile.id }) { friend ->
                val name = friend.userProfile.displayName ?: "Pengguna Gratify"
                Column(Modifier.width(avatarColumnWidth).clip(GratifyShapes.small)
                    .clickable(role = Role.Button) { onProfile(friend.userProfile.id) }
                    .semantics { contentDescription = "Profil $name, sedang mendengarkan" }
                    .padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        UserAvatar(friend.userProfile.avatarUrl, name, Modifier.size(64.dp), borderWidth = 0.dp)
                        Box(Modifier.size(14.dp).clip(CircleShape).background(GratifyColors.Accent)
                            .border(3.dp, GratifyColors.Background, CircleShape))
                    }
                    Text(name, style = typo().labelMedium, color = GratifyColors.TextPrimary,
                        maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Profile and playback remain separate tap targets, including on large text settings. */
@Composable
fun FriendListItem(item: FriendActivityItem, onUserClick: () -> Unit, onSongClick: () -> Unit, onMessage: (() -> Unit)? = null, showNote: Boolean = true) {
    val name = item.userProfile.displayName ?: "Pengguna Gratify"
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().clip(GratifyShapes.small).clickable(role = Role.Button, onClick = onUserClick)
            .semantics { contentDescription = "Lihat profil $name" }.heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            UserAvatar(item.userProfile.avatarUrl, name, Modifier.size(40.dp), borderWidth = 0.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = typo().titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = GratifyColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(if (item.isOnline) Res.string.friends_listening else
                    if (item.userProfile.showRecentArtists) Res.string.friends_activity_quiet else Res.string.friends_activity_private), style = typo().bodySmall,
                    color = if (item.isOnline) GratifyColors.Accent else GratifyColors.TextSecondary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (onMessage != null) IconButton(onClick = onMessage) {
                Icon(Icons.AutoMirrored.Rounded.Chat, "Pesan $name", Modifier.size(20.dp), tint = GratifyColors.TextSecondary)
            } else Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp), tint = GratifyColors.TextSecondary)
        }
        if (showNote && isNoteActive(item.userProfile.note, item.userProfile.noteUpdatedAt)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(2.dp).height(28.dp).background(GratifyColors.Outline))
                Text(item.userProfile.note.orEmpty(), style = typo().bodyMedium, color = GratifyColors.TextSecondary,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        if (item.videoId.isNotBlank()) {
            Row(Modifier.fillMaxWidth().clip(GratifyShapes.medium).background(GratifyColors.Surface)
                .padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.weight(1f).clip(GratifyShapes.small).clickable(role = Role.Button, onClick = onSongClick)
                    .heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(56.dp).clip(GratifyShapes.small).background(GratifyColors.SurfaceHighest),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.MusicNote, null, tint = GratifyColors.TextSecondary)
                        AsyncImage(model = item.artworkUrl ?: "https://i.ytimg.com/vi/${item.videoId}/hqdefault.jpg",
                            contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.songTitle, style = typo().bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = GratifyColors.TextPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(item.artistName, style = typo().bodySmall, color = GratifyColors.TextSecondary,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                FilledTonalIconButton(onClick = onSongClick, modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = GratifyColors.SurfaceHighest, contentColor = GratifyColors.TextPrimary)) {
                    Icon(Icons.Rounded.PlayArrow, "Putar ${item.songTitle}")
                }
            }
        }
    }
}

@Composable
private fun FriendsMessage(title: String, description: String, action: String, onAction: () -> Unit, empty: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(horizontal = GratifySpacing.Page, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (empty) {
            Icon(Icons.Rounded.PeopleOutline, null, tint = GratifyColors.Accent, modifier = Modifier.size(40.dp))
        }
        Text(title, style = typo().titleLarge.copy(fontWeight = FontWeight.SemiBold), color = GratifyColors.TextPrimary)
        Text(description, style = typo().bodyMedium, color = GratifyColors.TextSecondary)
        if (empty) {
            Button(onClick = onAction) { Text(action) }
        } else {
            OutlinedButton(onClick = onAction) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(action)
            }
        }
    }
}

@Composable
private fun FriendsLoadingState() {
    Column(Modifier.padding(horizontal = GratifySpacing.Page).semantics { contentDescription = "Memuat aktivitas teman" },
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        repeat(3) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(GratifyColors.SurfaceRaised))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.width(120.dp).height(12.dp).clip(GratifyShapes.extraSmall).background(GratifyColors.SurfaceRaised))
                        Box(Modifier.width(88.dp).height(8.dp).clip(GratifyShapes.extraSmall).background(GratifyColors.Surface))
                    }
                }
                Box(Modifier.fillMaxWidth().height(88.dp).clip(GratifyShapes.medium).background(GratifyColors.Surface))
            }
        }
    }
}
