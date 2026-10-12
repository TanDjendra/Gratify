package com.tan.gratify.ui.screen.home

import com.tan.domain.utils.uploadedProfileAvatar
import com.tan.domain.utils.parseProfileUserId
import com.tan.domain.utils.profileUrl

import com.tan.gratify.ui.theme.GratifyColors
import com.tan.gratify.ui.theme.GratifyShapes

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
import androidx.compose.foundation.layout.widthIn
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
import com.tan.gratify.expect.copyToClipboard
import gratify.composeapp.generated.resources.circle_app_icon
import com.tan.gratify.expect.ui.QrCodeView
import com.tan.gratify.expect.ui.rememberQrScannerLauncher
import com.tan.gratify.ui.navigation.destination.social.UserProfileDestination
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import gratify.composeapp.generated.resources.baseline_arrow_back_ios_new_24
import gratify.composeapp.generated.resources.baseline_settings_24
import gratify.composeapp.generated.resources.baseline_more_vert_24
import gratify.composeapp.generated.resources.holder

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
    val profileEditor: com.tan.gratify.viewModel.CreateProfileViewModel = koinViewModel()
    val editorState by profileEditor.uiState.collectAsStateWithLifecycle()
    val userProfileViewModel: UserProfileViewModel = koinViewModel()
    val sharedViewModel: com.tan.gratify.viewModel.SharedViewModel = koinInject()
    
    val userProfileState by userProfileViewModel.state.collectAsStateWithLifecycle()
    val nowPlayingData by sharedViewModel.nowPlayingScreenData.collectAsStateWithLifecycle()
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val sessionStatus by supabase.auth.sessionStatus.collectAsStateWithLifecycle()
    LaunchedEffect(navBackStackEntry, sessionStatus) {
        val currentUserId = supabase.auth.currentUserOrNull()?.id
        if (currentUserId != null) {
            userProfileViewModel.loadProfile(currentUserId)
        } else userProfileViewModel.clearProfile()
    }

    val localPlaylistsFlow = remember(localPlaylistRepository) { localPlaylistRepository.getAllLocalPlaylists() }
    val localPlaylists by localPlaylistsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    // Hanya playlist BUATAN SENDIRI yang boleh dipajang di profil. Playlist hasil menyimpan/
    // menambah dari orang lain punya sourceSharedPlaylistId != null → dikecualikan dari pajangan.
    val ownPlaylists = localPlaylists.filter { it.sourceSharedPlaylistId.isNullOrBlank() }
    val artistsFlow = remember(artistRepository) { artistRepository.observeFollowedArtists() }
    val loadedArtists by artistsFlow.collectAsStateWithLifecycle(initialValue = null)
    val libraryOwnerFlow = remember(dataStoreManager) { dataStoreManager.getString("last_synced_user_id") }
    val libraryOwner by libraryOwnerFlow.collectAsStateWithLifecycle(initialValue = null)
    val recentArtists = loadedArtists.orEmpty()
    LaunchedEffect(loadedArtists, libraryOwner, sessionStatus) {
        if (libraryOwner == supabase.auth.currentUserOrNull()?.id)
            loadedArtists?.let { userProfileViewModel.syncTopArtists(it) }
    }
    val appProfileName by dataStoreManager.getString("AppProfileName").collectAsStateWithLifecycle(initialValue = "")
    val appProfileImage by dataStoreManager.getString("AppProfileImage").collectAsStateWithLifecycle(initialValue = "")

    val currentOwner = supabase.auth.currentUserOrNull()?.id
    val cacheBelongsToOwner = currentOwner != null && libraryOwner == currentOwner
    val displayImage = if (userProfileState.profile != null) userProfileState.profile?.avatarUrl.uploadedProfileAvatar() else appProfileImage.uploadedProfileAvatar().takeIf { cacheBelongsToOwner }
    val displayName = userProfileState.profile?.displayName?.takeIf { it.isNotBlank() } ?: appProfileName?.takeIf { it.isNotBlank() && cacheBelongsToOwner } ?: "Pengguna"

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    var showEditProfile by rememberSaveable { mutableStateOf(false) }
    var avatarSelectionOwner by rememberSaveable { mutableStateOf<String?>(null) }

    
    var showPrivacySheet by remember { mutableStateOf(false) }
    var showMoreOptionsSheet by remember { mutableStateOf(false) }
    var showShareProfileSheet by remember { mutableStateOf(false) }
    var showLyricsSelectionSheet by remember { mutableStateOf(false) }
    var selectedLyricsSnippet by remember { mutableStateOf<String?>(null) }
    var shareCardBgColor by remember { mutableStateOf(Color(0xFF2E3B4E)) }
    val graphicsLayer = rememberGraphicsLayer()
    var isPreviewMode by remember { mutableStateOf(false) }
    var showCodeOverlay by remember { mutableStateOf(false) }
    com.tan.gratify.ui.component.LayerBackHandler(!showEditProfile && (showCodeOverlay || isPreviewMode)) {
        when {
            showCodeOverlay -> showCodeOverlay = false
            isPreviewMode -> isPreviewMode = false
        }
    }

    val scanLauncher = rememberQrScannerLauncher { scannedValue ->
        showCodeOverlay = false
        val scannedId = parseProfileUserId(scannedValue)
        if (scannedId != null) {
            navController.navigate(UserProfileDestination(userId = scannedId))
        } else {
            userProfileViewModel.makeToast("QR Code tidak memuat ID Profil yang valid.")
        }
    }


    var showAddPlaylistSheet by remember { mutableStateOf(false) }
    var selectedPinnedPlaylist by remember { mutableStateOf<LocalPlaylistEntity?>(null) }
    var pinnedPlaylistIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var publicPinnedPlaylistIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var playlistUpdatePending by remember { mutableStateOf(false) }
    var profilePlaylistError by remember { mutableStateOf<String?>(null) }
    // The cloud identity is stable across devices; local row IDs are not.
    LaunchedEffect(currentOwner) {
        pinnedPlaylistIds = emptySet()
        publicPinnedPlaylistIds = emptySet()
        profilePlaylistError = null
        showAddPlaylistSheet = false
        selectedPinnedPlaylist = null
        showPrivacySheet = false
        showMoreOptionsSheet = false
        showShareProfileSheet = false
        showCodeOverlay = false
        isPreviewMode = false
        if (editorState.ownerId != currentOwner) showEditProfile = false
        if (avatarSelectionOwner != currentOwner) avatarSelectionOwner = null
    }
    val refreshPinnedPlaylists: suspend () -> Unit = {
        val uid = supabase.auth.currentUserOrNull()?.id
        if (uid == null) pinnedPlaylistIds = emptySet()
        else try {
            val result = kotlinx.coroutines.withTimeout(20_000) {
                socialRepository.getUserProfilePlaylists(uid).first().getOrThrow()
            }
            if (supabase.auth.currentUserOrNull()?.id == uid) {
                val rows = localPlaylistRepository.getAllLocalPlaylists().first()
                if (supabase.auth.currentUserOrNull()?.id == uid) {
                    pinnedPlaylistIds = com.tan.gratify.ui.utils.profilePlaylistIds(rows, result)
                    publicPinnedPlaylistIds = com.tan.gratify.ui.utils.profilePlaylistIds(rows, result.filter { it.isPublic })
                    profilePlaylistError = null
                }
            }
        } catch (cancelled: CancellationException) {
            if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
            if (supabase.auth.currentUserOrNull()?.id == uid) profilePlaylistError = "Daftar playlist belum dapat dimuat. Coba lagi."
        } catch (_: Exception) {
            if (supabase.auth.currentUserOrNull()?.id == uid) profilePlaylistError = "Daftar playlist belum dapat dimuat. Periksa koneksi dan coba lagi."
        }
    }
    fun updateProfilePlaylists(ids: Set<Long>, visible: Boolean) {
        if (playlistUpdatePending || ids.isEmpty() || !cacheBelongsToOwner) return
        val uid = supabase.auth.currentUserOrNull()?.id ?: return
        playlistUpdatePending = true
        coroutineScope.launch {
            try {
                check(supabase.auth.currentUserOrNull()?.id == uid) { "Account changed" }
                for (id in ids) {
                    check(supabase.auth.currentUserOrNull()?.id == uid) { "Account changed" }
                    kotlinx.coroutines.withTimeout(30_000) {
                    if (visible) socialRepository.syncUpPlaylist(id, uid, isPublic = null, isProfilePinned = true).first().getOrThrow()
                    else socialRepository.setPlaylistProfilePinned(id, uid, pinned = false).first().getOrThrow()
                    }
                    if (supabase.auth.currentUserOrNull()?.id != uid) return@launch
                    pinnedPlaylistIds = if (visible) pinnedPlaylistIds + id else pinnedPlaylistIds - id
                }
                // Keep the confirmed selection even if the subsequent read fails.
                refreshPinnedPlaylists()
                showAddPlaylistSheet = false
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (e !is kotlinx.coroutines.TimeoutCancellationException) throw e
                userProfileViewModel.makeToast("Playlist belum tersimpan. Coba lagi.")
            }
            catch (_: Exception) {
                userProfileViewModel.makeToast("Playlist belum tersimpan. Periksa koneksi lalu coba lagi.")
            } finally { playlistUpdatePending = false }
        }
    }
    LaunchedEffect(navBackStackEntry, localPlaylists, sessionStatus) {
        if (!playlistUpdatePending) refreshPinnedPlaylists()
    }
    val sheetState = com.tan.gratify.ui.component.rememberBusyModalSheetState(playlistUpdatePending)
    
    val showFollowersFlow = remember { dataStoreManager.getString("privacy_show_followers") }
    val showPlaylistFlow = remember { dataStoreManager.getString("privacy_show_playlist") }
    val showRecentArtistsFlow = remember { dataStoreManager.getString("privacy_show_recent_artists") }
    
    val showFollowersStr by showFollowersFlow.collectAsStateWithLifecycle(initialValue = "FALSE")
    val showPlaylistStr by showPlaylistFlow.collectAsStateWithLifecycle(initialValue = "FALSE")
    val showRecentArtistsStr by showRecentArtistsFlow.collectAsStateWithLifecycle(initialValue = "FALSE")
    
    val showFollowers = userProfileState.profile?.showFollowers ?: (showFollowersStr == "TRUE" && cacheBelongsToOwner)
    val showPlaylist = userProfileState.profile?.showPlaylists ?: (showPlaylistStr == "TRUE" && cacheBelongsToOwner)
    val showRecentArtists = userProfileState.profile?.showRecentArtists ?: (showRecentArtistsStr == "TRUE" && cacheBelongsToOwner)


    val photoPicker = photoPickerResult(com.tan.gratify.expect.ui.PhotoCropShape.CIRCLE) { uri ->
        if (profileEditor.applySelectedAvatar(avatarSelectionOwner, uri)) showEditProfile = true
        avatarSelectionOwner = null
    }

    Box(modifier = Modifier.fillMaxSize().background(GratifyColors.Background), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp).fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            fun countLabel(count: Int, noun: String): String = when {
                isPreviewMode && !showFollowers -> "$noun disembunyikan"
                supabase.auth.currentUserOrNull() == null -> "Masuk untuk melihat ${noun.lowercase()}"
                userProfileState.isLoading || userProfileState.countsLoading -> "Memuat ${noun.lowercase()}…"
                userProfileState.countsError != null || userProfileState.profileError != null -> "$noun belum dimuat"
                else -> "$count ${noun.lowercase()}"
            }
            com.tan.gratify.ui.component.ProfileIdentityHeader(
                name = displayName, imageUrl = displayImage,
                followersLabel = countLabel(userProfileState.followersCount, "Pengikut"),
                followingLabel = countLabel(userProfileState.followingCount, "Mengikuti"),
                countsClickable = userProfileState.profile != null && (!isPreviewMode || showFollowers),
                onBack = { navController.popBackStack() },
                onFollowers = { supabase.auth.currentUserOrNull()?.id?.let { navController.navigate(FollowListDestination(it, 0)) } },
                onFollowing = { supabase.auth.currentUserOrNull()?.id?.let { navController.navigate(FollowListDestination(it, 1)) } })

            (userProfileState.profileError ?: userProfileState.countsError)?.let { error ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Text(error, color = GratifyColors.TextSecondary)
                    androidx.compose.material3.TextButton(onClick = {
                        supabase.auth.currentUserOrNull()?.id?.let { userProfileViewModel.loadProfile(it) }
                    }) { Text("Coba lagi") }
                }
            }

            com.tan.gratify.ui.component.ProfileActionsContent(
                editEnabled = currentOwner != null && !userProfileState.isLoading && userProfileState.profileError == null,
                privacyEnabled = currentOwner != null, preview = isPreviewMode,
                onEdit = {
                    profileEditor.beginEditing(currentOwner, displayName, displayImage)
                    showEditProfile = true
                }, onPrivacy = { showPrivacySheet = true }, onMore = { showMoreOptionsSheet = true },
            )
            if (!showEditProfile && editorState.saveSuccess && editorState.ownerId == currentOwner) {
                com.tan.gratify.ui.component.ProfileSaveFeedback(editorState,
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
        // ── Add Playlist Sheet ────────────────────────────────────────
        if (showAddPlaylistSheet) {
            ModalBottomSheet(
                onDismissRequest = { if (!playlistUpdatePending) showAddPlaylistSheet = false },
                sheetState = sheetState,
                containerColor = GratifyColors.Surface,
                dragHandle = null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Tambahkan playlist",
                                style = typo().titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (playlistUpdatePending) "Menyimpan pilihan…" else "Playlist privat yang dipajang hanya terlihat oleh Anda. Memajang atau melepas playlist tidak mengubah privasinya.",
                                style = typo().bodySmall,
                                color = GratifyColors.TextSecondary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .border(1.dp, GratifyColors.Outline, GratifyShapes.large)
                                .clip(GratifyShapes.large)
                                .clickable(enabled = !playlistUpdatePending) {
                                    updateProfilePlaylists(ownPlaylists.map { it.id }.toSet(), true)
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
                                color = GratifyColors.TextSecondary,
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
                                            .background(GratifyColors.SurfaceRaised, GratifyShapes.extraSmall)
                                    ) {
                                        if (com.tan.gratify.ui.component.isAutoAssignedOrNullThumbnail(playlist.thumbnail,playlist.youtubePlaylistId) && !playlist.tracks.isNullOrEmpty()) {
                                            com.tan.gratify.ui.component.PlaylistCollageThumbnail(playlist.tracks.orEmpty(),Modifier.fillMaxSize(),playlist.title)
                                        } else if (playlist.thumbnail != null) {
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
                                                tint = GratifyColors.TextSecondary,
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
                                            text = if (!isAdded) "Belum dipajang" else if (playlist.id !in publicPinnedPlaylistIds) "Dipajang • Privat • Hanya Anda" else if (showPlaylist) "Dipajang • Publik" else "Dipajang • Isi profil disembunyikan",
                                            style = typo().bodySmall,
                                            color = GratifyColors.TextSecondary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))

                                    if (isAdded) {
                                        Box(
                                            modifier = Modifier
                                                .border(1.dp, Color.Transparent, GratifyShapes.large)
                                                .background(GratifyColors.TextPrimary, GratifyShapes.large)
                                                .clickable(enabled = !playlistUpdatePending) {
                                                    updateProfilePlaylists(setOf(playlist.id), false)
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
                                                .border(1.dp, Color.Transparent, GratifyShapes.large)
                                                .background(Color.White, GratifyShapes.large)
                                                .clickable(enabled = !playlistUpdatePending) {
                                                    updateProfilePlaylists(setOf(playlist.id), true)
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
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = GratifyColors.Surface,
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
                                updateProfilePlaylists(setOf(playlist.id), false)
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
            Column {
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

                    if (profilePlaylistError != null && !isPreviewMode) {
                        Text(profilePlaylistError.orEmpty(), style = typo().bodySmall, color = GratifyColors.TextSecondary)
                        androidx.compose.material3.TextButton(onClick = { coroutineScope.launch { refreshPinnedPlaylists() } }) { Text("Coba lagi") }
                    }
                    if (isPreviewMode && !showPlaylist) {
                        com.tan.gratify.ui.component.ProfileContentPlaceholder("", "Playlist disembunyikan oleh pemilik profil")
                    } else if (ownPlaylists.none { it.id in pinnedPlaylistIds && (!isPreviewMode || it.id in publicPinnedPlaylistIds) }) {
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
                                text = "Pilih playlist untuk ditampilkan di profil. Kamu tetap dapat melihatnya saat visibilitas untuk orang lain dimatikan.",
                                style = typo().bodySmall,
                                color = GratifyColors.TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .border(1.dp, Color.White, GratifyShapes.large)
                                    .clip(GratifyShapes.large)
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
                            ownPlaylists.filter { it.id in pinnedPlaylistIds && (!isPreviewMode || it.id in publicPinnedPlaylistIds) }.forEach { playlist ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically, 
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedPinnedPlaylist = playlist }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Box(modifier = Modifier.size(60.dp).background(GratifyColors.SurfaceRaised, GratifyShapes.extraSmall)) {
                                        if (com.tan.gratify.ui.component.isAutoAssignedOrNullThumbnail(playlist.thumbnail,playlist.youtubePlaylistId) && !playlist.tracks.isNullOrEmpty()) {
                                            com.tan.gratify.ui.component.PlaylistCollageThumbnail(playlist.tracks.orEmpty(),Modifier.fillMaxSize(),playlist.title)
                                        } else if (playlist.thumbnail != null) {
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
                                                tint = GratifyColors.TextSecondary,
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
                                            text = if (playlist.id !in publicPinnedPlaylistIds) "Dipajang • Privat • Hanya Anda" else if (showPlaylist) "Dipajang • Publik" else "Dipajang • Isi profil disembunyikan",
                                            style = typo().bodySmall,
                                            color = GratifyColors.TextSecondary
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Box(
                                modifier = Modifier
                                    .border(1.dp, Color.White, GratifyShapes.large)
                                    .clip(GratifyShapes.large)
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
            Column {
                if ((isPreviewMode && !showRecentArtists) || recentArtists.isEmpty()) {
                    com.tan.gratify.ui.component.ProfileContentPlaceholder("Artis yang diikuti",
                        if (isPreviewMode && !showRecentArtists) "Aktivitas disembunyikan oleh pemilik profil" else "Artis pilihanmu akan muncul di sini.",
                        Modifier.padding(horizontal = 20.dp))
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp)
                    ) {
                        Text(
                            text = "Artis yang diikuti",
                            style = typo().titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        // Scroll horizontal: tampilkan SEMUA artis yang diikuti (bukan cuma 5).
                        // recentArtists berasal dari Flow observeFollowedArtists() sehingga otomatis
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
                                    Box(modifier = Modifier.size(72.dp).clip(CircleShape).background(GratifyColors.SurfaceRaised)) {
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
                                                tint = GratifyColors.TextSecondary,
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

            Spacer(modifier = Modifier.height(24.dp))
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

        // Editing uses the same guarded submission as profile creation.
        if (showEditProfile) {
            com.tan.gratify.ui.component.ProfileEditorContent(
                editorState, creating = false, onNameChange = profileEditor::updateDisplayName,
                onChoosePhoto = {
                    avatarSelectionOwner = editorState.ownerId
                    photoPicker.launch()
                }, onRemovePhoto = { profileEditor.updateAvatarPath("") },
                onSave = profileEditor::saveProfile, onClose = { showEditProfile = false },
                photoSelectionActive = photoPicker.isActive,
            )
        }
        LaunchedEffect(editorState.saveSuccess, showEditProfile) {
            if (showEditProfile && editorState.saveSuccess) {
                editorState.ownerId?.let {
                    userProfileViewModel.onProfileSaved(it, editorState.displayName, editorState.avatarPath)
                }
                showEditProfile = false
            }
        }

        if (showPrivacySheet) {
            ModalBottomSheet(onDismissRequest = { showPrivacySheet = false },
                sheetState = sheetState, containerColor = GratifyColors.SurfaceRaised) {
                com.tan.gratify.ui.component.ProfilePrivacyContent(
                    followers = showFollowers, playlists = showPlaylist, artists = showRecentArtists,
                    ready = userProfileState.privacyReady, saving = userProfileState.privacySaving,
                    error = userProfileState.privacyError,
                    onChange = { key, value -> coroutineScope.launch { userProfileViewModel.setProfilePrivacy(key, value) } },
                    onRetry = { currentOwner?.let { userProfileViewModel.loadProfile(it) } },
                )
            }
        }
        // ── Share Profile Bottom Sheet ────────────────────────────────
        if (showShareProfileSheet) {
            ModalBottomSheet(
                onDismissRequest = { showShareProfileSheet = false },
                sheetState = sheetState,
                containerColor = GratifyColors.Surface
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
                            .clip(GratifyShapes.large)
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
                                imageUrl = displayImage,
                                name = displayName,
                                modifier = Modifier.size(160.dp),
                                textStyle = typo().displayMedium
                            )
                            
                            Spacer(modifier = Modifier.height(20.dp))
                            
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = displayName,
                                    style = typo().headlineSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Profil di Gratify",
                                    style = typo().bodyMedium.copy(color = GratifyColors.TextSecondary)
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
                                            .clip(GratifyShapes.medium)
                                            .background(Color(0x33000000))
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Rounded.PlayArrow,
                                                contentDescription = null,
                                                tint = GratifyColors.Accent,
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
                                                    style = typo().bodySmall.copy(fontSize = 10.sp, color = GratifyColors.TextSecondary),
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
                                                    .clip(GratifyShapes.extraSmall)
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
                            val currentId = parseProfileUserId(supabase.auth.currentUserOrNull()?.id.orEmpty())
                            if (currentId != null) {
                                try {
                                    copyToClipboard("Profil Gratify", profileUrl(currentId))
                                    userProfileViewModel.makeToast("Tautan profil disalin.")
                                } catch (_: Exception) {
                                    userProfileViewModel.makeToast("Tautan belum dapat disalin. Coba lagi.")
                                    return@clickable
                                }
                            } else userProfileViewModel.makeToast("Masuk untuk membagikan profil.")
                            showShareProfileSheet = false
                        }) {
                            Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(GratifyColors.Divider), contentAlignment = Alignment.Center) {
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
                                    if (e is CancellationException) throw e
                                    userProfileViewModel.makeToast("Kartu profil belum dapat dibagikan. Coba lagi atau gunakan Salin link.")
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
                                    if (e is CancellationException) throw e
                                    userProfileViewModel.makeToast("Kartu profil belum dapat dibagikan. Coba lagi atau gunakan Salin link.")
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
                                    if (e is CancellationException) throw e
                                    userProfileViewModel.makeToast("Kartu profil belum dapat dibagikan. Coba lagi atau gunakan Salin link.")
                                }
                            }
                        }) {
                            Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(GratifyColors.Divider), contentAlignment = Alignment.Center) {
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
                    containerColor = GratifyColors.Surface
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
                                        color = if (isSelected) GratifyColors.Accent else Color.White,
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
                containerColor = GratifyColors.SurfaceRaised
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
                                .background(GratifyColors.Outline)
                        ) {
                            if (displayImage?.isNotEmpty() == true) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalPlatformContext.current)
                                        .data(displayImage)
                                        .diskCachePolicy(CachePolicy.ENABLED)
                                        .crossfade(true)
                                        .build(),
                                    placeholder = painterResource(Res.drawable.holder),
                                    error = painterResource(Res.drawable.holder),
                                    fallback = painterResource(Res.drawable.holder),
                                    contentDescription = "Profile Mini",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = displayName,
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
                            .background(Color(0xFFDCE2F0), GratifyShapes.medium)
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
                                        .background(GratifyColors.TextSecondary)
                                ) {
                                    if (displayImage?.isNotEmpty() == true) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalPlatformContext.current)
                                                .data(displayImage)
                                                .diskCachePolicy(CachePolicy.ENABLED)
                                                .crossfade(true)
                                                .build(),
                                            placeholder = painterResource(Res.drawable.holder),
                                            error = painterResource(Res.drawable.holder),
                                            fallback = painterResource(Res.drawable.holder),
                                            contentDescription = "Profile Code Avatar",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Person,
                                            contentDescription = "Default Avatar",
                                            modifier = Modifier.fillMaxSize().padding(8.dp),
                                            tint = GratifyColors.SurfaceHighest
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = displayName,
                                        style = typo().titleMedium.copy(color = Color.Black, fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Gratify Profile ID",
                                        style = typo().labelSmall.copy(color = GratifyColors.SurfaceHighest)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Real QR Code
                            val currentQrId = parseProfileUserId(supabase.auth.currentUserOrNull()?.id.orEmpty())
                            val qrContent = currentQrId?.let(::profileUrl)

                            Box(
                                modifier = Modifier
                                    .size(180.dp)
                                    .background(Color.White, GratifyShapes.small)
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (qrContent != null) QrCodeView(
                                    content = qrContent,
                                    modifier = Modifier.fillMaxSize(),
                                    tint = Color.Black
                                ) else Text("Masuk untuk menampilkan QR profil.", color = Color.Black, textAlign = TextAlign.Center)
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.QrCodeScanner,
                                    contentDescription = "QR Logo",
                                    tint = GratifyColors.SurfaceHighest,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Pindai untuk berteman & lihat playlist",
                                    style = typo().labelSmall.copy(color = GratifyColors.SurfaceHighest, fontWeight = FontWeight.Medium)
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
