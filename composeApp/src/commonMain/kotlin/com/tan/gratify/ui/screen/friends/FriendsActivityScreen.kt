package com.tan.gratify.ui.screen.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavDestination.Companion.hasRoute
import com.tan.gratify.ui.navigation.destination.friends.FriendsDestination
import com.tan.gratify.ui.navigation.openPeopleSearch
import com.tan.gratify.ui.navigation.isSocialAccountAvailable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.*
import androidx.navigation.NavController
import com.tan.domain.manager.DataStoreManager
import com.tan.gratify.extension.isScrollingUp
import com.tan.gratify.ui.component.UserAvatar
import com.tan.gratify.ui.navigation.destination.social.UserProfileDestination
import com.tan.gratify.ui.theme.*
import com.tan.gratify.viewModel.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import org.koin.compose.koinInject
import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

// Existing profile notes are persistent text. The timestamp remains metadata for older clients.
internal fun isNoteActive(note: String?, @Suppress("UNUSED_PARAMETER") noteUpdatedAt: String?): Boolean = !note.isNullOrBlank()

@Composable
fun FriendsActivityScreen(innerPadding: PaddingValues = PaddingValues(0.dp), navController: NavController,
    onScrolling: (Boolean) -> Unit = {}, onOpenDrawer: () -> Unit = {}, onLoginRequired: () -> Unit = {},
    sharedViewModel: SharedViewModel = koinInject(), viewModel: FriendsActivityViewModel = koinViewModel(),
    socialModel: FriendsSocialViewModel = koinViewModel()) {
    val activity by viewModel.state.collectAsStateWithLifecycle()
    val social by socialModel.state.collectAsStateWithLifecycle()
    val settings: DataStoreManager = koinInject()
    val profileImage by settings.getString("AppProfileImage").collectAsStateWithLifecycle("")
    val profileName by settings.getString("AppProfileName").collectAsStateWithLifecycle("")
    val pendingNotification by sharedViewModel.socialNotificationTarget.collectAsStateWithLifecycle()
    val pendingStory by sharedViewModel.lyricsStoryDraft.collectAsStateWithLifecycle()
    val supabase: SupabaseClient = koinInject()
    val sessions: com.tan.data.sync.AccountSessionCoordinator = koinInject()
    val access by sessions.access.collectAsStateWithLifecycle()
    val localOwner = (access as? com.tan.data.sync.AccountAccess.Ready)?.owner
    val storedOwner by socialModel.navigation.owner.collectAsStateWithLifecycle()
    val tabName by socialModel.navigation.saved.getStateFlow("inbox_tab", "ACTIVITY").collectAsStateWithLifecycle()
    val tab = notesInboxTab(tabName)
    val page by navController.currentBackStackEntryAsState()
    val isCurrentPage = page?.destination?.hasRoute<FriendsDestination>() == true
    var showPeople by rememberSaveable { mutableStateOf(false) }
    var showNote by rememberSaveable { mutableStateOf(false) }
    var noteDraft by rememberSaveable { mutableStateOf("") }
    var topHeight by remember { mutableStateOf(80.dp) }
    val list = rememberLazyListState()
    val reselect by sharedViewModel.reloadDestination.collectAsStateWithLifecycle()
    LaunchedEffect(reselect) {
        if (reselect == FriendsDestination::class) { list.animateScrollToItem(0); sharedViewModel.reloadDestinationDone() }
    }
    val scrollingUp by list.isScrollingUp()
    val density = androidx.compose.ui.platform.LocalDensity.current
    LifecycleStartEffect(viewModel) { viewModel.start(); onStopOrDispose { viewModel.stop() } }
    LifecycleStartEffect(socialModel) { socialModel.start(); onStopOrDispose { socialModel.stop() } }
    LaunchedEffect(tabName) { if (tabName != tab.name) socialModel.navigation.saved["inbox_tab"] = tab.name }
    LaunchedEffect(localOwner) {
        if (storedOwner != localOwner) { showPeople = false; showNote = false; noteDraft = "" }
        socialModel.navigation.bindOwner(localOwner)
        socialModel.navigation.saveStory(null)
    }
    LaunchedEffect(pendingStory, isCurrentPage) { if (isCurrentPage && pendingStory != null) sharedViewModel.consumeLyricsStory() }
    LaunchedEffect(pendingNotification, localOwner, social.userId, isCurrentPage, access) {
        val target = pendingNotification ?: return@LaunchedEffect
        if (!isCurrentPage) return@LaunchedEffect
        if (localOwner != target.owner) { sharedViewModel.openSocialNotification(null); return@LaunchedEffect }
        if (!isSocialAccountAvailable(access, supabase.auth.currentUserOrNull()?.id)) { onLoginRequired(); return@LaunchedEffect }
        if (social.userId != localOwner) { socialModel.refresh(); return@LaunchedEffect }
        sharedViewModel.openSocialNotification(null)
        target.peer?.let { socialModel.openPeer(it) }
        if (target.story != null && target.peer == null) sharedViewModel.makeToast("Konten ini sudah tidak tersedia.")
    }
    LaunchedEffect(list) { snapshotFlow { list.firstVisibleItemIndex to scrollingUp }.collect { (index, up) -> onScrolling(index <= 1 || up) } }
    fun requireLogin(block: () -> Unit) {
        if (!isSocialAccountAvailable(access, supabase.auth.currentUserOrNull()?.id)) onLoginRequired()
        else { if (social.userId != localOwner) socialModel.refresh(); block() }
    }
    fun profile(id: String) { navController.navigate(UserProfileDestination(id)) }
    Box(Modifier.fillMaxSize().background(GratifyColors.Background)) {
        FriendsInboxContent(activity, social, profileName.orEmpty(), profileImage, tab,
            onTab = { next -> socialModel.navigation.saved["inbox_tab"] = notesInboxTab(next.name).name },
            onStory = {}, onCreateStory = {}, onMoreStories = {},
            onEditNote = { requireLogin { viewModel.clearNoteError(); noteDraft = activity.myNote.orEmpty(); showNote = true } },
            onChat = { requireLogin { socialModel.openPeer(it) } }, onProfile = ::profile,
            onPlay = sharedViewModel::loadSharedMediaItem, onFindFriends = { navController.openPeopleSearch() },
            onRetry = { viewModel.loadFriendsActivity(); socialModel.refresh() },
            contentPadding = PaddingValues(top = topHeight + 4.dp, bottom = innerPadding.calculateBottomPadding() + 20.dp), listState = list, onLogin = onLoginRequired)
        Column(Modifier.background(GratifyColors.Background).onGloballyPositioned { topHeight = with(density) { it.size.height.toDp() } }) {
            FriendsInboxHeader(profileName.orEmpty(), onOpenDrawer,
                onNewMessage = { requireLogin { socialModel.clearError(); socialModel.searchPeople(""); showPeople = true } },
                onFind = { navController.openPeopleSearch() }, profileImage = profileImage)
        }
    }
    if (showPeople) SocialFullScreen(onClose = { showPeople = false }) {
        var query by rememberSaveable { mutableStateOf("") }
        Column(Modifier.fillMaxSize().background(GratifyColors.Background).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showPeople = false }) { Icon(Icons.Rounded.Close, "Tutup pesan baru") }
                Text("Pesan baru", style = typo().titleLarge, color = GratifyColors.TextPrimary)
            }
            OutlinedTextField(query, { query = it; socialModel.searchPeople(it) }, Modifier.fillMaxWidth().padding(16.dp), placeholder = { Text("Cari pengguna Gratify") }, singleLine = true)
            if(social.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                val people = if(query.length < 2) activity.friendsList.map { it.userProfile } else social.people
                items(people, key = { it.id }) { user ->
                    Surface(onClick = { showPeople = false; socialModel.openPeer(user.id) }, color = GratifyColors.Background) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            UserAvatar(user.avatarUrl, user.displayName, Modifier.size(48.dp), borderWidth = 0.dp)
                            Text(user.displayName ?: "Pengguna Gratify", style = typo().titleMedium, color = GratifyColors.TextPrimary)
                        }
                    }
                }
                if(people.isEmpty() && !social.searching) item { InboxNotice(if(query.length < 2) "Cari teman bicara" else "Pengguna belum ditemukan", "Masukkan setidaknya dua huruf nama pengguna.") }
                if(social.error != null) item { Text(social.error!!, color = GratifyColors.Error) }
            }
        }
    }
    if (isCurrentPage && (social.opening || social.activeConversation != null)) SocialFullScreen(onClose = socialModel::closeChat) {
        if(social.activeConversation == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else FriendsChatContent(social, socialModel::closeChat, onProfile = { profile(it) },
            socialModel::send, socialModel::respond, socialModel::loadOlder, socialModel::readVisible,
            onRetry = { social.activeConversation?.let { socialModel.openPeer(it.peerId) } }, savedStateHandle = socialModel.navigation.saved, onLatest = socialModel::showLatestMessages,
            onInvitation = { token -> socialModel.closeChat(); navController.navigate(com.tan.gratify.ui.navigation.destination.library.PlaylistWorkspaceDestination("invite", token = token)) })
    }
    if (showNote && isCurrentPage) FriendsNoteEditor(noteDraft, activity.myNote, activity.noteSaving, activity.noteError != null,
        onChange = { noteDraft = it; viewModel.clearNoteError() },
        onSave = { requireLogin { viewModel.updateMyNote(noteDraft) { showNote = false } } },
        onDelete = { requireLogin { viewModel.updateMyNote("") { showNote = false } } }, onClose = { showNote = false })
}

