package com.tan.gratify.ui

import androidx.navigation.toRoute

import com.tan.gratify.ui.navigation.*
import com.tan.gratify.ui.navigation.graph.youTubeSettingsGraph
import com.tan.gratify.ui.navigation.graph.loginScreenGraph
import androidx.compose.foundation.layout.PaddingValues
import com.tan.gratify.ui.navigation.destination.login.LoginDestination
import com.tan.gratify.ui.navigation.destination.login.SplashDestination
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.tan.gratify.ui.component.AppBottomNavigationBar
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import com.tan.gratify.ui.navigation.destination.MainDestination
import com.tan.gratify.ui.navigation.destination.friends.FriendsDestination
import com.tan.gratify.ui.navigation.destination.home.HomeDestination
import com.tan.gratify.ui.navigation.destination.library.AddSongsToPlaylistDestination
import com.tan.gratify.ui.navigation.destination.library.LibraryDestination
import com.tan.gratify.ui.navigation.destination.list.AlbumDestination
import com.tan.gratify.ui.navigation.destination.list.ArtistDestination
import com.tan.gratify.ui.navigation.destination.list.LocalPlaylistDestination
import com.tan.gratify.ui.navigation.destination.login.LoginLandingDestination
import com.tan.gratify.ui.navigation.destination.login.YouTubeConnectDestination
import com.tan.gratify.ui.navigation.destination.search.SearchDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlin.test.*

/** Exercises production navigation helpers, real controller, and production bottom bar.
 * Empty content fixtures cover routing; Android system gestures need device validation.
 */
