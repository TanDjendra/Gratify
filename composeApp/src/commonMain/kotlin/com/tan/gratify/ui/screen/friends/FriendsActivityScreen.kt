package com.tan.gratify.ui.screen.friends

import com.tan.gratify.ui.theme.GratifyColors
import com.tan.gratify.ui.theme.GratifyShapes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import org.jetbrains.compose.resources.stringResource
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.friends

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.tan.domain.manager.DataStoreManager
import com.tan.gratify.extension.copy
import com.tan.gratify.extension.isScrollingUp
import com.tan.gratify.ui.component.CenterLoadingBox
import com.tan.gratify.ui.component.EndOfPage
import com.tan.gratify.ui.component.UserAvatar
import com.tan.gratify.ui.navigation.destination.social.UserProfileDestination
import com.tan.gratify.ui.navigation.destination.search.SearchDestination
import com.tan.gratify.ui.theme.typo
import com.tan.gratify.viewModel.FriendActivityItem
import com.tan.gratify.viewModel.FriendsActivityViewModel
import com.tan.gratify.viewModel.SharedViewModel
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

// Catatan ("Notes IG") kedaluwarsa setelah 24 jam.
private const val NOTE_TTL_MS = 24L * 60 * 60 * 1000

/** True jika catatan tidak kosong DAN diubah kurang dari 24 jam lalu. */
private fun isNoteActive(note: String?, noteUpdatedAt: String?): Boolean {
    if (note.isNullOrBlank()) return false
    val updated = noteUpdatedAt?.toLongOrNull() ?: return false
    val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
    // `age` bisa NEGATIF bila jam perangkat teman sedikit di belakang jam perangkat
    // penulis catatan (note_updated_at ditulis pakai jam penulis). Kalau kita paksa
    // age >= 0, catatan yang baru diposting bisa "hilang" di perangkat teman padahal
    // datanya ada. Karena itu timestamp masa depan diperlakukan sebagai baru saja
    // diposting (tetap aktif); yang penting hanya batas kedaluwarsa 24 jam.
    val age = now - updated
    return age <= NOTE_TTL_MS
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun FriendsActivityScreen(
    innerPadding: PaddingValues = PaddingValues(0.dp),
    navController: NavController,
    onScrolling: (onTop: Boolean) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    sharedViewModel: SharedViewModel = koinInject(),
    viewModel: FriendsActivityViewModel = koinViewModel()
) {
    val density = LocalDensity.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dataStoreManager: DataStoreManager = koinInject()

    var topAppBarHeight by remember { mutableStateOf(0.dp) }

    var showNoteEditor by remember { mutableStateOf(false) }
    var noteDraft by remember { mutableStateOf("") }


    val listState = rememberLazyListState()
    val isScrollingUp by listState.isScrollingUp()
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect {
                if (it <= 1) {
                    onScrolling.invoke(true)
                } else {
                    onScrolling.invoke(isScrollingUp)
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = innerPadding.copy(
                top = topAppBarHeight + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Catatan kamu ("Notes IG"): tulis untuk ekspresiin lagu/mood ke teman ──
            item {
                val appProfileImage by dataStoreManager.getString("AppProfileImage").collectAsStateWithLifecycle(initialValue = "")
                val appProfileName by dataStoreManager.getString("AppProfileName").collectAsStateWithLifecycle(initialValue = "")
                val myNoteActive = isNoteActive(state.myNote, state.myNoteUpdatedAt)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(GratifyShapes.medium)
                        .clickable {
                            noteDraft = if (myNoteActive) state.myNote ?: "" else ""
                            showNoteEditor = true
                        }
                        .padding(vertical = 4.dp)
                ) {
                    UserAvatar(
                        imageUrl = appProfileImage,
                        name = appProfileName,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = if (myNoteActive) state.myNote ?: "" else "Bagikan catatan… ✏️",
                                style = typo().bodyMedium,
                                color = if (myNoteActive) Color.White else GratifyColors.TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Catatan kamu",
                            style = typo().labelSmall,
                            color = GratifyColors.TextSecondary
                        )
                    }
                }
            }

            if (state.isLoading) {
                item {
                    CenterLoadingBox(
                        modifier = Modifier.fillMaxWidth().height(300.dp)
                    )
                }
            } else if (state.friendsList.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 80.dp, start = 32.dp, end = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Belum Ada Teman",
                            style = typo().titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Aktivitas mendengarkan hanya muncul jika kamu dan pengguna lain saling mengikuti (berteman). Cari dan ikuti balik temanmu untuk melihat aktivitas mereka!",
                            style = typo().bodyMedium,
                            color = GratifyColors.TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(state.friendsList) { item ->
                    FriendListItem(
                        item = item,
                        onUserClick = {
                            navController.navigate(UserProfileDestination(userId = item.userProfile.id))
                        },
                        onSongClick = {
                            if (item.videoId.isNotEmpty()) {
                                sharedViewModel.loadSharedMediaItem(item.videoId)
                            }
                        }
                    )
                }
            }

            item {
                EndOfPage()
            }
        }
    }

    // Header (Top App Bar) with Haze matching LibraryScreen / HomeScreen
    Column(
        Modifier
            .background(MaterialTheme.colorScheme.background).onGloballyPositioned { coordinates ->
                topAppBarHeight = with(density) { coordinates.size.height.toDp() }
            },
    ) {
        val headerImage by dataStoreManager.getString("AppProfileImage").collectAsStateWithLifecycle(initialValue = "")
        val headerName by dataStoreManager.getString("AppProfileName").collectAsStateWithLifecycle(initialValue = "")
        com.tan.gratify.ui.component.ScreenHeader(
            title = stringResource(Res.string.friends), profileName = headerName,
            profileImage = headerImage, onOpenProfile = onOpenDrawer,
            actions = {
                IconButton(onClick = { navController.navigate(SearchDestination) }) {
                    Icon(Icons.Rounded.Search, "Search", tint = Color.White)
                }
                IconButton(onClick = { viewModel.loadFriendsActivity() }) {
                    Icon(Icons.Rounded.Refresh, "Refresh", tint = Color.White)
                }
            },
        )
    }

    if (showNoteEditor) {
        AlertDialog(
            onDismissRequest = { showNoteEditor = false },
            containerColor = GratifyColors.SurfaceRaised,
            title = { Text("Catatan kamu", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Ekspresikan lagu atau suasana hatimu — tampil ke teman.",
                        style = typo().bodySmall,
                        color = GratifyColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = noteDraft,
                        onValueChange = { if (it.length <= 100) noteDraft = it },
                        placeholder = { Text("Tulis catatan…", color = GratifyColors.TextSecondary) },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = GratifyColors.Accent,
                            unfocusedBorderColor = GratifyColors.TextSecondary,
                            cursorColor = GratifyColors.Accent
                        )
                    )
                    Text(
                        text = "${noteDraft.length}/100",
                        style = typo().labelSmall,
                        color = GratifyColors.TextSecondary,
                        modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateMyNote(noteDraft)
                    showNoteEditor = false
                }) { Text("Simpan", color = GratifyColors.Accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.updateMyNote("")
                    showNoteEditor = false
                }) { Text("Hapus", color = GratifyColors.TextSecondary) }
            }
        )
    }
}

