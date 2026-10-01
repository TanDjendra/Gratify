package com.tan.gratify.ui.component

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.tan.gratify.expect.ui.PlatformBackdrop
import com.tan.gratify.ui.navigation.destination.home.HomeDestination
import com.tan.gratify.viewModel.SharedViewModel
import kotlin.reflect.KClass

@Composable
expect fun LiquidGlassAppBottomNavigationBar(
    startDestination: Any = HomeDestination,
    navController: NavController,
    backdrop: PlatformBackdrop,
    viewModel: SharedViewModel,
    isScrolledToTop: Boolean = false,
    onOpenNowPlaying: () -> Unit = {},
    reloadDestinationIfNeeded: (KClass<*>) -> Unit = { _ -> },
)
