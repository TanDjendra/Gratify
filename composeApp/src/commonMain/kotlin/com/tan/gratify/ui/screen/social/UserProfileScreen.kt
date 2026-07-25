package com.tan.gratify.ui.screen.social

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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Add
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    userId: String,
    navController: NavController,
    viewModel: UserProfileViewModel = koinViewModel(),
    sharedViewModel: SharedViewModel = org.koin.compose.koinInject()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val scrollState = rememberScrollState()
    
    val localPlaylistRepository: com.tan.domain.repository.LocalPlaylistRepository = org.koin.compose.koinInject()
    var showSharedPlaylistSheet by remember { mutableStateOf(false) }
    var selectedSharedPlaylist by remember { mutableStateOf<com.tan.domain.data.entities.SharedPlaylist?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(userId, navBackStackEntry) {
        viewModel.loadProfile(userId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        if (state.isLoading && state.profile == null) {
            CenterLoadingBox(Modifier.fillMaxSize())
        } else if (state.profile != null) {
            val profile = state.profile!!
            val displayName = profile.displayName?.takeIf { it.isNotBlank() } ?: "Pengguna"
            val displayImage = profile.avatarUrl

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // ── Gradient Header + Profile Info ──────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    // Gradient background
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF555555).copy(alpha = 0.6f),
                                        Color(0xFF333333).copy(alpha = 0.2f),
                                        Color(0xFF121212)
                                    )
                                )
                            )
                    )

                    // Back button
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(start = 4.dp, top = 4.dp)
                            .align(Alignment.TopStart)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.baseline_arrow_back_ios_new_24),
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    // Profile content
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 20.dp, vertical = 24.dp)
                    ) {
                        // Profile image
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF5A5A5A), Color(0xFF2B2B2B))
                                    )
                                )
                                .border(1.5.dp, Color(0xFF8E8E8E), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!displayImage.isNullOrEmpty()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalPlatformContext.current)
                                        .data(displayImage)
                                        .diskCachePolicy(CachePolicy.ENABLED)
                                        .diskCacheKey(displayImage)
                                        .crossfade(true)
                                        .build(),
                                    placeholder = painterResource(Res.drawable.holder),
                                    error = painterResource(Res.drawable.holder),
                                    contentDescription = "Avatar",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                val initial = displayName.trim().takeIf { it.isNotBlank() }?.take(1)?.uppercase() ?: ""
                                if (initial.isNotEmpty()) {
                                    Text(
                                        text = initial,
                                        style = typo().displayMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 48.sp
                                        ),
                                        color = Color.White
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Person,
                                        contentDescription = null,
                                        tint = Color(0xFFE0E0E0),
                                        modifier = Modifier.size(56.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(20.dp))

                        // Name + stats
                        Column {
                            Text(
                                text = displayName,
                                style = typo().headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${state.followersCount} pengikut",
                                    style = typo().bodySmall,
                                    color = Color(0xFFB3B3B3),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            navController.navigate(FollowListDestination(userId = userId, initialTab = 0))
                                        }
                                        .padding(vertical = 2.dp)
                                )
                                Text(
                                    text = " · ",
                                    style = typo().bodySmall,
                                    color = Color(0xFFB3B3B3)
                                )
                                Text(
                                    text = "${state.followingCount} mengikuti",
                                    style = typo().bodySmall,
                                    color = Color(0xFFB3B3B3),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            navController.navigate(FollowListDestination(userId = userId, initialTab = 1))
                                        }
                                        .padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                if (state.recentActivityTitle != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E1E1E))
                            .border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp))
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
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
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
                                        .background(if (state.isOnlinePlaying) Color(0xFF1DB954) else Color.Gray)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (state.isOnlinePlaying) "Sedang memutar" else "Terakhir diputar",
                                    style = typo().labelSmall,
                                    color = if (state.isOnlinePlaying) Color(0xFF1DB954) else Color.Gray
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
                                color = Color(0xFFB3B3B3),
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
                        Box(
                            modifier = Modifier
                                .border(1.dp, Color(0xFF727272), RoundedCornerShape(20.dp))
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (state.isFollowing) Color(0xFF282828) else Color(0xFFE0E0E0))
                                .clickable { viewModel.toggleFollow(userId) }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (state.isFollowing) "Berhenti Mengikuti" else "Ikuti",
                                style = typo().bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = if (state.isFollowing) Color.White else Color.Black
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .border(1.dp, Color(0xFF727272), RoundedCornerShape(20.dp))
                                .clip(RoundedCornerShape(20.dp))
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Profil Kamu",
                                style = typo().bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }

                    IconButton(
                        onClick = { /* TODO: More actions */ },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.baseline_more_vert_24),
                            contentDescription = "More",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // ── Top Artists Section ─────────────────────────────────────
                val topArtists = state.profile?.topArtists
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
                                                .background(Color(0xFF333333)),
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

                    if (state.publicPlaylists.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        ) {
                            Text(
                                text = "Belum ada playlist publik",
                                style = typo().titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "$displayName belum membagikan playlist apa pun ke profilnya.",
                                style = typo().bodySmall,
                                color = Color(0xFFB3B3B3),
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
                                            selectedSharedPlaylist = playlist
                                            showSharedPlaylistSheet = true
                                        }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .background(Color(0xFF282828), RoundedCornerShape(4.dp))
                                    ) {
                                        if (!playlist.thumbnailUrl.isNullOrEmpty()) {
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
                                                tint = Color.Gray,
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
                                            text = "Publik • $displayName",
                                            style = typo().bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(80.dp))
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Pengguna tidak ditemukan",
                    style = typo().titleMedium,
                    color = Color.White
                )
            }
        }
        
        if (showSharedPlaylistSheet && selectedSharedPlaylist != null) {
            ModalBottomSheet(
                onDismissRequest = { showSharedPlaylistSheet = false },
                sheetState = sheetState,
                containerColor = Color(0xFF282828),
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
                                append("Ditambahkan ${sp.addCount} kali")
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
                            .clickable {
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
                                        onError = {
                                            showSharedPlaylistSheet = false
                                        }
                                    )
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Tambahkan ke Pustaka",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Tambahkan ke Pustaka",
                            style = typo().bodyLarge,
                            color = Color.White
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