@Composable
fun FriendListItem(
    item: FriendActivityItem,
    onUserClick: () -> Unit,
    onSongClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(GratifyShapes.medium)
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), GratifyShapes.medium)
            .padding(14.dp)
    ) {
        // User info row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onUserClick() }
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(GratifyColors.AccentContainer, GratifyColors.SurfaceRaised)
                            )
                        )
                        .border(1.dp, GratifyColors.Outline, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (!item.userProfile.avatarUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = item.userProfile.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        val initial = item.userProfile.displayName?.trim()?.takeIf { it.isNotBlank() }?.take(1)?.uppercase() ?: ""
                        if (initial.isNotEmpty()) {
                            Text(
                                text = initial,
                                style = typo().titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 20.sp
                                ),
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .scale(if (item.isOnline) pulseScale else 1f)
                        .clip(CircleShape)
                        .background(if (item.isOnline) GratifyColors.Accent else GratifyColors.TextSecondary)
                        .border(2.dp, GratifyColors.Background, CircleShape)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.userProfile.displayName ?: "Pengguna Gratify",
                    style = typo().bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Catatan ("Notes IG") milik teman — hanya tampil bila masih aktif (< 24 jam).
                if (isNoteActive(item.userProfile.note, item.userProfile.noteUpdatedAt)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(GratifyShapes.small)
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "“${item.userProfile.note}”",
                            style = typo().bodySmall,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.lastActiveText,
                        style = typo().bodySmall,
                        color = if (item.isOnline) GratifyColors.Accent else GratifyColors.TextSecondary
                    )
                }
            }
        }

        // Now playing song card (if any)
        if (item.videoId.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(GratifyShapes.small)
                    .background(Color.White.copy(alpha = 0.05f))
                    .clickable { onSongClick() }
                    .padding(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(GratifyColors.Accent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Putar",
                            tint = GratifyColors.Accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = item.songTitle,
                            style = typo().bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.artistName,
                            style = typo().bodySmall,
                            color = GratifyColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(GratifyColors.Accent)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Putar",
                        style = typo().labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    )
                }
            }
        }
    }
}
