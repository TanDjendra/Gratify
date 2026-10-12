package com.tan.gratify.ui.screen.home.wrapped

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.LocalPlatformContext
import com.tan.gratify.expect.shareLyricsImage
import com.tan.gratify.ui.navigation.destination.home.SettingsDestination
import com.tan.gratify.ui.navigation.destination.list.LocalPlaylistDestination
import com.tan.gratify.viewModel.WrappedViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun WrappedScreen(innerPadding: PaddingValues, navController: NavController, viewModel: WrappedViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalPlatformContext.current
    WrappedContent(state, innerPadding,
        onBack = { navController.popBackStack() }, onSelectYear = viewModel::load,
        onRefresh = { viewModel.load(state.year) }, onSettings = { navController.navigate(SettingsDestination) },
        onSavePlaylist = viewModel::savePlaylist,
        onOpenPlaylist = { id -> navController.navigate(LocalPlaylistDestination(id)) },
        onShare = { image -> shareLyricsImage(image, context) })
}
