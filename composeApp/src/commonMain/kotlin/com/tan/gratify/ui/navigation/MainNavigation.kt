package com.tan.gratify.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.tan.gratify.ui.navigation.destination.home.HomeDestination
import com.tan.gratify.ui.navigation.destination.search.SearchDestination
import com.tan.gratify.ui.navigation.destination.library.LibraryDestination
import com.tan.gratify.ui.navigation.destination.friends.FriendsDestination
import com.tan.gratify.ui.navigation.destination.library.AddSongsToPlaylistDestination
import com.tan.gratify.ui.navigation.destination.library.ArtistSelectionDestination
import com.tan.gratify.ui.navigation.destination.list.LocalPlaylistDestination
import com.tan.gratify.ui.navigation.destination.login.LoginDestination
import com.tan.gratify.ui.navigation.destination.player.FullscreenDestination
import kotlin.reflect.KClass
import com.tan.data.sync.AccountAccess

fun isSocialAccountAvailable(access: AccountAccess, cloudUserId: String?): Boolean =
    access is AccountAccess.Ready && !access.offline && access.owner == cloudUserId

fun NavController.navigateFromPlayer(route: Any, dismissPlayer: () -> Unit) {
    dismissPlayer()
    navigate(route) { launchSingleTop = true }
}

enum class MainTab(val route: Any) {
    HOME(HomeDestination), SEARCH(SearchDestination), LIBRARY(LibraryDestination), FRIENDS(FriendsDestination);
    companion object {
        fun forDestination(destination: NavDestination?): MainTab? = entries.firstOrNull {
            destination?.hasRoute(it.route::class) == true
        }
    }
}

/** Details belong to the last tab root on the ACTIVE stack, including restored stacks. */
fun NavController.currentMainTab(): MainTab = currentBackStack.value.asReversed()
    .firstNotNullOfOrNull { MainTab.forDestination(it.destination) } ?: MainTab.HOME

fun NavController.openMainTab(route: Any, returnToRoot: Boolean = false,
    onRootReselect: (KClass<*>) -> Unit = {}) {
    require(MainTab.entries.any { it.route::class == route::class })
    if (currentMainTab().route::class == route::class) {
        if (currentDestination?.hasRoute(route::class) == true) onRootReselect(route::class)
        else popBackStack(route, inclusive = false)
    } else {
        navigate(route) {
            popUpTo(graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        if (returnToRoot && currentDestination?.hasRoute(route::class) != true) {
            popBackStack(route, inclusive = false)
        }
    }
}

fun NavController.openPeopleSearch() {
    openMainTab(SearchDestination, returnToRoot = true)
    currentBackStackEntry?.savedStateHandle?.set("people_search_requested", true)
}

fun NavController.openPlaylistBuilder(title: String = "") {
    navigate(AddSongsToPlaylistDestination(title)) { launchSingleTop = true }
}

fun NavController.openPlaylistSongPicker(id: Long, title: String) {
    navigate(AddSongsToPlaylistDestination(title, id)) { launchSingleTop = true }
}

fun NavController.finishPlaylistAddition(id: Long) {
    if (previousBackStackEntry?.destination?.hasRoute<LocalPlaylistDestination>() == true) {
        previousBackStackEntry?.savedStateHandle?.set("playlist_tracks_changed", true)
        popBackStack()
    } else finishPlaylistCreation(id)
}

fun NavController.finishPlaylistCreation(id: Long) {
    navigate(LocalPlaylistDestination(id)) {
        popUpTo(AddSongsToPlaylistDestination::class) { inclusive = true }
        launchSingleTop = true
    }
}

fun showMainChrome(destination: NavDestination?): Boolean = destination != null &&
    !destination.hasRoute<FullscreenDestination>() &&
    !destination.hasRoute<ArtistSelectionDestination>() &&
    !destination.hasRoute<AddSongsToPlaylistDestination>() &&
    !destination.hasRoute<LoginDestination>()
