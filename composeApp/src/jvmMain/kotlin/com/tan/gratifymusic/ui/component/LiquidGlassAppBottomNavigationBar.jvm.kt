package com.tan.gratifymusic.ui.component

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.tan.gratifymusic.expect.ui.PlatformBackdrop
import com.tan.gratifymusic.viewModel.SharedViewModel
import kotlin.reflect.KClass

@Composable
actual fun LiquidGlassAppBottomNavigationBar(
    startDestination: Any,
    navController: NavController,
    backdrop: PlatformBackdrop,
    viewModel: SharedViewModel,
    isScrolledToTop: Boolean,
    onOpenNowPlaying: () -> Unit,
    reloadDestinationIfNeeded: (KClass<*>) -> Unit
) {
}