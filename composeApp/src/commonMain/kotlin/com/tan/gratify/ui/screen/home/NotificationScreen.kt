package com.tan.gratify.ui.screen.home

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.eygraber.uri.Uri
import com.tan.domain.data.model.intent.GenericIntent
import com.tan.domain.utils.notificationSocialLink
import com.tan.gratify.expect.notificationsEnabled
import com.tan.gratify.expect.openNotificationSettings
import com.tan.gratify.ui.navigation.destination.list.AlbumDestination
import com.tan.gratify.ui.navigation.destination.list.ArtistDestination
import com.tan.gratify.ui.navigation.destination.social.UserProfileDestination
import com.tan.gratify.viewModel.NotificationViewModel
import com.tan.gratify.viewModel.SharedViewModel
import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun NotificationScreen(navController: NavController, viewModel: NotificationViewModel = koinViewModel()) {
    val rows by viewModel.listNotification.collectAsStateWithLifecycle()
    val following by viewModel.following.collectAsStateWithLifecycle()
    val busy by viewModel.followingBusy.collectAsStateWithLifecycle()
    val followingReady by viewModel.followingReady.collectAsStateWithLifecycle()
    val deleting by viewModel.deleting.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val readFailed by viewModel.readFailed.collectAsStateWithLifecycle()
    val problems by viewModel.syncProblems.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val shared: SharedViewModel = koinInject()
    val settingsFailure = stringResource(Res.string.inbox_settings_failed)
    var enabled by remember { mutableStateOf(notificationsEnabled()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) enabled = notificationsEnabled() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    NotificationInboxContent(rows, following, busy, followingReady, deleting, refreshing, readFailed, problems,
        error, enabled, onBack = { navController.navigateUp() }, onRefresh = viewModel::refresh,
        onSettings = { if (!openNotificationSettings()) viewModel.actionFailed(settingsFailure) },
        onDismissError = viewModel::clearError, onDelete = viewModel::deleteNotification,
        onFollowBack = viewModel::followBack,
        onOpenProfile = { row, id -> viewModel.openAccountNotification(row) { navController.navigate(UserProfileDestination(id)) } },
        onOpenSocial = { row -> notificationSocialLink(row)?.let { link ->
            viewModel.openAccountNotification(row) { shared.setIntent(GenericIntent("android.intent.action.VIEW", Uri.parse(link))) }
        } }, onOpenArtist = { id -> navController.navigate(ArtistDestination(id)) },
        onOpenAlbum = { id -> navController.navigate(AlbumDestination(id)) }, onOpenLink = uriHandler::openUri)
}