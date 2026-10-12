package com.tan.gratify.ui.screen

import com.tan.gratify.ui.theme.GratifyShapes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.SnackbarHostState
import com.tan.gratify.ui.component.InAppNotificationHost
import com.tan.gratify.ui.component.InAppNotificationVisuals
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import coil3.toUri
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.tan.domain.data.player.GenericMediaItem
import com.tan.domain.manager.DataStoreManager
import com.tan.gratify.Platform
import com.tan.gratify.expect.Orientation
import com.tan.gratify.expect.currentOrientation
import com.tan.gratify.expect.downloadAndInstallApk
import com.tan.gratify.extension.copy
import com.tan.gratify.getPlatform
import com.tan.gratify.ui.component.AppBottomNavigationBar
import com.tan.gratify.ui.component.AppNavigationRail
import com.tan.gratify.ui.component.ProfileDrawerContent
import com.tan.gratify.ui.navigation.destination.home.ProfileDestination
import com.tan.gratify.ui.navigation.destination.home.SettingsDestination
import com.tan.gratify.ui.navigation.destination.home.NotificationDestination
import com.tan.gratify.ui.navigation.openMainTab
import com.tan.gratify.ui.navigation.destination.home.RecentlySongsDestination
import com.tan.gratify.ui.navigation.destination.list.AlbumDestination
import com.tan.gratify.ui.navigation.destination.list.ArtistDestination
import com.tan.gratify.ui.navigation.destination.list.PlaylistDestination
import com.tan.gratify.ui.navigation.destination.player.FullscreenDestination

import com.tan.gratify.ui.navigation.graph.AppNavigationGraph
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import com.tan.gratify.viewModel.auth.ProfileLoginReturn
import com.tan.domain.data.model.intent.GenericIntent
import com.tan.domain.utils.profileUrl
import com.tan.gratify.ui.screen.player.FullscreenPlayer
import com.tan.gratify.ui.screen.player.NowPlayingScreen
import com.tan.gratify.ui.screen.player.NowPlayingScreenContent
import com.tan.gratify.ui.theme.fontFamily
import com.tan.gratify.ui.theme.typo
import com.tan.gratify.utils.VersionManager
import com.tan.gratify.ui.navigation.showMainChrome
import com.tan.gratify.ui.component.LayerBackHandler
import com.tan.gratify.viewModel.SettingsViewModel
import com.tan.gratify.viewModel.SharedViewModel
import com.tan.logger.Logger
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.cancel
import gratify.composeapp.generated.resources.do_not_show_again
import gratify.composeapp.generated.resources.download
import gratify.composeapp.generated.resources.good_night
import gratify.composeapp.generated.resources.notification
import gratify.composeapp.generated.resources.sleep_timer_off
import gratify.composeapp.generated.resources.this_app_needs_to_access_your_notification
import gratify.composeapp.generated.resources.this_link_is_not_supported
import gratify.composeapp.generated.resources.unknown
import gratify.composeapp.generated.resources.update_available
import gratify.composeapp.generated.resources.update_message
import gratify.composeapp.generated.resources.yes
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.ExperimentalTime

