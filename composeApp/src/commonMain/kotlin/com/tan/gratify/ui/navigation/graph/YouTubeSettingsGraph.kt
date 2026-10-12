package com.tan.gratify.ui.navigation.graph

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.tan.gratify.ui.navigation.destination.login.LoginDestination
import com.tan.gratify.ui.screen.login.YouTubeConnectScreen

/** The app's inner host may manage YouTube permission, but never run root onboarding. */
fun NavGraphBuilder.youTubeSettingsGraph(navController: NavController) {
    composable<LoginDestination> { YouTubeConnectScreen(navController = navController, onboarding = false) }
}
