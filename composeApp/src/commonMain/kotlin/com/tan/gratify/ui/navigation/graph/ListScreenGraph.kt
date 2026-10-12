package com.tan.gratify.ui.navigation.graph

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.tan.gratify.ui.navigation.destination.list.AlbumDestination
import com.tan.gratify.ui.navigation.destination.list.ArtistDestination
import com.tan.gratify.ui.navigation.destination.list.LocalPlaylistDestination
import com.tan.gratify.ui.navigation.destination.list.MoreAlbumsDestination
import com.tan.gratify.ui.navigation.destination.list.PlaylistDestination
import com.tan.gratify.ui.navigation.destination.list.SharedPlaylistDestination
import com.tan.gratify.ui.navigation.destination.list.PodcastDestination
import com.tan.gratify.ui.screen.library.LocalPlaylistScreen
import com.tan.gratify.ui.screen.album.AlbumScreen
import com.tan.gratify.ui.screen.artist.ArtistScreen
import com.tan.gratify.ui.screen.album.MoreAlbumsScreen
import com.tan.gratify.ui.navigation.destination.list.YouTubePlaylistDestination
import com.tan.gratify.ui.screen.playlist.YouTubePlaylistScreen
import com.tan.gratify.ui.screen.playlist.PlaylistScreen
import com.tan.gratify.ui.screen.podcast.PodcastScreen
import com.tan.gratify.ui.screen.playlist.SharedPlaylistScreen
import com.tan.gratify.ui.screen.social.UserProfileScreen
import com.tan.gratify.ui.screen.social.FollowListScreen
import com.tan.gratify.ui.navigation.destination.social.UserProfileDestination
import com.tan.gratify.ui.navigation.destination.social.FollowListDestination

@ExperimentalMaterial3Api
@ExperimentalFoundationApi
fun NavGraphBuilder.listScreenGraph(
    innerPadding: PaddingValues,
    navController: NavController,
    onLoginRequired: () -> Unit = {},
) {
    composable<YouTubePlaylistDestination> { entry ->
        YouTubePlaylistScreen(entry.toRoute<YouTubePlaylistDestination>(), navController)
    }
    composable<AlbumDestination> { entry ->
        val data = entry.toRoute<AlbumDestination>()
        AlbumScreen(
            browseId = data.browseId,
            navController = navController,
        )
    }
    composable<ArtistDestination> { entry ->
        val data = entry.toRoute<ArtistDestination>()
        ArtistScreen(
            channelId = data.channelId,
            navController = navController,
        )
    }
    composable<LocalPlaylistDestination> { entry ->
        val data = entry.toRoute<LocalPlaylistDestination>()
        com.tan.gratify.ui.screen.library.UnifiedPlaylistScreen(data.id,navController)
    }
    composable<MoreAlbumsDestination> { entry ->
        val data = entry.toRoute<MoreAlbumsDestination>()
        MoreAlbumsScreen(
            innerPadding = innerPadding,
            navController = navController,
            type = data.type,
            id = data.id,
        )
    }
    composable<PlaylistDestination> { entry ->
        val data = entry.toRoute<PlaylistDestination>()
        PlaylistScreen(
            playlistId = data.playlistId,
            isYourYouTubePlaylist = data.isYourYouTubePlaylist,
            navController = navController,
        )
    }
    composable<PodcastDestination> { entry ->
        val data = entry.toRoute<PodcastDestination>()
        PodcastScreen(
            podcastId = data.podcastId,
            navController = navController,
        )
    }
    composable<SharedPlaylistDestination> { entry ->
        val data = entry.toRoute<SharedPlaylistDestination>()
        if (data.playlistId.startsWith("workspace:")) com.tan.gratify.ui.screen.library.PlaylistWorkspaceScreen(
            com.tan.gratify.ui.navigation.destination.library.PlaylistWorkspaceDestination("detail", data.playlistId.removePrefix("workspace:")), navController)
        else com.tan.gratify.ui.screen.library.CanonicalSharedPlaylistScreen(data.playlistId,navController)
    }
    composable<UserProfileDestination>(
        deepLinks = listOf(
            androidx.navigation.navDeepLink<UserProfileDestination>(basePath = "https://gratify.web.id/app/profile"),
            androidx.navigation.navDeepLink<UserProfileDestination>(basePath = "https://gratify.org/app/profile"),
            androidx.navigation.navDeepLink<UserProfileDestination>(basePath = "gratify://app/profile"),
            androidx.navigation.navDeepLink<UserProfileDestination>(basePath = "gratify://profile")
        )
    ) { entry ->
        val data = entry.toRoute<UserProfileDestination>()
        UserProfileScreen(
            userId = data.userId,
            navController = navController,
            onLoginRequired = onLoginRequired,
        )
    }
    composable<FollowListDestination> { entry ->
        val data = entry.toRoute<FollowListDestination>()
        FollowListScreen(
            userId = data.userId,
            initialTab = data.initialTab,
            navController = navController,
        )
    }
}
