package com.tan.gratify.ui.component

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.tan.gratify.expect.ui.PlatformBackdrop
import com.tan.gratify.viewModel.SharedViewModel
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