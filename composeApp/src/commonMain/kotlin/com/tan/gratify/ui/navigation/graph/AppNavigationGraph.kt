package com.tan.gratify.ui.navigation.graph

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.tan.gratify.ui.navigation.destination.friends.FriendsDestination
import com.tan.gratify.ui.navigation.destination.home.HomeDestination
import com.tan.gratify.ui.navigation.destination.library.LibraryDestination
import com.tan.gratify.ui.navigation.destination.home.ProfileDestination
import com.tan.gratify.ui.screen.home.ProfileScreen
import com.tan.gratify.viewModel.HomeViewModel
import org.koin.compose.viewmodel.koinViewModel
import com.tan.gratify.ui.navigation.destination.player.FullscreenDestination
import com.tan.gratify.ui.navigation.destination.search.SearchDestination
import com.tan.gratify.ui.screen.friends.FriendsActivityScreen
import com.tan.gratify.ui.screen.home.HomeScreen
import com.tan.gratify.ui.screen.library.LibraryScreen
import com.tan.gratify.ui.screen.search.SearchScreen
import com.tan.gratify.ui.screen.player.FullscreenPlayer

@Composable
@ExperimentalMaterial3Api
@ExperimentalFoundationApi
fun AppNavigationGraph(
    innerPadding: PaddingValues,
    navController: NavHostController,
    startDestination: Any = HomeDestination,
    hideNavBar: () -> Unit = { },
    showNavBar: (shouldShowNowPlayingSheet: Boolean) -> Unit = { },
    showNowPlayingSheet: () -> Unit = {},
    onScrolling: (onTop: Boolean) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
) {
    val homeViewModel: HomeViewModel = koinViewModel()
    NavHost(
        navController,
        startDestination = startDestination,
        enterTransition = {
            fadeIn(animationSpec = tween(180))
        },
        exitTransition = {
            fadeOut(animationSpec = tween(180))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(180))
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(180))
        },
    ) {
        // Bottom bar destinations
        composable<HomeDestination> {
            HomeScreen(
                innerPadding = innerPadding,
                viewModel = homeViewModel,
                onOpenDrawer = onOpenDrawer,
                onScrolling = onScrolling,
                navController = navController,
            )
        }
        composable<ProfileDestination> { ProfileScreen(navController = navController) }
        composable<SearchDestination> {
            SearchScreen(
                innerPadding = innerPadding,
                homeViewModel = homeViewModel,
                onOpenDrawer = onOpenDrawer,
                navController = navController,
            )
        }
        composable<LibraryDestination> {
            LibraryScreen(
                onOpenDrawer = onOpenDrawer,
                innerPadding = innerPadding,
                navController = navController,
                onScrolling = onScrolling,
            )
        }
        composable<FriendsDestination> {
            FriendsActivityScreen(
                innerPadding = innerPadding,
                onOpenDrawer = onOpenDrawer,
                navController = navController,
                onScrolling = onScrolling,
            )
        }
        composable<FullscreenDestination> {
            FullscreenPlayer(
                navController,
                hideNavBar = hideNavBar,
                showNavBar = {
                    showNavBar.invoke(true)
                    showNowPlayingSheet.invoke()
                },
            )
        }
        // Home screen graph
        homeScreenGraph(
            innerPadding = innerPadding,
            navController = navController,
        )
        // Library screen graph
        libraryScreenGraph(
            innerPadding = innerPadding,
            navController = navController,
        )
        // List screen graph
        listScreenGraph(
            innerPadding = innerPadding,
            navController = navController,
        )
        // Login screen graph
        loginScreenGraph(
            innerPadding = innerPadding,
            navController = navController,
            hideBottomBar = hideNavBar,
            showBottomBar = {
                showNavBar(false)
            },
        )
    }
}
