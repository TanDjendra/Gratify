package com.tan.gratify.ui.screen.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.SavedStateHandle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tan.domain.data.model.social.DirectMessage
import com.tan.gratify.ui.component.UserAvatar
import com.tan.gratify.ui.theme.*
import com.tan.gratify.viewModel.FriendsSocialState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

internal fun messageTime(value: String): String = runCatching {
    val time = Instant.parse(value).toLocalDateTime(TimeZone.currentSystemDefault()).time
    "${time.hour.toString().padStart(2,'0')}:${time.minute.toString().padStart(2,'0')}"
}.getOrDefault("")

@Composable
fun FriendsChatContent(state: FriendsSocialState, onClose: () -> Unit, onProfile: (String) -> Unit,
    onSend: (String, () -> Unit) -> Unit, onRespond: (Boolean) -> Unit,
    onOlder: () -> Unit, onRead: (DirectMessage) -> Unit, onRetry: () -> Unit, savedStateHandle: SavedStateHandle? = null, onLatest: () -> Unit = {}, onInvitation: (String) -> Unit = {}) {
    val conversation = state.activeConversation ?: return
    var draft by rememberSaveable(conversation.id) { mutableStateOf(savedStateHandle?.get<String>("chat_draft_${conversation.id}") ?: "") }
    val list = rememberLazyListState(
        initialFirstVisibleItemIndex = savedStateHandle?.get<Int>("chat_index_${conversation.id}") ?: 0,
        initialFirstVisibleItemScrollOffset = savedStateHandle?.get<Int>("chat_offset_${conversation.id}") ?: 0)
    LaunchedEffect(conversation.id, list) {
        snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }.collect { (index, offset) ->
            savedStateHandle?.set("chat_index_${conversation.id}", index)
            savedStateHandle?.set("chat_offset_${conversation.id}", offset)
        }
    }
    val incomingRequest = !conversation.accepted && conversation.requestFrom != state.userId
    val waiting = !conversation.accepted && conversation.requestFrom == state.userId && state.messages.isNotEmpty()
    val messages by rememberUpdatedState(state.messages)
    LaunchedEffect(conversation.id) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }.lastOrNull { it.startsWith("message:") } }
            .distinctUntilChanged().collect { key -> messages.find { "message:${it.id}" == key }?.let(onRead) }
    }
    var previousLast by rememberSaveable(conversation.id) { mutableStateOf(savedStateHandle?.get<String>("chat_last_${conversation.id}")) }
    LaunchedEffect(state.messages.lastOrNull()?.id) {
        val latest = state.messages.lastOrNull() ?: return@LaunchedEffect
        val wasAtBottom = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 2 } ?: true
        if (!state.browsingOlderMessages && (previousLast == null || latest.id != previousLast && (latest.senderId == state.userId || wasAtBottom))) {
            val headerItems = (if (state.moreMessages) 1 else 0) + (if (state.chatLoading) 1 else 0)
            list.scrollToItem((state.messages.size - 1 + headerItems).coerceAtLeast(0))
        }
        previousLast = latest.id
        savedStateHandle?.set("chat_last_${conversation.id}", latest.id)
    }
    Column(Modifier.fillMaxSize().background(GratifyColors.Background).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") }
            UserAvatar(conversation.peerAvatar, conversation.peerName, Modifier.size(38.dp), borderWidth = 0.dp)
            Text(conversation.peerName ?: "Pengguna Gratify", Modifier.weight(1f).padding(horizontal = 12.dp),
                style = typo().titleMedium.copy(fontWeight = FontWeight.SemiBold), color = GratifyColors.TextPrimary)
            IconButton(onClick = { onProfile(conversation.peerId) }) { Icon(Icons.Rounded.PersonOutline, "Lihat profil") }
        }
        HorizontalDivider(color = GratifyColors.Divider)
        if (state.browsingOlderMessages) {
            TextButton(onClick = onLatest, enabled = !state.chatLoading, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Kembali ke pesan terbaru")
            }
        }
        if (incomingRequest) {
            Column(Modifier.fillMaxWidth().background(GratifyColors.Surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Permintaan pesan", style = typo().titleSmall, color = GratifyColors.TextPrimary)
                Text("Terima permintaan untuk mulai saling membalas.", style = typo().bodySmall, color = GratifyColors.TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onRespond(true) }, enabled = !state.busy) { Text("Terima") }
                    TextButton(onClick = { onRespond(false) }, enabled = !state.busy) { Text("Tolak") }
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.moreMessages) item("older") { TextButton(onClick = onOlder, enabled = !state.loadingOlder) { Text(if(state.loadingOlder) "Memuat…" else "Pesan sebelumnya") } }
            if (state.chatLoading) item("loading") { Text("Memuat pesan…", color = GratifyColors.TextSecondary) }
            items(state.messages, key = { "message:${it.id}" }) { message ->
                val own = message.senderId == state.userId
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if(own) Arrangement.End else Arrangement.Start) {
                    Surface(shape = GratifyShapes.large, color = if(own) GratifyColors.AccentContainer else GratifyColors.SurfaceRaised,
                        modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(.88f)) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val invite = if (message.kind in listOf("playlist_invite", "blend_invite") && message.invitationId != null)
                                com.tan.domain.utils.parseWorkspaceInvitation(message.body.lineSequence().lastOrNull().orEmpty()) else null
                            if (invite != null) {
                                Text(message.body.substringBefore('\n'), style = typo().titleSmall, color = GratifyColors.TextPrimary)
                                if(com.tan.domain.utils.PlaylistFeaturePolicy.experimentalEnabled) {
                                    Text("Buka undangan untuk memeriksa playlist dan bergabung.", style = typo().bodySmall)
                                    TextButton(onClick = { onInvitation(invite) }) { Text("Buka undangan") }
                                } else Text(com.tan.domain.utils.PlaylistFeaturePolicy.archivedMessage,style=typo().bodySmall)
                            } else Text(message.body, style = typo().bodyMedium, color = GratifyColors.TextPrimary)
                            Text(messageTime(message.createdAt), style = typo().labelSmall, color = GratifyColors.TextSecondary, modifier = Modifier.align(Alignment.End))
                        }
                    }
                }
            }
            if (state.messages.isEmpty() && !state.chatLoading && state.chatError == null) item("empty") {
                InboxNotice("Mulai dari sapaan", "Bagikan kabar atau musik yang sedang kamu dengarkan.")
            }
        }
        if (state.chatError != null) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(state.chatError, Modifier.weight(1f), style = typo().bodySmall, color = GratifyColors.Error)
            TextButton(onClick = onRetry) { Text("Muat ulang") }
        }
        if (waiting) Text("Permintaan terkirim. Tunggu hingga diterima untuk mengirim pesan berikutnya.",
            Modifier.padding(16.dp), style = typo().bodySmall, color = GratifyColors.TextSecondary)
        if (!incomingRequest && !waiting) Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(draft, { if(it.length <= 2000) { draft = it; savedStateHandle?.set("chat_draft_${conversation.id}", it) } }, Modifier.weight(1f),
                placeholder = { Text("Tulis pesan…") }, maxLines = 5, shape = GratifyShapes.extraLarge,
                enabled = !state.busy, supportingText = if(draft.length > 1800) ({ Text("${draft.length}/2000") }) else null)
            FilledIconButton(onClick = { onSend(draft) { draft = ""; savedStateHandle?.set("chat_draft_${conversation.id}", "") } }, enabled = draft.isNotBlank() && !state.busy,
                modifier = Modifier.padding(bottom = 4.dp).size(48.dp)) {
                if(state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.AutoMirrored.Rounded.Send, "Kirim pesan")
            }
        }
    }
}
