package com.tan.gratify.ui.navigation.graph

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.tan.gratify.ui.navigation.destination.library.AddSongsToPlaylistDestination
import com.tan.gratify.ui.navigation.destination.library.LibraryDynamicPlaylistDestination
import com.tan.gratify.ui.navigation.destination.library.ArtistSelectionDestination
import com.tan.gratify.ui.screen.library.AddSongsToPlaylistScreen
import com.tan.gratify.ui.screen.library.LibraryDynamicPlaylistScreen
import com.tan.gratify.ui.screen.library.ArtistSelectionScreen

@ExperimentalMaterial3Api
fun NavGraphBuilder.libraryScreenGraph(
    innerPadding: PaddingValues,
    navController: NavController,
) {
    composable<com.tan.gratify.ui.navigation.destination.library.PlaylistWorkspaceDestination> { entry ->
        com.tan.gratify.ui.screen.library.PlaylistWorkspaceScreen(entry.toRoute(), navController)
    }
    composable<LibraryDynamicPlaylistDestination> { entry ->
        val data = entry.toRoute<LibraryDynamicPlaylistDestination>()
        LibraryDynamicPlaylistScreen(
            innerPadding = innerPadding,
            navController = navController,
            type = data.type,
        )
    }

    composable<ArtistSelectionDestination> {
        ArtistSelectionScreen(
            navController = navController,
        )
    }

    composable<AddSongsToPlaylistDestination> { entry ->
        val data = entry.toRoute<AddSongsToPlaylistDestination>()
        AddSongsToPlaylistScreen(
            playlistTitle = data.playlistTitle,
            targetPlaylistId = data.targetPlaylistId,
            navController = navController,
        )
    }
}
