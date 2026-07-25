package com.tan.gratify.ui.screen.home

import com.tan.logger.Logger
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import com.tan.gratify.ui.component.UserAvatar
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.foundation.Image
import com.tan.gratify.expect.shareImage
import com.tan.gratify.expect.shareUrl
import gratify.composeapp.generated.resources.circle_app_icon
import com.tan.gratify.expect.ui.QrCodeView
import com.tan.gratify.expect.ui.rememberQrScannerLauncher
import com.tan.gratify.ui.navigation.destination.social.UserProfileDestination
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull
import com.tan.gratify.expect.ui.photoPickerResult
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.aspectRatio
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.tan.domain.manager.DataStoreManager
import com.tan.gratify.ui.theme.typo
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import gratify.composeapp.generated.resources.Res
import com.tan.gratify.viewModel.UserProfileViewModel
import com.tan.gratify.utils.compressImage
import io.github.jan.supabase.storage.storage
import kotlinx.datetime.Clock
import gratify.composeapp.generated.resources.baseline_arrow_back_ios_new_24
import gratify.composeapp.generated.resources.baseline_settings_24
import gratify.composeapp.generated.resources.baseline_more_vert_24
import gratify.composeapp.generated.resources.holder
import io.github.jan.supabase.postgrest.postgrest

