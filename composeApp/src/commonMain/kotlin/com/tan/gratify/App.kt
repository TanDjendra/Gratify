package com.tan.gratify

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tan.gratify.ui.navigation.destination.MainDestination
import com.tan.gratify.ui.navigation.destination.login.LoginLandingDestination
import com.tan.gratify.ui.navigation.destination.login.SplashDestination
import com.tan.gratify.ui.navigation.graph.loginScreenGraph
import com.tan.gratify.ui.screen.MainScreen
import com.tan.gratify.ui.theme.AppTheme

/**
 * Root App Composable.
 * Memisahkan secara bersih antara Auth Flow (Splash, Landing, SignUp) dan Main Flow (Aplikasi Utama).
 * Root App tidak lagi memiliki Scaffold atau BottomBar bawaan.
 */
@Composable
fun App() {
    val navController = rememberNavController()

    AppTheme {
        NavHost(
            navController = navController,
            startDestination = SplashDestination,
            enterTransition = {
                fadeIn() + slideInHorizontally { -it }
            },
            exitTransition = {
                fadeOut() + slideOutHorizontally { it }
            },
            popEnterTransition = {
                fadeIn() + slideInHorizontally { -it }
            },
            popExitTransition = {
                fadeOut() + slideOutHorizontally { it }
            },
        ) {
            // ── Auth Flow (Tanpa Bottom Bar) ──────────────────────────────────
            loginScreenGraph(
                innerPadding = PaddingValues(),
                navController = navController,
                hideBottomBar = {},
                showBottomBar = {}
            )

            // ── Main Flow (Dengan Bottom Bar di dalam MainScreen) ─────────────
            composable<MainDestination> {
                MainScreen(
                    onLogOut = {
                        navController.navigate(LoginLandingDestination) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}