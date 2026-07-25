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
) {
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
        LocalPlaylistScreen(
            id = data.id,
            navController = navController,
        )
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
        SharedPlaylistScreen(
            playlistId = data.playlistId,
            navController = navController,
        )
    }
    composable<UserProfileDestination>(
        deepLinks = listOf(
            androidx.navigation.navDeepLink<UserProfileDestination>(basePath = "https://gratify.org/app/profile"),
            androidx.navigation.navDeepLink<UserProfileDestination>(basePath = "gratify://app/profile")
        )
    ) { entry ->
        val data = entry.toRoute<UserProfileDestination>()
        UserProfileScreen(
            userId = data.userId,
            navController = navController,
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