import com.tan.domain.repository.LocalPlaylistRepository
import com.tan.domain.data.entities.LocalPlaylistEntity
import com.tan.domain.repository.ArtistRepository
import com.tan.gratify.ui.navigation.destination.list.LocalPlaylistDestination
import com.tan.gratify.ui.navigation.destination.list.ArtistDestination
import com.tan.gratify.ui.navigation.destination.social.FollowListDestination
import org.koin.compose.viewmodel.koinViewModel
import androidx.navigation.compose.currentBackStackEntryAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController
) {
    val dataStoreManager: DataStoreManager = koinInject()
    val supabase: SupabaseClient = koinInject()
    val localPlaylistRepository: LocalPlaylistRepository = koinInject()
    val socialRepository: com.tan.domain.repository.SocialRepository = koinInject()
    val artistRepository: ArtistRepository = koinInject()
    val userProfileViewModel: UserProfileViewModel = koinViewModel()
    val sharedViewModel: com.tan.gratify.viewModel.SharedViewModel = koinInject()
    
    val userProfileState by userProfileViewModel.state.collectAsStateWithLifecycle()
    val nowPlayingData by sharedViewModel.nowPlayingScreenData.collectAsStateWithLifecycle()
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(navBackStackEntry) {
        val currentUserId = supabase.auth.currentUserOrNull()?.id
        if (currentUserId != null) {
            userProfileViewModel.loadProfile(currentUserId)
        }
    }

    val localPlaylists by localPlaylistRepository.getAllLocalPlaylists().collectAsStateWithLifecycle(initialValue = emptyList())
    // Hanya playlist BUATAN SENDIRI yang boleh dipajang di profil. Playlist hasil menyimpan/
    // menambah dari orang lain punya sourceSharedPlaylistId != null → dikecualikan dari pajangan.
    val ownPlaylists = localPlaylists.filter { it.sourceSharedPlaylistId.isNullOrBlank() }
    val recentArtists by artistRepository.getFollowedArtists().collectAsStateWithLifecycle(initialValue = emptyList())
    
    LaunchedEffect(recentArtists) {
        if (recentArtists.isNotEmpty()) {
            userProfileViewModel.syncTopArtists(recentArtists)
        }
    }
    val appProfileName by dataStoreManager.getString("AppProfileName").collectAsStateWithLifecycle(initialValue = "")
    val appProfileImage by dataStoreManager.getString("AppProfileImage").collectAsStateWithLifecycle(initialValue = "")

    val displayImage = userProfileState.profile?.avatarUrl?.takeIf { it.isNotBlank() } ?: appProfileImage
    val displayName = userProfileState.profile?.displayName?.takeIf { it.isNotBlank() } ?: appProfileName?.takeIf { it.isNotBlank() } ?: "Pengguna"

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    var showEditProfile by remember { mutableStateOf(false) }
    var editName by remember(displayName) { mutableStateOf(displayName) }
    var editImage by remember(displayImage) { mutableStateOf(displayImage ?: "") }
    
    var showPrivacySheet by remember { mutableStateOf(false) }
    var showMoreOptionsSheet by remember { mutableStateOf(false) }
    var showShareProfileSheet by remember { mutableStateOf(false) }
    var showLyricsSelectionSheet by remember { mutableStateOf(false) }
    var selectedLyricsSnippet by remember { mutableStateOf<String?>(null) }
    var shareCardBgColor by remember { mutableStateOf(Color(0xFF2E3B4E)) }
    val graphicsLayer = rememberGraphicsLayer()
    var isPreviewMode by remember { mutableStateOf(false) }
    var showCodeOverlay by remember { mutableStateOf(false) }

    val scanLauncher = rememberQrScannerLauncher { scannedValue ->
        showCodeOverlay = false
        val scannedId = when {
            scannedValue.startsWith("gratify://profile/") -> scannedValue.removePrefix("gratify://profile/")
            scannedValue.contains("/profile/") -> scannedValue.substringAfterLast("/profile/")
            else -> scannedValue
        }.trim()
        if (scannedId.isNotBlank() && scannedId != "gratify://profile/") {
            navController.navigate(UserProfileDestination(userId = scannedId))
        } else {
            userProfileViewModel.makeToast("QR Code tidak memuat ID Profil yang valid.")
        }
    }


    var showAddPlaylistSheet by remember { mutableStateOf(false) }
    var selectedPinnedPlaylist by remember { mutableStateOf<LocalPlaylistEntity?>(null) }
    // Sumber kebenaran "playlist dipajang di profil" adalah SERVER (cloud_playlists is_public=true
    // milik user), bukan DataStore lokal. Ini membuatnya realtime + tidak hilang saat ganti akun /
    // logout-login, karena cloud_playlists persist per-user dan ID lokalnya di-remap saat sync-down.
    var pinnedPlaylistIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val refreshPinnedPlaylists: suspend () -> Unit = {
        val uid = supabase.auth.currentUserOrNull()?.id
        pinnedPlaylistIds = if (uid != null) {
            socialRepository.getUserPublicPlaylists(uid).firstOrNull()?.getOrNull()
                ?.mapNotNull { it.localPlaylistId }?.toSet() ?: emptySet()
        } else {
            emptySet()
        }
    }
    // Selalu ambil daftar playlist yang dipajang langsung dari server (realtime),
    // termasuk saat kembali ke layar profil setelah ganti akun / logout-login.
    LaunchedEffect(navBackStackEntry) {
        refreshPinnedPlaylists()
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    val showFollowersFlow = remember { dataStoreManager.getString("privacy_show_followers") }
    val showPlaylistFlow = remember { dataStoreManager.getString("privacy_show_playlist") }
    val showRecentArtistsFlow = remember { dataStoreManager.getString("privacy_show_recent_artists") }
    
    val showFollowersStr by showFollowersFlow.collectAsStateWithLifecycle(initialValue = "TRUE")
    val showPlaylistStr by showPlaylistFlow.collectAsStateWithLifecycle(initialValue = "TRUE")
    val showRecentArtistsStr by showRecentArtistsFlow.collectAsStateWithLifecycle(initialValue = "TRUE")
    
    val showFollowers = showFollowersStr != "FALSE"
    val showPlaylist = showPlaylistStr != "FALSE"
    val showRecentArtists = showRecentArtistsStr != "FALSE"


    val photoPicker = photoPickerResult { uri ->
        if (uri != null) {
            editImage = uri
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
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
                                contentDescription = "Profile",
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
                            style = typo().headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        AnimatedVisibility(visible = showFollowers) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${userProfileState.followersCount} pengikut",
                                    style = typo().bodySmall,
                                    color = Color(0xFFB3B3B3),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            val uid = userProfileState.profile?.id ?: supabase.auth.currentUserOrNull()?.id
                                            if (uid != null) {
                                                navController.navigate(FollowListDestination(userId = uid, initialTab = 0))
                                            }
                                        }
                                        .padding(vertical = 2.dp)
                                )
                                Text(
                                    text = " · ",
                                    style = typo().bodySmall,
                                    color = Color(0xFFB3B3B3)
                                )
                                Text(
                                    text = "${userProfileState.followingCount} mengikuti",
                                    style = typo().bodySmall,
                                    color = Color(0xFFB3B3B3),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            val uid = userProfileState.profile?.id ?: supabase.auth.currentUserOrNull()?.id
                                            if (uid != null) {
                                                navController.navigate(FollowListDestination(userId = uid, initialTab = 1))
                                            }
                                        }
                                        .padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── Action Buttons (Edit, Settings, More) ───────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 4.dp, bottom = 20.dp)
            ) {
                if (isPreviewMode) {
                    // Follow button for preview
                    Box(
                        modifier = Modifier
                            .border(1.dp, Color(0xFF727272), RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { /* TODO: Follow action */ }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Ikuti",
                            style = typo().bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }

                    // More options icon
                    IconButton(
                        onClick = { showMoreOptionsSheet = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.baseline_more_vert_24),
                            contentDescription = "More",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    // Edit button
                    Box(
                        modifier = Modifier
                            .border(1.dp, Color(0xFF727272), RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                editName = appProfileName ?: ""
                                editImage = appProfileImage ?: ""
                                showEditProfile = true 
                            }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Edit",
                            style = typo().bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }

                    // Settings icon
                    IconButton(
                        onClick = { showPrivacySheet = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.baseline_settings_24),
                            contentDescription = "Settings",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // More options icon
                    IconButton(
                        onClick = { showMoreOptionsSheet = true },
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
            }

        // ── Add Playlist Sheet ────────────────────────────────────────
        if (showAddPlaylistSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAddPlaylistSheet = false },
                sheetState = sheetState,
                containerColor = Color(0xFF1E1E1E),
                dragHandle = null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Tambahkan playlist",
                                style = typo().titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Playlist akan muncul di profilmu",
                                style = typo().bodySmall,
                                color = Color(0xFFB3B3B3)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .border(1.dp, Color(0xFF727272), RoundedCornerShape(20.dp))
                                .clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    // Add all local playlists (optimistic, lalu sinkron + refresh dari server)
                                    val allIds = ownPlaylists.map { it.id }.toSet()
                                    pinnedPlaylistIds = pinnedPlaylistIds + allIds
                                    coroutineScope.launch {
                                        val userId = supabase.auth.currentUserOrNull()?.id
                                        if (userId != null) {
                                            ownPlaylists.forEach { pl ->
                                                socialRepository.syncUpPlaylist(pl.id, userId, isPublic = true).collect {}
                                            }
                                            refreshPinnedPlaylists()
                                        }
                                    }
                                    showAddPlaylistSheet = false
                                }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Tambahkan semua",
                                style = typo().bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Yang bisa dipilih untuk dipajang: buatan sendiri + yang mungkin sudah
                        // terlanjur dipajang sebelumnya (biar tetap bisa dilepas dari sini).
                        val pickerPlaylists = localPlaylists.filter {
                            it.sourceSharedPlaylistId.isNullOrBlank() || it.id in pinnedPlaylistIds
                        }
                        if (pickerPlaylists.isEmpty()) {
                            Text(
                                text = "Kamu belum membuat playlist apa pun.",
                                style = typo().bodyMedium,
                                color = Color(0xFFB3B3B3),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
                            )
                        } else {
                            pickerPlaylists.forEach { playlist ->
                                val isAdded = playlist.id in pinnedPlaylistIds
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .background(Color(0xFF282828), RoundedCornerShape(4.dp))
                                    ) {
                                        if (playlist.thumbnail != null) {
                                            AsyncImage(
                                                model = playlist.thumbnail,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                painter = painterResource(Res.drawable.holder),
                                                contentDescription = null,
                                                tint = Color.Gray,
                                                modifier = Modifier.align(Alignment.Center).size(24.dp)
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
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Publik • Kamu",
                                            style = typo().bodySmall,
                                            color = Color(0xFFB3B3B3)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))

                                    if (isAdded) {
                                        Box(
                                            modifier = Modifier
                                                .border(1.dp, Color.Transparent, RoundedCornerShape(20.dp))
                                                .background(Color(0xFFE0E0E0), RoundedCornerShape(20.dp))
                                                .clickable {
                                                    pinnedPlaylistIds = pinnedPlaylistIds - playlist.id
                                                    coroutineScope.launch {
                                                        val userId = supabase.auth.currentUserOrNull()?.id
                                                        if (userId != null) {
                                                            socialRepository.syncUpPlaylist(playlist.id, userId, isPublic = false).collect {}
                                                            socialRepository.unshareOrHideCloudPlaylist(playlist.id, userId).collect {}
                                                            refreshPinnedPlaylists()
                                                        }
                                                    }
                                                }
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = "Ditambahkan",
                                                style = typo().bodySmall.copy(fontWeight = FontWeight.Bold),
                                                color = Color.Black
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .border(1.dp, Color.Transparent, RoundedCornerShape(20.dp))
                                                .background(Color.White, RoundedCornerShape(20.dp))
                                                .clickable {
                                                    pinnedPlaylistIds = pinnedPlaylistIds + playlist.id
                                                    coroutineScope.launch {
                                                        val userId = supabase.auth.currentUserOrNull()?.id
                                                        if (userId != null) {
                                                            socialRepository.syncUpPlaylist(playlist.id, userId, isPublic = true).collect {}
                                                            refreshPinnedPlaylists()
                                                        }
                                                    }
                                                }
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = "Tambahkan",
                                                style = typo().bodySmall.copy(fontWeight = FontWeight.Bold),
                                                color = Color.Black
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
        
        if (selectedPinnedPlaylist != null) {
            val playlist = selectedPinnedPlaylist!!
            ModalBottomSheet(
                onDismissRequest = { selectedPinnedPlaylist = null },
                sheetState = sheetState,
                containerColor = Color(0xFF1E1E1E),
                dragHandle = null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Text(
                        text = playlist.title,
                        style = typo().titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedPinnedPlaylist = null
                                navController.navigate(LocalPlaylistDestination(playlist.id))
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Menu,
                            contentDescription = "Lihat Playlist",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Lihat Playlist",
                            style = typo().bodyLarge,
                            color = Color.White
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                pinnedPlaylistIds = pinnedPlaylistIds - playlist.id
                                coroutineScope.launch {
                                    val userId = supabase.auth.currentUserOrNull()?.id
                                    if (userId != null) {
                                        socialRepository.syncUpPlaylist(playlist.id, userId, isPublic = false).collect {}
                                        socialRepository.unshareOrHideCloudPlaylist(playlist.id, userId).collect {}
                                        refreshPinnedPlaylists()
                                    }
                                }
                                selectedPinnedPlaylist = null
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Hapus dari Profil",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Hapus dari Profil",
                            style = typo().bodyLarge,
                            color = Color.White
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
            // ── Playlist Section ────────────────────────────────────────
            AnimatedVisibility(visible = showPlaylist) {
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

                    if (ownPlaylists.none { it.id in pinnedPlaylistIds }) {
                        // Empty state
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                        ) {
                            Text(
                                text = "Tidak ada playlist",
                                style = typo().titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Orang lain bisa melihat musik yang kamu suka, membagikan playlist-mu, dan menyimpan playlist untuk didengar nanti.",
                                style = typo().bodySmall,
                                color = Color(0xFFB3B3B3),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .border(1.dp, Color.White, RoundedCornerShape(20.dp))
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { showAddPlaylistSheet = true }
                                    .padding(horizontal = 20.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Tambahkan playlist",
                                    style = typo().bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                        }
                    } else {
                        // Pinned Playlists list
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            ownPlaylists.filter { it.id in pinnedPlaylistIds }.forEach { playlist ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically, 
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedPinnedPlaylist = playlist }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Box(modifier = Modifier.size(60.dp).background(Color(0xFF282828), RoundedCornerShape(4.dp))) {
                                        if (playlist.thumbnail != null) {
                                            AsyncImage(
                                                model = playlist.thumbnail,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                painter = painterResource(Res.drawable.holder),
                                                contentDescription = null,
                                                tint = Color.Gray,
                                                modifier = Modifier.align(Alignment.Center).size(24.dp)
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
                                            text = "Publik • Kamu",
                                            style = typo().bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Box(
                                modifier = Modifier
                                    .border(1.dp, Color.White, RoundedCornerShape(20.dp))
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { showAddPlaylistSheet = true }
                                    .padding(horizontal = 20.dp, vertical = 8.dp)
                                    .align(Alignment.CenterHorizontally)
                            ) {
                                Text(
                                    text = "Kelola playlist",
                                    style = typo().bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // ── Recent Artists Section ──────────────────────────────────
            AnimatedVisibility(visible = showRecentArtists) {
                if (recentArtists.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp)
                    ) {
                        Text(
                            text = "Artis yang baru diputar",
                            style = typo().titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        // Scroll horizontal: tampilkan SEMUA artis yang diikuti (bukan cuma 5).
                        // recentArtists berasal dari Flow getFollowedArtists() sehingga otomatis
                        // ter-update (realtime) begitu user menambahkan/mengikuti artis baru.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            recentArtists.forEach { artist ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(80.dp)
                                        .clickable { navController.navigate(ArtistDestination(artist.channelId)) }
                                ) {
                                    Box(modifier = Modifier.size(72.dp).clip(CircleShape).background(Color(0xFF282828))) {
                                        if (artist.thumbnails != null) {
                                            AsyncImage(
                                                model = artist.thumbnails,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                painter = painterResource(Res.drawable.holder),
                                                contentDescription = null,
                                                tint = Color.Gray,
                                                modifier = Modifier.align(Alignment.Center).size(28.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = artist.name,
                                        style = typo().bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }

        if (isPreviewMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Seperti inilah profilmu terlihat oleh orang lain.",
                        style = typo().bodyMedium.copy(color = Color.Black, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Kembali",
                        style = typo().bodyMedium.copy(color = Color(0xFF404040), fontWeight = FontWeight.Bold),
                        modifier = Modifier.clickable { isPreviewMode = false }
                    )
                }
            }
        }

        // ── Edit Profile Overlay ──────────────────────────────────────
        AnimatedVisibility(
            visible = showEditProfile,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { showEditProfile = false }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
                        }
                        Text(
                            text = "Edit profil",
                            style = typo().titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                        )
                        Text(
                            text = "Simpan",
                            style = typo().bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold),
                            modifier = Modifier.clickable {
                                coroutineScope.launch {
                                    dataStoreManager.putString("AppProfileName", editName)
                                    showEditProfile = false

                                    try {
                                        val currentUser = supabase.auth.currentUserOrNull()
                                        if (currentUser != null) {
                                            var avatarUrlToSave = editImage
                                            Logger.d("ProfileEdit", "editImage value: $avatarUrlToSave")

                                            if (avatarUrlToSave.isNotEmpty() && !avatarUrlToSave.startsWith("http")) {
                                                Logger.d("ProfileEdit", "Compressing image...")
                                                val compressedBytes = compressImage(avatarUrlToSave)
                                                if (compressedBytes != null) {
                                                    Logger.d("ProfileEdit", "Compressed: ${compressedBytes.size} bytes")
                                                    val fileName = "${currentUser.id}.jpg"
                                                    val bucket = supabase.storage.from("avatars")

                                                    try {
                                                        bucket.upload(
                                                            path = fileName,
                                                            data = compressedBytes
                                                        ) { upsert = true }
                                                        val timestamp = Clock.System.now().toEpochMilliseconds()
                                                        avatarUrlToSave = bucket.publicUrl(fileName) + "?v=$timestamp"
                                                        Logger.d("ProfileEdit", "Upload success: $avatarUrlToSave")
                                                    } catch (uploadError: Exception) {
                                                        Logger.e("ProfileEdit", "Upload FAILED: ${uploadError.message}")
                                                        uploadError.printStackTrace()
                                                    }
                                                } else {
                                                    Logger.e("ProfileEdit", "compressImage returned null!")
                                                }
                                            }

                                            dataStoreManager.putString("AppProfileImage", avatarUrlToSave)

                                            supabase.auth.updateUser {
                                                data = buildJsonObject {
                                                    put("display_name", editName)
                                                    if (avatarUrlToSave.isNotEmpty()) {
                                                        put("avatar_url", avatarUrlToSave)
                                                    }
                                                }
                                            }
                                            try {
                                                supabase.postgrest["profiles"].upsert(
                                                    com.tan.domain.data.entities.UserProfile(
                                                        id = currentUser.id,
                                                        displayName = editName,
                                                        avatarUrl = avatarUrlToSave.ifEmpty { null }
                                                    )
                                                )
                                                userProfileViewModel.loadProfile(currentUser.id)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Logger.e("ProfileEdit", "Save profile error: ${e.message}")
                                        e.printStackTrace()
                                    }
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .align(Alignment.CenterHorizontally)
                            .clickable { photoPicker.launch() }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF535353))
                        ) {
                            if (editImage.isNotEmpty()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalPlatformContext.current)
                                        .data(editImage)
                                        .diskCachePolicy(CachePolicy.ENABLED)
                                        .crossfade(true)
                                        .build(),
                                    placeholder = painterResource(Res.drawable.holder),
                                    error = painterResource(Res.drawable.holder),
                                    contentDescription = "Avatar",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = "Default Avatar",
                                    modifier = Modifier.fillMaxSize().padding(32.dp),
                                    tint = Color.Gray
                                )
                            }
                        }
                        // Edit Icon (Pencil)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 4.dp, bottom = 4.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "Edit photo",
                                modifier = Modifier.size(20.dp),
                                tint = Color.Black
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    // Name Field
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Nama",
                            style = typo().bodyLarge.copy(color = Color.White, fontWeight = FontWeight.Bold),
                            modifier = Modifier.width(80.dp)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                        ) {
                            BasicTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                textStyle = typo().bodyLarge.copy(color = Color.White),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                cursorBrush = SolidColor(Color.White),
                                modifier = Modifier.fillMaxSize().padding(bottom = 8.dp)
                            )
                            // Underline
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color(0xFF535353))
                            )
                        }
                    }
                }
            }
        }

        // ── Privacy Bottom Sheet ──────────────────────────────────────
        if (showPrivacySheet) {
            ModalBottomSheet(
                onDismissRequest = { showPrivacySheet = false },
                sheetState = sheetState,
                containerColor = Color(0xFF282828)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Yang bisa dilihat orang lain",
                        style = typo().titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 24.dp)
                    )
                    
                    // Item 1: Pengikut dan mengikuti
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                            Text("Pengikut dan mengikuti", style = typo().bodyLarge.copy(color = Color.White, fontWeight = FontWeight.Bold))
                            Text("Di profilmu, orang bisa melihat siapa yang mengikutimu dan siapa yang kamu ikuti.", style = typo().bodySmall.copy(color = Color(0xFFB3B3B3)))
                        }
                        Switch(
                            checked = showFollowers ?: true,
                            onCheckedChange = { coroutineScope.launch { dataStoreManager.putString("privacy_show_followers", if (it) "TRUE" else "FALSE") } },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color(0xFFE0E0E0), uncheckedThumbColor = Color.Gray)
                        )
                    }
                    
                    // Item 2: Playlist
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                            Text("Playlist", style = typo().bodyLarge.copy(color = Color.White, fontWeight = FontWeight.Bold))
                            Text("Orang bisa melihat playlist yang kamu tambahkan ke profilmu.", style = typo().bodySmall.copy(color = Color(0xFFB3B3B3)))
                        }
                        Switch(
                            checked = showPlaylist ?: true,
                            onCheckedChange = { coroutineScope.launch { dataStoreManager.putString("privacy_show_playlist", if (it) "TRUE" else "FALSE") } },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color(0xFFE0E0E0), uncheckedThumbColor = Color.Gray)
                        )
                    }
                    
                    // Item 3: Artis yang baru diputar
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                            Text("Artis yang baru diputar", style = typo().bodyLarge.copy(color = Color.White, fontWeight = FontWeight.Bold))
                            Text("Orang bisa melihat siapa yang baru-baru ini kamu dengarkan di profilmu.", style = typo().bodySmall.copy(color = Color(0xFFB3B3B3)))
                        }
                        Switch(
                            checked = showRecentArtists ?: true,
                            onCheckedChange = { coroutineScope.launch { dataStoreManager.putString("privacy_show_recent_artists", if (it) "TRUE" else "FALSE") } },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color(0xFFE0E0E0), uncheckedThumbColor = Color.Gray)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // ── Share Profile Bottom Sheet ────────────────────────────────
        if (showShareProfileSheet) {
            ModalBottomSheet(
                onDismissRequest = { showShareProfileSheet = false },
                sheetState = sheetState,
                containerColor = Color(0xFF1E1E1E)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Custom Profile Card
                    Box(
                        modifier = Modifier
                            .width(280.dp)
                            .wrapContentHeight()
                            .defaultMinSize(minHeight = 370.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(shareCardBgColor)
                            .drawWithContent {
                                graphicsLayer.record {
                                    this@drawWithContent.drawContent()
                                }
                                drawContent()
                            }
                            .padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))
                            UserAvatar(
                                imageUrl = appProfileImage,
                                name = appProfileName,
                                modifier = Modifier.size(160.dp),
                                textStyle = typo().displayMedium
                            )
                            
                            Spacer(modifier = Modifier.height(20.dp))
                            
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = if (appProfileName.isNullOrEmpty()) "Pengguna" else appProfileName!!,
                                    style = typo().headlineSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Profil di Gratify",
                                    style = typo().bodyMedium.copy(color = Color(0xFFB3B3B3))
                                )

                                val shareSongTitle = nowPlayingData.nowPlayingTitle.takeIf { it.isNotEmpty() }
                                    ?: userProfileState.profile?.nowPlayingTitle
                                val shareSongArtist = nowPlayingData.artistName.takeIf { it.isNotEmpty() }
                                    ?: userProfileState.profile?.nowPlayingArtist ?: "Gratify Music"
                                val lyricsSnippet = nowPlayingData.lyricsData?.lyrics?.lines?.takeIf { it.isNotEmpty() }?.let { lines ->
                                    val startIdx = maxOf(0, lines.size / 2 - 1)
                                    lines.drop(startIdx).take(2).joinToString("\n") { it.words }
                                }

                                if (!shareSongTitle.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0x33000000))
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Rounded.PlayArrow,
                                                contentDescription = null,
                                                tint = Color(0xFF1DB954),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = shareSongTitle,
                                                    style = typo().labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White),
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = shareSongArtist,
                                                    style = typo().bodySmall.copy(fontSize = 10.sp, color = Color(0xFFCCCCCC)),
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        val displayLyrics = selectedLyricsSnippet ?: lyricsSnippet
                                        if (displayLyrics != null) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "\"$displayLyrics\"",
                                                style = typo().bodySmall.copy(
                                                    fontSize = 11.sp, 
                                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, 
                                                    color = Color(0xFFEBEBEB)
                                                ),
                                                maxLines = 3,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                modifier = Modifier
                                                    .padding(start = 28.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .clickable {
                                                        val lines = nowPlayingData.lyricsData?.lyrics?.lines
                                                        if (!lines.isNullOrEmpty()) {
                                                            showLyricsSelectionSheet = true
                                                        }
                                                    }
                                                    .padding(4.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        painter = painterResource(Res.drawable.circle_app_icon),
                                        contentDescription = "Gratify Logo",
                                        modifier = Modifier.size(24.dp).clip(CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Gratify",
                                        style = typo().titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Color picker dots
                    val colors = listOf(Color(0xFF2E3B4E), Color(0xFF4A3B42), Color(0xFF334A3E), Color(0xFF454545))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        colors.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (shareCardBgColor == color) 2.dp else 0.dp,
                                        color = if (shareCardBgColor == color) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { shareCardBgColor = color }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Share targets scroll row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Salin Link
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                            val currentId = userProfileState.profile?.id ?: supabase.auth.currentUserOrNull()?.id ?: ""
                            val url = "https://gratify.org/app/profile/$currentId"
                            shareUrl("Bagikan Profil", url)
                            showShareProfileSheet = false
                        }) {
                            Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(Color(0xFF333333)), contentAlignment = Alignment.Center) {
                                Icon(imageVector = Icons.Rounded.Link, contentDescription = "Salin", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Salin link", style = typo().bodySmall.copy(color = Color.White))
                        }
                        
                        // WhatsApp
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                            coroutineScope.launch {
                                try {
                                    val bitmap = graphicsLayer.toImageBitmap()
                                    shareImage(bitmap, "com.whatsapp")
                                    showShareProfileSheet = false
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }) {
                            Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(Color(0xFF25D366)), contentAlignment = Alignment.Center) {
                                Icon(imageVector = Icons.Rounded.Share, contentDescription = "WA", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("WhatsApp", style = typo().bodySmall.copy(color = Color.White))
                        }
                        
                        // Instagram
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                            coroutineScope.launch {
                                try {
                                    val bitmap = graphicsLayer.toImageBitmap()
                                    shareImage(bitmap, "com.instagram.android")
                                    showShareProfileSheet = false
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }) {
                            Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF833AB4), Color(0xFFFD1D1D), Color(0xFFF56040)))), contentAlignment = Alignment.Center) {
                                Icon(imageVector = Icons.Rounded.Share, contentDescription = "IG", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Instagram", style = typo().bodySmall.copy(color = Color.White))
                        }
                        
                        // Lainnya
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                            coroutineScope.launch {
                                try {
                                    val bitmap = graphicsLayer.toImageBitmap()
                                    shareImage(bitmap, null)
                                    showShareProfileSheet = false
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }) {
                            Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(Color(0xFF333333)), contentAlignment = Alignment.Center) {
                                Icon(imageVector = Icons.Rounded.Share, contentDescription = "Lainnya", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Lainnya", style = typo().bodySmall.copy(color = Color.White))
                        }
                    }
                }
            }
        }

        // ── More Options Bottom Sheet ─────────────────────────────────
        // ── Lyrics Selection Bottom Sheet ────────────────────────────────────────
        if (showLyricsSelectionSheet) {
            val lines = nowPlayingData.lyricsData?.lyrics?.lines
            if (!lines.isNullOrEmpty()) {
                ModalBottomSheet(
                    onDismissRequest = { showLyricsSelectionSheet = false },
                    sheetState = sheetState,
                    containerColor = Color(0xFF1E1E1E)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.7f)
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = "Pilih Lirik untuk Profil",
                            style = typo().titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
                            modifier = Modifier.padding(bottom = 16.dp, top = 8.dp)
                        )
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            itemsIndexed(lines) { index, line ->
                                val snippet = if (index + 1 < lines.size) {
                                    "${line.words}\n${lines[index + 1].words}"
                                } else {
                                    line.words
                                }
                                
                                val defaultLyrics = lines.let { l ->
                                    val startIdx = maxOf(0, l.size / 2 - 1)
                                    l.drop(startIdx).take(2).joinToString("\n") { it.words }
                                }
                                
                                val isSelected = (selectedLyricsSnippet == snippet) || (selectedLyricsSnippet == null && defaultLyrics == snippet)
                                
                                Text(
                                    text = snippet,
                                    style = typo().bodyMedium.copy(
                                        color = if (isSelected) Color(0xFF1DB954) else Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedLyricsSnippet = snippet
                                            showLyricsSelectionSheet = false
                                        }
                                        .padding(vertical = 14.dp, horizontal = 8.dp)
                                )
                                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                            }
                            item {
                                Spacer(modifier = Modifier.height(32.dp))
                            }
                        }
                    }
                }
            }
        }

        if (showMoreOptionsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showMoreOptionsSheet = false },
                sheetState = sheetState,
                containerColor = Color(0xFF282828)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    // Top user info
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF535353))
                        ) {
                            if (appProfileImage?.isNotEmpty() == true) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalPlatformContext.current)
                                        .data(appProfileImage)
                                        .diskCachePolicy(CachePolicy.ENABLED)
                                        .crossfade(true)
                                        .build(),
                                    placeholder = painterResource(Res.drawable.holder),
                                    error = painterResource(Res.drawable.holder),
                                    contentDescription = "Profile Mini",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = if (appProfileName.isNullOrEmpty()) "Pengguna" else appProfileName!!,
                            style = typo().titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                        )
                    }

                    // Divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFF3E3E3E))
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Option 1: Bagikan
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showMoreOptionsSheet = false
                                showShareProfileSheet = true
                            }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Bagikan",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Bagikan",
                            style = typo().bodyLarge.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
                        )
                    }

                    // Option 2: Lihat pratinjau profil
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showMoreOptionsSheet = false
                                isPreviewMode = true
                            }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info, // Assuming visibility icon is available, falling back if not
                            contentDescription = "Lihat pratinjau profil",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Lihat pratinjau profil",
                            style = typo().bodyLarge.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
                        )
                    }

                    // Option 3: Tampilkan Kode Gratify
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showMoreOptionsSheet = false
                                showCodeOverlay = true
                            }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Menu, // Using Menu or Info as a placeholder for code icon
                            contentDescription = "Tampilkan Kode Gratify",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Tampilkan Kode Gratify",
                            style = typo().bodyLarge.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
                        )
                    }

                    // Option 4: Scan Kode Teman
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showMoreOptionsSheet = false
                                scanLauncher()
                            }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = "Scan Kode Teman",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Scan Kode Teman",
                            style = typo().bodyLarge.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }

        // ── Code Overlay ───────────────────────────────────────────────
        AnimatedVisibility(
            visible = showCodeOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
            ) {
                IconButton(
                    onClick = { showCodeOverlay = false },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .windowInsetsPadding(WindowInsets.statusBars)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(280.dp)
                            .background(Color(0xFFDCE2F0), RoundedCornerShape(16.dp))
                            .padding(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Avatar + Username
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color.Gray)
                                ) {
                                    if (appProfileImage?.isNotEmpty() == true) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalPlatformContext.current)
                                                .data(appProfileImage)
                                                .diskCachePolicy(CachePolicy.ENABLED)
                                                .crossfade(true)
                                                .build(),
                                            placeholder = painterResource(Res.drawable.holder),
                                            error = painterResource(Res.drawable.holder),
                                            contentDescription = "Profile Code Avatar",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Person,
                                            contentDescription = "Default Avatar",
                                            modifier = Modifier.fillMaxSize().padding(8.dp),
                                            tint = Color.DarkGray
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (appProfileName?.isNotEmpty() == true) appProfileName!! else "Pengguna Gratify",
                                        style = typo().titleMedium.copy(color = Color.Black, fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Gratify Profile ID",
                                        style = typo().labelSmall.copy(color = Color.DarkGray)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Real QR Code
                            val currentQrId = userProfileState.profile?.id ?: supabase.auth.currentUserOrNull()?.id ?: ""
                            val qrContent = "gratify://profile/$currentQrId"

                            Box(
                                modifier = Modifier
                                    .size(180.dp)
                                    .background(Color.White, RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                QrCodeView(
                                    content = qrContent,
                                    modifier = Modifier.fillMaxSize(),
                                    tint = Color.Black
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.QrCodeScanner,
                                    contentDescription = "QR Logo",
                                    tint = Color.DarkGray,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Pindai untuk berteman & lihat playlist",
                                    style = typo().labelSmall.copy(color = Color.DarkGray, fontWeight = FontWeight.Medium)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Scan button outside card
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { scanLauncher() }
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = "Scan Kode Teman",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Scan Kode Teman",
                            style = typo().bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}
