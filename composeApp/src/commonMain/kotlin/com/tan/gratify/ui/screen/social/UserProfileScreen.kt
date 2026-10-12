package com.tan.gratify.ui.screen.social

import com.tan.gratify.ui.theme.GratifyColors
import com.tan.gratify.ui.theme.GratifyShapes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.tan.gratify.ui.component.CenterLoadingBox
import com.tan.gratify.ui.navigation.destination.list.SharedPlaylistDestination
import com.tan.gratify.ui.navigation.destination.social.FollowListDestination
import com.tan.gratify.ui.theme.typo
import com.tan.gratify.viewModel.UserProfileViewModel
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.baseline_arrow_back_ios_new_24
import gratify.composeapp.generated.resources.baseline_more_vert_24
import gratify.composeapp.generated.resources.holder
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import com.tan.gratify.viewModel.SharedViewModel
import com.tan.gratify.viewModel.auth.ProfileLoginReturn
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    userId: String,
    navController: NavController,
    viewModel: UserProfileViewModel = koinViewModel(),
    sharedViewModel: SharedViewModel = org.koin.compose.koinInject(),
    onLoginRequired: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val scrollState = rememberScrollState()
    
    val localPlaylistRepository: com.tan.domain.repository.LocalPlaylistRepository = org.koin.compose.koinInject()
    val libraryFlow = remember(localPlaylistRepository) { localPlaylistRepository.getAllLocalPlaylists() }
    val libraryPlaylists by libraryFlow.collectAsStateWithLifecycle(initialValue=emptyList())
    val savedSources = libraryPlaylists.mapNotNull { it.sourceSharedPlaylistId }.toSet()
    var showSharedPlaylistSheet by remember { mutableStateOf(false) }
    var selectedSharedPlaylist by remember { mutableStateOf<com.tan.domain.data.entities.SharedPlaylist?>(null) }
    val sheetState = com.tan.gratify.ui.component.rememberBusyModalSheetState(state.savingPlaylist)

    var playlistSaveError by remember(userId) { mutableStateOf<String?>(null) }
    val supabase: SupabaseClient = org.koin.compose.koinInject()
    val sessionStatus by supabase.auth.sessionStatus.collectAsStateWithLifecycle()
    LaunchedEffect(userId, navBackStackEntry, sessionStatus) {
        viewModel.loadProfile(userId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GratifyColors.Background),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (state.isLoading && state.profile == null) {
            ProfileLoadStatus(loading = true, message = "Memuat profil…", onBack = { navController.popBackStack() }, onRetry = { viewModel.loadProfile(userId) })
        } else if (state.profile != null) {
            val profile = state.profile!!
            val displayName = profile.displayName?.takeIf { it.isNotBlank() } ?: "Pengguna"
            val displayImage = profile.avatarUrl

            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp).fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                val countsVisible = state.isOwnProfile || profile.showFollowers
                fun countLabel(count: Int, noun: String): String = when {
                    !countsVisible -> "$noun disembunyikan"
                    state.countsLoading -> "Memuat ${noun.lowercase()}…"
                    state.countsError != null -> "$noun belum dimuat"
                    else -> "$count ${noun.lowercase()}" + if (state.isOwnProfile) "" else " terlihat"
                }
                com.tan.gratify.ui.component.ProfileIdentityHeader(
                    name = displayName, imageUrl = displayImage,
                    followersLabel = countLabel(state.followersCount, "Pengikut"),
                    followingLabel = countLabel(state.followingCount, "Mengikuti"), countsClickable = countsVisible,
                    onBack = { navController.popBackStack() },
                    onFollowers = { navController.navigate(FollowListDestination(userId, 0)) },
                    onFollowing = { navController.navigate(FollowListDestination(userId, 1)) })

                if (state.recentActivityTitle != null && (state.isOwnProfile || profile.showRecentArtists)) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clip(GratifyShapes.medium)
                            .background(GratifyColors.Surface)
                            .border(1.dp, GratifyColors.Divider, GratifyShapes.medium)
                            .clickable {
                                state.recentActivityVideoId?.takeIf { it.isNotEmpty() }?.let { vid ->
                                    sharedViewModel.loadSharedMediaItem(vid)
                                }
                            }
                            .padding(12.dp)
                    ) {
                        Box(modifier = Modifier.size(44.dp)) {
                            if (!state.recentActivityVideoId.isNullOrEmpty()) {
                                AsyncImage(
                                    model = "https://i.ytimg.com/vi/${state.recentActivityVideoId}/mqdefault.jpg",
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(GratifyShapes.small),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (state.isOnlinePlaying) GratifyColors.Accent else GratifyColors.TextSecondary)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (state.isOnlinePlaying) "Sedang memutar" else "Terakhir diputar",
                                    style = typo().labelSmall,
                                    color = if (state.isOnlinePlaying) GratifyColors.Accent else GratifyColors.TextSecondary
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = state.recentActivityTitle ?: "",
                                style = typo().bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1
                            )
                            Text(
                                text = state.recentActivityArtist ?: "-",
                                style = typo().bodySmall,
                                color = GratifyColors.TextSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }

                // ── Action Buttons Row ──────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(top = 4.dp, bottom = 20.dp)
                ) {
                    if (!state.isOwnProfile) {
                        Button(
                            onClick = { viewModel.toggleFollow(userId) {
                                ProfileLoginReturn.request(userId)
                                onLoginRequired()
                            } },
                            enabled = state.followReady && !state.isFollowBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = if (state.isFollowing) GratifyColors.SurfaceRaised else GratifyColors.Accent)
                        ) {
                            Text(if (state.isFollowBusy) "Menyimpan…" else if (!state.followReady) "Memuat…"
                                else if (state.isFollowing) "Berhenti mengikuti" else "Ikuti")
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .border(1.dp, GratifyColors.Outline, GratifyShapes.large)
                                .clip(GratifyShapes.large)
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Profil Kamu",
                                style = typo().bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }


                }

                state.followError?.let { error ->
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Text(error, color = GratifyColors.TextSecondary)
                        TextButton(onClick = { viewModel.loadProfile(userId) }) { Text("Muat ulang status") }
                    }
                }
                state.countsError?.let { error ->
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Text(error, color = GratifyColors.TextSecondary)
                        TextButton(onClick = { viewModel.loadProfile(userId) }) { Text("Muat ulang jumlah akun") }
                    }
                }

                // ── Top Artists Section ─────────────────────────────────────
                val topArtists = state.profile?.topArtists?.takeIf { state.isOwnProfile || state.profile?.showRecentArtists == true }
                if (!topArtists.isNullOrEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                    ) {
                        Text(
                            text = "Artis yang diikuti",
                            style = typo().titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        androidx.compose.foundation.lazy.LazyRow(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(topArtists.size) { index ->
                                val artist = topArtists[index]
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(90.dp)
                                        .clickable { navController.navigate(com.tan.gratify.ui.navigation.destination.list.ArtistDestination(artist.channelId)) }
                                ) {
                                    if (artist.thumbnails != null) {
                                        coil3.compose.AsyncImage(
                                            model = artist.thumbnails,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(90.dp)
                                                .clip(androidx.compose.foundation.shape.CircleShape)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(90.dp)
                                                .clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(GratifyColors.Divider),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = androidx.compose.material.icons.Icons.Rounded.Person,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(48.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = artist.name,
                                        style = typo().bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                if (topArtists.isNullOrEmpty()) {
                    com.tan.gratify.ui.component.ProfileContentPlaceholder("Artis yang diikuti",
                        if (!state.isOwnProfile && state.profile?.showRecentArtists != true) "Aktivitas disembunyikan oleh pemilik profil" else "Belum ada artis pilihan",
                        Modifier.padding(horizontal = 20.dp))
                }

                // ── Public Playlists Section ────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Text(
                        text = "Playlist",
                        style = typo().titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    val canSeePlaylists = state.isOwnProfile || state.profile?.showPlaylists == true
                    if (state.playlistLoading && canSeePlaylists) {
                        Text("Memuat playlist…", color = GratifyColors.TextSecondary)
                    } else if (state.playlistError != null && canSeePlaylists) {
                        Text(state.playlistError.orEmpty(), color = GratifyColors.TextSecondary)
                        androidx.compose.material3.TextButton(onClick = { viewModel.loadProfile(userId) }) { Text("Coba lagi") }
                    } else if (!canSeePlaylists || state.publicPlaylists.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        ) {
                            Text(
                                text = if (canSeePlaylists) "Belum ada playlist publik" else "Playlist disembunyikan",
                                style = typo().titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (canSeePlaylists) "$displayName belum membagikan playlist apa pun ke profilnya." else "Pemilik profil membatasi siapa yang dapat melihat playlistnya.",
                                style = typo().bodySmall,
                                color = GratifyColors.TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            state.publicPlaylists.forEach { playlist ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val local = if (state.isOwnProfile && !playlist.isPublic && !playlist.clientSyncId.isNullOrBlank())
                                                libraryPlaylists.firstOrNull { it.syncId == playlist.clientSyncId && it.sourceSharedPlaylistId == null } else null
                                            if (local != null) navController.navigate(com.tan.gratify.ui.navigation.destination.list.LocalPlaylistDestination(local.id))
                                            else playlist.id?.let { navController.navigate(SharedPlaylistDestination(it)) }
                                        }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .background(GratifyColors.SurfaceRaised, GratifyShapes.extraSmall)
                                    ) {
                                        if(playlist.automaticImages.isNotEmpty()&&(playlist.thumbnailUrl.isNullOrBlank()||playlist.thumbnailUrl?.contains("i.ytimg.com")==true)) {
                                            com.tan.gratify.ui.component.PlaylistAutomaticArtwork(playlist.automaticImages,Modifier.fillMaxSize(),playlist.title)
                                        } else if (!playlist.thumbnailUrl.isNullOrEmpty()) {
                                            AsyncImage(
                                                model = playlist.thumbnailUrl,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                painter = painterResource(Res.drawable.holder),
                                                contentDescription = null,
                                                tint = GratifyColors.TextSecondary,
                                                modifier = Modifier
                                                    .align(Alignment.Center)
                                                    .size(24.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = playlist.title,
                                            style = typo().bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White
                                        )
                                        Text(
                                            text = if (playlist.isPublic) "Publik • $displayName • ${playlist.addCount} simpan" else "Privat • Hanya Anda",
                                            style = typo().bodySmall,
                                            color = GratifyColors.TextSecondary
                                        )
                                    }
                                    if (!state.isOwnProfile) IconButton(onClick = {
                                        if (playlist.id in savedSources) {
                                            navController.navigate(SharedPlaylistDestination(requireNotNull(playlist.id))) { launchSingleTop=true }
                                        } else {
                                        selectedSharedPlaylist = playlist
                                        playlistSaveError = null
                                        showSharedPlaylistSheet = true
                                        }
                                    }, enabled = playlist.id != null && !state.savingPlaylist) {
                                        Icon(if (playlist.id in savedSources) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                            contentDescription = if (playlist.id in savedSources) "Playlist tersimpan; buka untuk mengelola" else "Simpan playlist ke Pustaka",
                                            tint = if (playlist.id in savedSources) GratifyColors.Accent else Color.White)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(80.dp))
            }
        } else {
            ProfileLoadStatus(loading = false,
                message = state.profileError ?: "Pengguna tidak ditemukan",
                retryAvailable = state.profileError != null,
                onBack = { navController.popBackStack() }, onRetry = { viewModel.loadProfile(userId) })
        }

        if (showSharedPlaylistSheet && selectedSharedPlaylist != null) {
            ModalBottomSheet(
                onDismissRequest = { if (!state.savingPlaylist) showSharedPlaylistSheet = false },
                sheetState = sheetState,
                containerColor = GratifyColors.SurfaceRaised,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Text(
                        text = selectedSharedPlaylist?.title ?: "",
                        style = typo().titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    selectedSharedPlaylist?.let { sp ->
                        val creator = sp.creatorName?.takeIf { it.isNotBlank() }
                        val subtitle = buildString {
                            if (creator != null) append("Dibuat oleh $creator")
                            if (sp.addCount > 0) {
                                if (isNotEmpty()) append(" • ")
                                append("${sp.addCount} simpan")
                            }
                        }
                        if (subtitle.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = subtitle,
                                style = typo().bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !state.savingPlaylist) {
                                playlistSaveError = null
                                val playlist = selectedSharedPlaylist
                                if (playlist != null && playlist.id != null) {
                                    viewModel.addSharedPlaylistToLibrary(
                                        playlistId = playlist.id!!,
                                        title = playlist.title,
                                        thumbnailUrl = playlist.thumbnailUrl,
                                        localPlaylistRepository = localPlaylistRepository,
                                        creatorName = playlist.creatorName,
                                        onSuccess = {
                                            showSharedPlaylistSheet = false
                                        },
                                        onError = { playlistSaveError = it }
                                    )
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FavoriteBorder,
                            contentDescription = "Tambahkan ke Pustaka",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = if (state.savingPlaylist) "Menyimpan…" else "Tambahkan ke Pustaka",
                            style = typo().bodyLarge,
                            color = Color.White
                        )
                    }
                    
                    playlistSaveError?.let { Text(it, color = GratifyColors.TextSecondary, modifier = Modifier.padding(vertical = 8.dp)) }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
internal fun ProfileLoadStatus(
    loading: Boolean, message: String, onBack: () -> Unit, onRetry: () -> Unit,
    retryAvailable: Boolean = true,
) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)) {
            Icon(painterResource(Res.drawable.baseline_arrow_back_ios_new_24), contentDescription = "Kembali", tint = Color.White)
        }
        Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (loading) CircularProgressIndicator(color = GratifyColors.Accent, modifier = Modifier.padding(bottom = 16.dp))
            Text(message, color = Color.White, textAlign = TextAlign.Center)
            if (!loading && retryAvailable) TextButton(onClick = onRetry) { Text("Coba lagi") }
        }
    }
}