/**
 * MainScreen berisi flow utama aplikasi (Home, Search, Library) lengkap dengan BottomNavigationBar,
 * MiniPlayer, dan FullscreenPlayer. Screen ini di-host secara terpisah dari Auth Flow.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    onLogOut: () -> Unit = {},
    viewModel: SharedViewModel = koinInject(),
    settingsViewModel: SettingsViewModel = koinInject()
) {
    val supabase: SupabaseClient = koinInject()
    val sessions: com.tan.data.sync.AccountSessionCoordinator = koinInject()
    val accountAccess by sessions.access.collectAsStateWithLifecycle()
    LaunchedEffect(accountAccess) {
        if (accountAccess == com.tan.data.sync.AccountAccess.SignedOut &&
            supabase.auth.sessionStatus.value is io.github.jan.supabase.auth.status.SessionStatus.NotAuthenticated &&
            !com.tan.gratify.viewModel.auth.PasswordRecoveryCoordinator.pending.value) onLogOut()
    }
    if (accountAccess !is com.tan.data.sync.AccountAccess.Ready) {
        com.tan.gratify.ui.component.AccountRecoveryScreen(
            accountAccess != com.tan.data.sync.AccountAccess.RecoveryRequired, sessions::retry)
        return
    }
    val windowSize = currentWindowAdaptiveInfo().windowSizeClass
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    LaunchedEffect(Unit) {
        val target = ProfileLoginReturn.consume(supabase.auth.currentUserOrNull() != null)
        if (target != null && viewModel.intent.value == null) {
            viewModel.setIntent(GenericIntent(action = "android.intent.action.VIEW", data = com.eygraber.uri.Uri.parse(profileUrl(target))))
        }
    }
    
    LaunchedEffect(Unit) {
        settingsViewModel.getLoggedIn()
    }

    val sleepTimerState by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    val nowPlayingData by viewModel.nowPlayingState.collectAsStateWithLifecycle()
    val updateData by viewModel.updateResponse.collectAsStateWithLifecycle()
    val updateDialogRequest by viewModel.updateDialogRequest.collectAsStateWithLifecycle()
    val installState by com.tan.gratify.expect.updateInstallState().collectAsStateWithLifecycle()
    val updateStatusRequest by com.tan.gratify.expect.updateStatusRequests.collectAsStateWithLifecycle()
    val intent by viewModel.intent.collectAsStateWithLifecycle()
    val showNotificationPermissionDialog by viewModel.showNotificationPermissionDialog.collectAsStateWithLifecycle()

    val updateResponse by viewModel.updateResponse.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collect { event ->
            val result = snackbarHostState.showSnackbar(
                visuals = InAppNotificationVisuals(event),
            )
            if (result == SnackbarResult.ActionPerformed) {
                if (event.actionId == "view_notification") {
                    navController.navigate(NotificationDestination) { launchSingleTop = true }
                }
            }
        }
    }
    
    var isShowMiniPlayer by rememberSaveable { mutableStateOf(true) }
    var isShowNowPlaylistScreen by rememberSaveable { mutableStateOf(false) }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val isInFullscreen = navBackStackEntry?.destination?.hasRoute<FullscreenDestination>() == true
    val isNavBarVisible = showMainChrome(navBackStackEntry?.destination)
    var accountActionUnavailable by remember { mutableStateOf(false) }
    val requestAccountAction: () -> Unit = {
        // Main is already admitted by the local session coordinator. A temporarily
        // missing cloud identity must never send this account back through onboarding.
        accountActionUnavailable = true
    }
    var shouldShowUpdateDialog by rememberSaveable { mutableStateOf(false) }
    var lastOpenedUpdateId by rememberSaveable { mutableStateOf(0L) }
    var lastHandledUpdateRequest by rememberSaveable { mutableStateOf(0L) }

    LaunchedEffect(nowPlayingData) {
        isShowMiniPlayer = !(nowPlayingData?.mediaItem == null || nowPlayingData?.mediaItem == GenericMediaItem.EMPTY)
    }

    LaunchedEffect(intent) {
        val intent = intent ?: return@LaunchedEffect
        val data = intent.data
        Logger.d("MainActivity", "Content link received")
        if (data != null) {
            val invitation = com.tan.domain.utils.parseWorkspaceInvitation(data.toString())
            if (invitation != null) {
                viewModel.setIntent(null)
                if(!com.tan.domain.utils.PlaylistFeaturePolicy.experimentalEnabled) {
                    viewModel.makeToast(com.tan.domain.utils.PlaylistFeaturePolicy.archivedMessage)
                    return@LaunchedEffect
                }
                navController.navigate(com.tan.gratify.ui.navigation.destination.library.PlaylistWorkspaceDestination("invite", token = invitation)) { launchSingleTop = true }
                return@LaunchedEffect
            }
            val profileId = com.tan.domain.utils.parseProfileUserId(data.toString(), allowRawId = false)
            if (profileId != null) {
                viewModel.setIntent(null)
                navController.navigate(com.tan.gratify.ui.navigation.destination.social.UserProfileDestination(profileId)) { launchSingleTop = true }
                return@LaunchedEffect
            }
            if (data.scheme == "gratify" && data.host == "friends") {
                viewModel.setIntent(null)
                val owner = (accountAccess as? com.tan.data.sync.AccountAccess.Ready)?.owner.orEmpty()
                val target = com.tan.gratify.ui.navigation.parseSocialNotificationTarget(data.toString(), owner)
                if (target != null) {
                    viewModel.openSocialNotification(target)
                    navController.openMainTab(com.tan.gratify.ui.navigation.destination.friends.FriendsDestination, returnToRoot = true)
                } else viewModel.makeToast("Notifikasi ini tidak tersedia untuk akun yang sedang digunakan.")
            } else if (data == "gratify://downloads".toUri()) {
                viewModel.setIntent(null)
                navController.openMainTab(com.tan.gratify.ui.navigation.destination.library.LibraryDestination)
            } else if (data == "gratify://notification".toUri()) {
                viewModel.setIntent(null)
                navController.navigate(NotificationDestination) { launchSingleTop = true }
            } else if (data.host in listOf("gratify.web.id", "gratify.org") || data.scheme == "gratify") {
                val segments = data.pathSegments
                val appPath = if (data.scheme == "gratify") data.host else segments.getOrNull(1)
                Logger.d("MainActivity", "gratify.org deep link, appPath: $appPath")
                viewModel.setIntent(null)
                when (appPath) {
                    "watch" -> {
                        data.getQueryParameter("v")?.let { videoId ->
                            viewModel.loadSharedMediaItem(videoId)
                        }
                    }
                    "playlist" -> {
                        data.getQueryParameter("list")?.let { playlistId ->
                            if (playlistId.startsWith("OLAK5uy_")) {
                                navController.navigate(AlbumDestination(browseId = playlistId))
                            } else if (playlistId.startsWith("VL")) {
                                navController.navigate(PlaylistDestination(playlistId = playlistId))
                            } else {
                                navController.navigate(PlaylistDestination(playlistId = "VL$playlistId"))
                            }
                        }
                    }
                    "channel", "c" -> {
                        val artistId = if (data.scheme == "gratify") segments.firstOrNull() else segments.getOrNull(2)
                        artistId?.let {
                            if (it.startsWith("UC")) {
                                navController.navigate(ArtistDestination(channelId = it))
                            } else {
                                viewModel.makeToast(getString(Res.string.this_link_is_not_supported))
                            }
                        }
                    }
                    "album" -> {
                        data.getQueryParameter("id")?.let { albumId ->
                            navController.navigate(AlbumDestination(browseId = albumId))
                        }
                    }
                    else -> {
                        viewModel.makeToast(getString(Res.string.this_link_is_not_supported))
                    }
                }
            } else {
                Logger.d("MainActivity", "External content link received")
                when (val path = data.pathSegments.firstOrNull()) {
                    "playlist" -> {
                        data.getQueryParameter("list")?.let { playlistId ->
                            viewModel.setIntent(null)
                            if (playlistId.startsWith("OLAK5uy_")) {
                                navController.navigate(AlbumDestination(browseId = playlistId))
                            } else if (playlistId.startsWith("VL")) {
                                navController.navigate(PlaylistDestination(playlistId = playlistId))
                            } else {
                                navController.navigate(PlaylistDestination(playlistId = "VL$playlistId"))
                            }
                        }
                    }
                    "channel", "c" -> {
                        data.lastPathSegment?.let { artistId ->
                            if (artistId.startsWith("UC")) {
                                viewModel.setIntent(null)
                                navController.navigate(ArtistDestination(channelId = artistId))
                            } else {
                                viewModel.makeToast(getString(Res.string.this_link_is_not_supported))
                            }
                        }
                    }
                    else -> {
                        val videoId = when {
                            path == "watch" -> data.getQueryParameter("v")
                            data.host == "youtu.be" -> path
                            else -> null
                        }
                        videoId?.let {
                            viewModel.setIntent(null)
                            viewModel.loadSharedMediaItem(it)
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(updateData, updateDialogRequest) {
        if (updateData?.let(VersionManager::isUpdateAvailable) == true && viewModel.showedUpdateDialog &&
            updateData?.apkUrl?.startsWith("https://") == true) shouldShowUpdateDialog = true
    }
    LaunchedEffect(installState.downloadId) {
        if (installState.downloadId > 0 && installState.downloadId != lastOpenedUpdateId) {
            shouldShowUpdateDialog = true
            lastOpenedUpdateId = installState.downloadId
        }
    }
    LaunchedEffect(updateStatusRequest) {
        if (updateStatusRequest > 0 && updateStatusRequest != lastHandledUpdateRequest) {
            shouldShowUpdateDialog = true
            lastHandledUpdateRequest = updateStatusRequest
        }
    }

    LaunchedEffect(navBackStackEntry) {
        Logger.d("MainActivity", "Current destination: ${navBackStackEntry?.destination?.route}")
        if (isInFullscreen) {
            isShowNowPlaylistScreen = false
        }
    }

    val isDesktop = getPlatform() == Platform.Desktop
    val isTablet = windowSize.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
    val isTabletLandscape = isTablet && currentOrientation() == Orientation.LANDSCAPE

    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val supportPromptStatus by viewModel.developerSupportPrompt.status.collectAsStateWithLifecycle()
    val supportCanShow = navBackStackEntry?.destination?.hasRoute<com.tan.gratify.ui.navigation.destination.home.HomeDestination>() == true &&
        !showNotificationPermissionDialog && !shouldShowUpdateDialog && !sleepTimerState.isDone &&
        !accountActionUnavailable && !isShowNowPlaylistScreen && drawerState.isClosed && intent?.data == null
    LaunchedEffect(supportPromptStatus, supportCanShow) {
        if (supportPromptStatus == com.tan.gratify.viewModel.support.SupportPromptStatus.Pending && supportCanShow) {
            kotlinx.coroutines.delay(700)
            viewModel.developerSupportPrompt.present()
        }
    }
    val dataStoreManager: DataStoreManager = koinInject()
    val appProfileName by dataStoreManager.getString("AppProfileName").collectAsStateWithLifecycle(initialValue = "")
    val appProfileImage by dataStoreManager.getString("AppProfileImage").collectAsStateWithLifecycle(initialValue = "")

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ProfileDrawerContent(
                profileImageUrl = appProfileImage,
                profileName = appProfileName ?: "Pengguna",
                onViewProfileClick = {
                    coroutineScope.launch { drawerState.close() }
                    navController.navigate(ProfileDestination) { launchSingleTop = true }
                },
                onWrappedClick = {
                    coroutineScope.launch { drawerState.close() }
                    navController.navigate(com.tan.gratify.ui.navigation.destination.home.WrappedDestination) { launchSingleTop = true }
                },
                onRecentlyPlayedClick = {
                    coroutineScope.launch { drawerState.close() }
                    navController.navigate(RecentlySongsDestination) { launchSingleTop = true }
                },
                onNotificationClick = {
                    coroutineScope.launch { drawerState.close() }
                    navController.navigate(NotificationDestination) { launchSingleTop = true }
                },
                onSettingsClick = {
                    coroutineScope.launch { drawerState.close() }
                    navController.navigate(SettingsDestination) { launchSingleTop = true }
                },
                onCloseClick = {
                    coroutineScope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            bottomBar = {
            if (!isTablet && !isDesktop) {
                Column(
                    modifier = Modifier
                        .zIndex(10f)
                ) {
                    AnimatedVisibility(
                        isShowMiniPlayer && isNavBarVisible,
                        enter = fadeIn() + slideInHorizontally(),
                        exit = fadeOut(),
                    ) {
                        MiniPlayer(
                            Modifier
                                .height(56.dp)
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 4.dp),
                            onClick = { isShowNowPlaylistScreen = true },
                            progressEnabled = !isShowNowPlaylistScreen,
                            onClose = {
                                viewModel.stopPlayer()
                                viewModel.isServiceRunning = false
                            },
                        )
                    }
                    
                    if (isNavBarVisible) {
                        AppBottomNavigationBar(navController = navController) { destination ->
                            viewModel.reloadDestination(destination)
                        }
                    }
                }
            }
        },
        content = { innerPadding ->
            if (isDesktop) {
                com.tan.gratify.ui.component.DesktopWorkspace(
                    navController = navController,
                    sharedViewModel = viewModel,
                    profileName = appProfileName,
                    profileImage = appProfileImage,
                    chromeVisible = isNavBarVisible,
                ) {
                    AppNavigationGraph(
                        onLoginRequired = requestAccountAction,
                        innerPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        navController = navController,
                        onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                        hideNavBar = {},
                        showNavBar = {},
                    )
                }
            } else {
            val destinationPadding = if (isTablet && isShowMiniPlayer && isNavBarVisible) {
                innerPadding.copy(bottom = innerPadding.calculateBottomPadding() + 84.dp)
            } else innerPadding
            Box(
                Modifier
                    .fillMaxSize(),
            ) {
                Row(Modifier.fillMaxSize()) {
                    if (isTablet && isNavBarVisible) {
                        AppNavigationRail(navController = navController) { klass ->
                            viewModel.reloadDestination(klass)
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .weight(1f),
                    ) {
                        com.tan.gratify.ui.component.BottomSafeContent(destinationPadding) { contentPadding ->
                            AppNavigationGraph(
                                onLoginRequired = requestAccountAction,
                                innerPadding = contentPadding,
                                navController = navController,
                                onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                                hideNavBar = {},
                                showNavBar = { showPlayer ->
                                    if (showPlayer) isShowNowPlaylistScreen = true
                                },
                            )
                        }
                        this@Row.AnimatedVisibility(
                            modifier = Modifier
                                .padding(innerPadding)
                                .align(Alignment.BottomCenter),
                            visible = isShowMiniPlayer && isTablet && isNavBarVisible,
                            enter = fadeIn() + slideInHorizontally(),
                            exit = fadeOut(),
                        ) {
                            MiniPlayer(
                                if (getPlatform() == Platform.Android) {
                                    Modifier
                                        .height(84.dp)
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp)
                                        .padding(bottom = 4.dp)
                                } else {
                                    Modifier
                                        .fillMaxWidth()
                                        .height(84.dp)
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                },
                                onClick = { isShowNowPlaylistScreen = true },
                                onClose = {
                                    viewModel.stopPlayer()
                                    viewModel.isServiceRunning = false
                                },
                            )
                        }
                    }
                    if (isTablet && isTabletLandscape && !isInFullscreen) {
                        AnimatedVisibility(
                            isShowNowPlaylistScreen,
                            enter = slideInHorizontally(tween(240)) { it } + fadeIn(tween(240)),
                            exit = slideOutHorizontally(tween(240)) { it } + fadeOut(tween(240)),
                        ) {
                            Row(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(0.35f),
                            ) {
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    Modifier
                                        .padding(innerPadding.copy(start = 0.dp, top = 0.dp, bottom = 0.dp))
                                        .clip(GratifyShapes.medium),
                                ) {
                                    NowPlayingScreenContent(
                                        navController = navController,
                                        sharedViewModel = viewModel,
                                        isExpanded = true,
                                        dismissIcon = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                                    ) {
                                        isShowNowPlaylistScreen = false
                                    }
                                    // Registered after the NavHost so this inline panel closes first.
                                    LayerBackHandler(isShowNowPlaylistScreen && !drawerState.isOpen) {
                                        isShowNowPlaylistScreen = false
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isShowNowPlaylistScreen && !isTabletLandscape) {
                NowPlayingScreen(
                    navController = navController,
                    sharedViewModel = viewModel,
                ) {
                    isShowNowPlaylistScreen = false
                }
            }
            }

            if (accountActionUnavailable) {
                AlertDialog(onDismissRequest = { accountActionUnavailable = false },
                    title = { Text("Koneksi akun belum tersedia") },
                    text = { Text("Story, pesan, dan mengikuti akun memerlukan koneksi ke Gratify. Lagu unduhan tetap bisa diputar. Coba lagi saat koneksi tersedia.") },
                    confirmButton = { TextButton(onClick = { accountActionUnavailable = false; sessions.retry() }) { Text("Coba lagi") } },
                    dismissButton = { TextButton(onClick = { accountActionUnavailable = false }) { Text("Tutup") } })
            }
            // Developer support appears only once the homepage is ready and other modals are closed.
            if (supportPromptStatus == com.tan.gratify.viewModel.support.SupportPromptStatus.Visible && supportCanShow) {
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                com.tan.gratify.ui.component.DeveloperSupportSheet(
                    onDismiss = { optedOut -> coroutineScope.launch { viewModel.developerSupportPrompt.dismiss(optedOut) } },
                    onOpenLink = uriHandler::openUri,
                )
            }

            // Sleep Timer Dialog
            if (sleepTimerState.isDone) {
                AlertDialog(
                    properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                    onDismissRequest = { viewModel.stopSleepTimer() },
                    confirmButton = {
                        TextButton(onClick = { viewModel.stopSleepTimer() }) {
                            Text(stringResource(Res.string.yes), style = typo().bodySmall)
                        }
                    },
                    text = {
                        Text(stringResource(Res.string.sleep_timer_off), style = typo().labelSmall)
                    },
                    title = {
                        Text(stringResource(Res.string.good_night), style = typo().bodySmall)
                    },
                )
            }

            // Updates can always be postponed; local playback stays reachable.
            if (shouldShowUpdateDialog && (updateData != null || installState.update != null)) {
                com.tan.gratify.ui.component.AppUpdateDialog(updateData, installState) {
                    shouldShowUpdateDialog = false
                    viewModel.showedUpdateDialog = false
                }
            }

            // Notification Permission Dialog
            if (showNotificationPermissionDialog) {
                var doNotShowAgain by remember { mutableStateOf(false) }
                AlertDialog(
                    onDismissRequest = { viewModel.dismissNotificationPermissionDialog(doNotShowAgain) },
                    confirmButton = {
                        TextButton(onClick = {
                            com.tan.gratify.expect.openNotificationSettings()
                            viewModel.dismissNotificationPermissionDialog(doNotShowAgain)
                        }) {
                            Text("Buka pengaturan", style = typo().bodySmall)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.dismissNotificationPermissionDialog(doNotShowAgain) }) { Text("Nanti") }
                    },
                    title = {
                        Text(stringResource(Res.string.notification), style = typo().labelSmall)
                    },
                    text = {
                        Column {
                            Text(
                                stringResource(Res.string.this_app_needs_to_access_your_notification),
                                style = typo().bodySmall,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { doNotShowAgain = !doNotShowAgain }
                                    .fillMaxWidth(),
                            ) {
                                Checkbox(
                                    checked = doNotShowAgain,
                                    onCheckedChange = { doNotShowAgain = it },
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    stringResource(Res.string.do_not_show_again),
                                    style = typo().bodySmall,
                                )
                            }
                        }
                    },
                )
            }
            
            Box(Modifier.fillMaxSize().padding(innerPadding)) {
                InAppNotificationHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(if (isDesktop) Alignment.TopEnd else Alignment.TopCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        },
    )
    }
}
