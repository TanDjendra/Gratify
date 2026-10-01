package com.tan.gratify.ui.screen

import com.tan.gratify.ui.theme.GratifyShapes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
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
import androidx.navigation.NavDestination.Companion.hierarchy
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
import com.tan.gratify.ui.navigation.destination.home.RecentlySongsDestination
import com.tan.gratify.ui.navigation.destination.list.AlbumDestination
import com.tan.gratify.ui.navigation.destination.list.ArtistDestination
import com.tan.gratify.ui.navigation.destination.list.PlaylistDestination
import com.tan.gratify.ui.navigation.destination.player.FullscreenDestination
import com.tan.gratify.ui.navigation.destination.library.AddSongsToPlaylistDestination
import com.tan.gratify.ui.navigation.destination.library.ArtistSelectionDestination

import com.tan.gratify.ui.navigation.graph.AppNavigationGraph
import com.tan.gratify.ui.screen.player.FullscreenPlayer
import com.tan.gratify.ui.screen.player.NowPlayingScreen
import com.tan.gratify.ui.screen.player.NowPlayingScreenContent
import com.tan.gratify.ui.theme.fontFamily
import com.tan.gratify.ui.theme.typo
import com.tan.gratify.utils.VersionManager
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
    val windowSize = currentWindowAdaptiveInfo().windowSizeClass
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    
    val loggedInState by settingsViewModel.loggedIn.collectAsStateWithLifecycle(null)
    LaunchedEffect(Unit) {
        settingsViewModel.getLoggedIn()
    }
    LaunchedEffect(loggedInState) {
        if (loggedInState == DataStoreManager.FALSE) {
            onLogOut()
        }
    }

    val sleepTimerState by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    val nowPlayingData by viewModel.nowPlayingState.collectAsStateWithLifecycle()
    val updateData by viewModel.updateResponse.collectAsStateWithLifecycle()
    val intent by viewModel.intent.collectAsStateWithLifecycle()
    val showNotificationPermissionDialog by viewModel.showNotificationPermissionDialog.collectAsStateWithLifecycle()

    val updateResponse by viewModel.updateResponse.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collect { event ->
            val result = snackbarHostState.showSnackbar(
                message = event.message,
                actionLabel = event.actionLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                if (event.actionId == "view_notification") {
                    navController.navigate(NotificationDestination)
                }
            }
        }
    }
    
    var isShowMiniPlayer by rememberSaveable { mutableStateOf(true) }
    var isShowNowPlaylistScreen by rememberSaveable { mutableStateOf(false) }
    var isInFullscreen by rememberSaveable { mutableStateOf(false) }
    var isNavBarVisible by rememberSaveable { mutableStateOf(true) }
    var shouldShowUpdateDialog by rememberSaveable { mutableStateOf(false) }


    LaunchedEffect(nowPlayingData) {
        isShowMiniPlayer = !(nowPlayingData?.mediaItem == null || nowPlayingData?.mediaItem == GenericMediaItem.EMPTY)
    }

    LaunchedEffect(intent) {
        val intent = intent ?: return@LaunchedEffect
        val data = intent.data
        Logger.d("MainActivity", "onCreate: $data")
        if (data != null) {
            if (data == "gratify://notification".toUri()) {
                viewModel.setIntent(null)
                navController.navigate(NotificationDestination)
            } else if (data.host == "gratify.org" || data.scheme == "gratify") {
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
                Logger.d("MainActivity", "onCreate: $data")
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

    LaunchedEffect(updateData) {
        val response = updateData ?: return@LaunchedEffect
        val currentVersion = VersionManager.getVersionName()
        val minVersion = response.minVersion ?: "2.0.0"
        val hasDownload = response.apkUrl.startsWith("https://")
        val isForceUpdate = hasDownload && VersionManager.isVersionLower(currentVersion, minVersion)
        if (hasDownload && (viewModel.showedUpdateDialog || isForceUpdate) &&
            VersionManager.isVersionLower(currentVersion, response.tagName)
        ) {
            shouldShowUpdateDialog = true
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(navBackStackEntry) {
        Logger.d("MainActivity", "Current destination: ${navBackStackEntry?.destination?.route}")
        if (navBackStackEntry?.destination?.route?.contains("FullscreenDestination") == true) {
            isShowNowPlaylistScreen = false
        }
        isInFullscreen = navBackStackEntry?.destination?.hierarchy?.any {
            it.hasRoute(FullscreenDestination::class)
        } == true
        val isArtistSelection = navBackStackEntry?.destination?.hierarchy?.any {
            it.hasRoute(ArtistSelectionDestination::class)
        } == true
        val isAddSongsToPlaylist = navBackStackEntry?.destination?.hierarchy?.any {
            it.hasRoute(AddSongsToPlaylistDestination::class)
        } == true

        // Tampilkan bottom navigation bar di semua layar kecuali FullscreenPlayer, ArtistSelection, dan AddSongsToPlaylist
        isNavBarVisible = !isInFullscreen && !isArtistSelection && !isAddSongsToPlaylist
    }

    val isTablet = windowSize.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
    val isTabletLandscape = isTablet && currentOrientation() == Orientation.LANDSCAPE

    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
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
                    navController.navigate(ProfileDestination)
                },
                onRecentlyPlayedClick = {
                    coroutineScope.launch { drawerState.close() }
                    navController.navigate(RecentlySongsDestination)
                },
                onNotificationClick = {
                    coroutineScope.launch { drawerState.close() }
                    navController.navigate(NotificationDestination)
                },
                onSettingsClick = {
                    coroutineScope.launch { drawerState.close() }
                    navController.navigate(SettingsDestination)
                },
                onCloseClick = {
                    coroutineScope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            bottomBar = {
            if (!isTablet) {
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
                        Box(
                            Modifier
                                .fillMaxSize(),
                        ) {
                            AppNavigationGraph(
                                innerPadding = destinationPadding,
                                navController = navController,
                                onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                                hideNavBar = { isNavBarVisible = false },
                                showNavBar = { showPlayer ->
                                    isNavBarVisible = true
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
                            enter = expandHorizontally() + fadeIn(),
                            exit = fadeOut() + shrinkHorizontally(),
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

            // Update Dialog
            if (shouldShowUpdateDialog) {
                val response = updateData ?: return@Scaffold
                val currentVersion = VersionManager.getVersionName()
                val minVersion = response.minVersion ?: "2.0.0"
                val isForceUpdate = response.apkUrl.startsWith("https://") &&
                    VersionManager.isVersionLower(currentVersion, minVersion)
                AlertDialog(
                    properties = DialogProperties(dismissOnBackPress = !isForceUpdate, dismissOnClickOutside = false),
                    onDismissRequest = {
                        if (!isForceUpdate) {
                            shouldShowUpdateDialog = false
                            viewModel.showedUpdateDialog = false
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (!isForceUpdate) {
                                    shouldShowUpdateDialog = false
                                    viewModel.showedUpdateDialog = false
                                }
                                downloadAndInstallApk(response.apkUrl, response.tagName)
                            },
                        ) {
                            Text(stringResource(Res.string.download), style = typo().bodySmall)
                        }
                    },
                    dismissButton = {
                        if (!isForceUpdate) {
                            TextButton(
                                onClick = {
                                    shouldShowUpdateDialog = false
                                    viewModel.showedUpdateDialog = false
                                },
                            ) {
                                Text(stringResource(Res.string.cancel), style = typo().bodySmall)
                            }
                        }
                    },
                    title = {
                        Text(stringResource(Res.string.update_available), style = typo().labelSmall)
                    },
                    text = {
                        val formatted = response.releaseTime?.let { input ->
                            try {
                                val instant = kotlinx.datetime.Instant.parse(input)
                                val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
                                dateTime.format(
                                    LocalDateTime.Format {
                                        dayOfMonth()
                                        char(' ')
                                        monthName(MonthNames.ENGLISH_ABBREVIATED)
                                        char(' ')
                                        year()
                                        char(' ')
                                        hour()
                                        char(':')
                                        minute()
                                        char(':')
                                        second()
                                    },
                                )
                            } catch (e: Exception) {
                                stringResource(Res.string.unknown)
                            }
                        } ?: stringResource(Res.string.unknown)

                        val updateMessage = stringResource(Res.string.update_message, response.tagName, formatted)
                        Column(
                            Modifier
                                .heightIn(max = 400.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(
                                text = updateMessage,
                                style = typo().labelMedium,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                            Markdown(
                                response.body,
                                typography = markdownTypography(
                                    h1 = typo().labelLarge,
                                    h2 = typo().labelMedium,
                                    h3 = typo().labelSmall,
                                    text = typo().bodySmall,
                                    bullet = typo().bodySmall,
                                    paragraph = typo().bodySmall,
                                    textLink = TextLinkStyles(
                                        SpanStyle(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Normal,
                                            fontFamily = fontFamily(),
                                            textDecoration = TextDecoration.Underline,
                                        ),
                                    ),
                                ),
                            )
                        }
                    },
                )
            }

            // Notification Permission Dialog
            if (showNotificationPermissionDialog) {
                var doNotShowAgain by remember { mutableStateOf(false) }
                AlertDialog(
                    onDismissRequest = { viewModel.dismissNotificationPermissionDialog(doNotShowAgain) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.dismissNotificationPermissionDialog(doNotShowAgain) }) {
                            Text(stringResource(Res.string.yes), style = typo().bodySmall)
                        }
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
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
                )
            }
        },
    )
    }
}