@Composable
internal fun SocialFullScreen(onClose: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(GratifyColors.Background)) { content() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FriendsNoteEditor(value: String, savedNote: String?, saving: Boolean, failed: Boolean,
    onChange: (String) -> Unit, onSave: () -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!saving) onClose() }, containerColor = GratifyColors.SurfaceRaised,
        title = { Text(stringResource(Res.string.friends_note_title)) },
        text = { FriendsNoteInput(value, onChange, saving, failed) },
        confirmButton = { TextButton(enabled = !saving && value.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp), onClick = onSave) {
            Text(stringResource(if (saving) Res.string.friends_note_saving else Res.string.save)) } },
        dismissButton = { FlowRow {
            if (!savedNote.isNullOrBlank()) TextButton(enabled = !saving, modifier = Modifier.heightIn(min = 48.dp), onClick = onDelete) {
                Text(stringResource(Res.string.delete), color = GratifyColors.Error) }
            TextButton(enabled = !saving, modifier = Modifier.heightIn(min = 48.dp), onClick = onClose) { Text(stringResource(Res.string.cancel)) }
        } })
}

@Composable
internal fun FriendsNoteInput(value: String, onChange: (String) -> Unit, saving: Boolean, failed: Boolean) {
    OutlinedTextField(value, { if (it.length <= 100) onChange(it) }, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(Res.string.friends_note_hint)) }, minLines = 3, maxLines = 5,
        enabled = !saving, isError = failed,
        supportingText = { Text(if (failed) stringResource(Res.string.friends_note_failed)
            else stringResource(Res.string.friends_note_count, value.length)) })
}
