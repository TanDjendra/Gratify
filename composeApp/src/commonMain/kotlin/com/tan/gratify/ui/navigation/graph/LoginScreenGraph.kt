package com.tan.gratify.ui.navigation.graph

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.tan.gratify.ui.navigation.destination.login.EmailLoginDestination
import com.tan.gratify.ui.navigation.destination.login.ForgotPasswordDestination
import com.tan.gratify.ui.navigation.destination.login.LoginDestination
import com.tan.gratify.ui.navigation.destination.login.LoginLandingDestination
import com.tan.gratify.ui.navigation.destination.login.SignUpDestination
import com.tan.gratify.ui.navigation.destination.login.CreateProfileDestination
import com.tan.gratify.ui.navigation.destination.login.SplashDestination
import com.tan.gratify.ui.screen.login.EmailLoginScreen
import com.tan.gratify.ui.screen.login.ForgotPasswordScreen
import com.tan.gratify.ui.screen.login.LoginLandingScreen
import com.tan.gratify.ui.screen.login.LoginScreen
import com.tan.gratify.ui.screen.login.SignUpScreen
import com.tan.gratify.ui.screen.login.CreateProfileScreen
import com.tan.gratify.ui.screen.login.SplashScreen

fun NavGraphBuilder.loginScreenGraph(
    innerPadding: PaddingValues,
    navController: NavController,
    hideBottomBar: () -> Unit,
    showBottomBar: () -> Unit,
) {
    // ── Splash ────────────────────────────────────────────────────────────────
    composable<SplashDestination> {
        SplashScreen(navController = navController)
    }

    // ── Login Landing (onboarding / sign-up entry point) ──────────────────────
    composable<LoginLandingDestination> {
        LoginLandingScreen(
            navController = navController,
            innerPadding = innerPadding,
            hideBottomNavigation = hideBottomBar,
            showBottomNavigation = showBottomBar,
        )
    }

    // ── Sign Up Flow ──────────────────────────────────────────────────────────
    composable<SignUpDestination> {
        SignUpScreen(
            innerPadding = innerPadding,
            navController = navController,
            hideBottomNavigation = hideBottomBar,
            showBottomNavigation = showBottomBar,
        )
    }

    // ── Create Profile Flow ───────────────────────────────────────────────────
    composable<CreateProfileDestination> {
        CreateProfileScreen(
            innerPadding = innerPadding,
            navController = navController,
            hideBottomNavigation = hideBottomBar,
            showBottomNavigation = showBottomBar,
        )
    }

    // ── Email Login Flow (email + password) ───────────────────────────────────
    composable<EmailLoginDestination> {
        EmailLoginScreen(
            innerPadding = innerPadding,
            navController = navController,
            hideBottomNavigation = hideBottomBar,
            showBottomNavigation = showBottomBar,
        )
    }

    // ── Forgot Password Flow ──────────────────────────────────────────────────
    composable<ForgotPasswordDestination> {
        ForgotPasswordScreen(
            innerPadding = innerPadding,
            navController = navController,
            hideBottomNavigation = hideBottomBar,
            showBottomNavigation = showBottomBar,
        )
    }

    // ── YouTube Music web-view login ──────────────────────────────────────────
    composable<LoginDestination> {
        LoginScreen(
            innerPadding = innerPadding,
            navController = navController,
            hideBottomNavigation = hideBottomBar,
            showBottomNavigation = showBottomBar,
        )
    }

}