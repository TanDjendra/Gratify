package com.tan.gratify.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.tan.gratify.ui.navigation.destination.home.HomeDestination
import com.tan.gratify.ui.theme.GratifyColors
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.mono
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.reflect.KClass

private val mainTabs = listOf(BottomNavScreen.Home, BottomNavScreen.Search, BottomNavScreen.Library, BottomNavScreen.Friends)

@Composable
private fun rememberSelectedTab(navController: NavController, startDestination: Any): Int {
    val entry by navController.currentBackStackEntryAsState()
    var selected by rememberSaveable {
        mutableIntStateOf(mainTabs.firstOrNull { it.destination::class == startDestination::class }?.ordinal ?: 0)
    }
    LaunchedEffect(entry) {
        mainTabs.firstOrNull { entry?.destination?.hasRoute(it.destination::class) == true }
            ?.let { selected = it.ordinal }
    }
    return selected
}

private fun NavController.openTab(screen: BottomNavScreen, onReselect: (KClass<*>) -> Unit) {
    if (currentDestination?.hasRoute(screen.destination::class) == true) {
        onReselect(screen.destination::class)
    } else {
        navigate(screen.destination) {
            popUpTo(graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}

@Composable
fun AppBottomNavigationBar(
    startDestination: Any = HomeDestination,
    navController: NavController,
    isTranslucentBackground: Boolean = false,
    reloadDestinationIfNeeded: (KClass<*>) -> Unit = {},
) {
    val selected = rememberSelectedTab(navController, startDestination)
    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
        containerColor = GratifyColors.Navigation,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        windowInsets = NavigationBarDefaults.windowInsets,
    ) {
        mainTabs.forEach { screen ->
            NavigationBarItem(
                selected = selected == screen.ordinal,
                onClick = { navController.openTab(screen, reloadDestinationIfNeeded) },
                icon = screen.icon,
                label = {
                    Text(stringResource(screen.title), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

@Composable
fun AppNavigationRail(
    startDestination: Any = HomeDestination,
    navController: NavController,
    reloadDestinationIfNeeded: (KClass<*>) -> Unit = {},
) {
    val selected = rememberSelectedTab(navController, startDestination)
    NavigationRail(
        containerColor = GratifyColors.Navigation,
        contentColor = MaterialTheme.colorScheme.onSurface,
        header = {
            Image(
                painter = painterResource(Res.drawable.mono),
                contentDescription = "Gratify",
                modifier = Modifier.padding(vertical = 20.dp).size(36.dp),
            )
        },
    ) {
        Spacer(Modifier.height(24.dp))
        mainTabs.forEach { screen ->
            NavigationRailItem(
                selected = selected == screen.ordinal,
                onClick = { navController.openTab(screen, reloadDestinationIfNeeded) },
                icon = screen.icon,
                label = { Text(stringResource(screen.title), style = MaterialTheme.typography.labelSmall) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}