class NavigationRegressionTest {
    private class Fixture(outer: Boolean = false) {
        val store = ViewModelStore()
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply {
                currentState = Lifecycle.State.RESUMED
            }
        }
        val navigator = ComposeNavigator()
        val nav = NavHostController().apply {
            setLifecycleOwner(owner)
            setViewModelStore(store)
            navigatorProvider.addNavigator(navigator)
            graph = createGraph(startDestination = if (outer) SplashDestination else HomeDestination) {
                if (outer) {
                    loginScreenGraph(PaddingValues(), this@apply, {}, {})
                    composable<MainDestination> {}
                } else {
                composable<HomeDestination> {}
                composable<SearchDestination> {}
                composable<LibraryDestination> {}
                composable<FriendsDestination> {}
                composable<AlbumDestination> {}
                composable<ArtistDestination> {}
                composable<LocalPlaylistDestination> {}
                composable<AddSongsToPlaylistDestination> {}
                youTubeSettingsGraph(this@apply)
                }
            }
        }
        fun complete() = nav.currentBackStack.value.toList().forEach(navigator::onTransitionComplete)
        fun go(route: Any) { nav.navigate(route); complete() }
        fun tab(route: Any) {
            nav.openMainTab(route)
            complete()
        }
        fun back(): Boolean = nav.popBackStack().also { complete() }
        fun close() { (owner.lifecycle as LifecycleRegistry).currentState = Lifecycle.State.DESTROYED; store.clear() }
    }

    private fun audit(block: Fixture.() -> Unit): Unit = runBlocking(Dispatchers.Swing) {
        val fixture = Fixture()
        try { fixture.complete(); fixture.block() } finally { fixture.close() }
    }

    @Test fun libraryOpensMusicFirstBuilderOnceAndReturnsWithoutAnExtraNameForm() = audit {
        tab(LibraryDestination)
        nav.openPlaylistBuilder(); complete(); nav.openPlaylistBuilder(); complete()
        assertEquals(1, nav.currentBackStack.value.count { it.destination.hasRoute<AddSongsToPlaylistDestination>() })
        assertEquals("", nav.currentBackStackEntry!!.toRoute<AddSongsToPlaylistDestination>().playlistTitle)
        assertTrue(back()); assertTrue(nav.currentDestination!!.hasRoute<LibraryDestination>())
    }

    @Test fun creationPreservesLibraryAsReturnPath() = audit {
        tab(LibraryDestination)
        go(AddSongsToPlaylistDestination("Audit"))
        nav.finishPlaylistCreation(123)
        complete()
        assertTrue(back())
        assertTrue(nav.currentDestination!!.hasRoute<LibraryDestination>())
        assertTrue(nav.currentBackStack.value.any { it.destination.hasRoute<LibraryDestination>() })
    }
    @Test fun addingSongsReturnsToTheSamePlaylistAndRequestsRefreshWithoutDuplicateRoutes() = audit {
        tab(LibraryDestination); go(LocalPlaylistDestination(1))
        nav.openPlaylistSongPicker(1,"1"); complete(); nav.openPlaylistSongPicker(1,"1"); complete()
        assertEquals(1,nav.currentBackStack.value.count { it.destination.hasRoute<AddSongsToPlaylistDestination>() })
        assertEquals(1L,nav.currentBackStackEntry!!.toRoute<AddSongsToPlaylistDestination>().targetPlaylistId)
        nav.finishPlaylistAddition(1); complete()
        assertEquals(1L,nav.currentBackStackEntry!!.toRoute<LocalPlaylistDestination>().id)
        assertEquals(true,nav.currentBackStackEntry!!.savedStateHandle.get<Boolean>("playlist_tracks_changed"))
        assertEquals(1,nav.currentBackStack.value.count { it.destination.hasRoute<LocalPlaylistDestination>() })
        assertTrue(back()); assertTrue(nav.currentDestination!!.hasRoute<LibraryDestination>())
    }

    @Test fun restoredSearchDetailKeepsCorrectTabOwner() = audit {
        tab(SearchDestination)
        go(AlbumDestination("audit-album"))
        tab(LibraryDestination)
        tab(SearchDestination)
        assertTrue(nav.currentDestination!!.hasRoute<AlbumDestination>())
        assertFalse(nav.currentDestination!!.hasRoute<SearchDestination>())
        assertEquals(MainTab.SEARCH, nav.currentMainTab())
    }

    @Test fun reselectFromRestoredDetailReturnsToTabRoot() = audit {
        tab(SearchDestination); go(AlbumDestination("audit-album"))
        tab(LibraryDestination); tab(SearchDestination)
        tab(SearchDestination)
        assertTrue(nav.currentDestination!!.hasRoute<SearchDestination>())
    }

    @Test fun crossTabLinksDoNotDuplicateSearch() = audit {
        tab(SearchDestination)
        nav.openMainTab(FriendsDestination, returnToRoot = true); complete()
        nav.openPeopleSearch(); complete()
        assertEquals(1, nav.currentBackStack.value.count { it.destination.hasRoute<SearchDestination>() })
        assertEquals(true, nav.currentBackStackEntry!!.savedStateHandle.get<Boolean>("people_search_requested"))
        assertTrue(back())
        assertTrue(nav.currentDestination!!.hasRoute<HomeDestination>())
    }

    @Test fun innerHostOnlyHasYouTubeSettingsAndCannotRunOnboarding() = audit {
        go(LoginDestination)
        assertFailsWith<IllegalArgumentException> { nav.navigate(LoginLandingDestination) }
        assertFailsWith<IllegalArgumentException> { nav.navigate(YouTubeConnectDestination) }
        assertFalse(showMainChrome(nav.currentDestination))
        assertTrue(back())
        assertTrue(showMainChrome(nav.currentDestination))
    }

    @Test fun actualRootAuthGraphFinishesOnboardingWithoutReturningToLogin(): Unit = runBlocking(Dispatchers.Swing) {
        val fixture = Fixture(outer = true)
        try {
            fixture.complete(); fixture.go(YouTubeConnectDestination)
            fixture.nav.navigate(MainDestination) { popUpTo(0) { inclusive = true } }
            fixture.complete()
            assertTrue(fixture.nav.currentDestination!!.hasRoute<MainDestination>())
            assertNull(fixture.nav.previousBackStackEntry)
        } finally { fixture.close() }
    }

    @Test fun normalDetailBackReturnsToOrigin() = audit {
        tab(SearchDestination); go(AlbumDestination("audit-album"))
        assertTrue(back())
        assertTrue(nav.currentDestination!!.hasRoute<SearchDestination>())
    }

    @Test fun reselectAtRootWithSingleTopDoesNotDuplicateRoot() = audit {
        tab(LibraryDestination); tab(LibraryDestination)
        assertEquals(1, nav.currentBackStack.value.count { it.destination.hasRoute<LibraryDestination>() })
    }

    @Test fun tabSwitchKeepsDetailHistoryForReturn() = audit {
        tab(SearchDestination); go(AlbumDestination("audit-album"))
        tab(FriendsDestination); tab(SearchDestination)
        assertTrue(nav.currentDestination!!.hasRoute<AlbumDestination>())
        assertTrue(back())
        assertTrue(nav.currentDestination!!.hasRoute<SearchDestination>())
    }

    @Test fun playerDismissesBeforeArtistNavigationAndRepeatedTapKeepsOneDetail() = audit {
        var dismissedAt = ""
        nav.navigateFromPlayer(ArtistDestination("artist")) {
            dismissedAt = nav.currentDestination!!.route.orEmpty()
        }
        complete()
        assertTrue(dismissedAt.contains("HomeDestination"))
        assertTrue(nav.currentDestination!!.hasRoute<ArtistDestination>())
        nav.navigateFromPlayer(ArtistDestination("artist")) {}; complete()
        assertEquals(1, nav.currentBackStack.value.count { it.destination.hasRoute<ArtistDestination>() })
    }

    @Test fun offlineAndMissingCloudSessionNeverAuthorizeSocialActions() {
        val online = com.tan.data.sync.AccountAccess.Ready("one", false, false)
        assertTrue(isSocialAccountAvailable(online, "one"))
        assertFalse(isSocialAccountAvailable(online, null))
        assertFalse(isSocialAccountAvailable(online, "two"))
        assertFalse(isSocialAccountAvailable(online.copy(offline = true), "one"))
        assertFalse(isSocialAccountAvailable(com.tan.data.sync.AccountAccess.SignedOut, "one"))
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test fun actualBottomBarHighlightsSearchAfterRestoringSearchAlbum(): Unit = runBlocking(Dispatchers.Swing) {
        val fixture = Fixture()
        val scene = ImageComposeScene(420, 100, Density(1f), coroutineContext = coroutineContext) {
            MaterialTheme { AppBottomNavigationBar(navController = fixture.nav) }
        }
        var frame = 0L
        suspend fun settle() { repeat(8) { scene.render(frame++ * 16_000_000L).close(); delay(16) } }
        fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
        fun selected() = scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }
            .mapNotNull { it.config.getOrNull(SemanticsProperties.Selected) }
        try {
            fixture.complete(); settle()
            fixture.tab(SearchDestination); settle()
            assertEquals(listOf(false, true, false, false), selected())
            fixture.go(AlbumDestination("audit-album")); settle()
            fixture.tab(LibraryDestination); settle()
            assertEquals(listOf(false, false, true, false), selected())
            fixture.tab(SearchDestination); settle()
            assertTrue(fixture.nav.currentDestination!!.hasRoute<AlbumDestination>())
            assertEquals(listOf(false, true, false, false), selected())
        } finally { scene.close(); fixture.close() }
    }
}
