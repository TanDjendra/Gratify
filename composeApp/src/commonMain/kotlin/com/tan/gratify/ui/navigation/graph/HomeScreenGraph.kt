package com.tan.gratify.ui.navigation.graph

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.tan.gratify.ui.navigation.destination.home.AnalyticsDestination
import com.tan.gratify.ui.navigation.destination.home.CreditDestination
import com.tan.gratify.ui.navigation.destination.home.MoodDestination
import com.tan.gratify.ui.navigation.destination.home.NotificationDestination
import com.tan.gratify.ui.navigation.destination.home.RecentlySongsDestination
import com.tan.gratify.ui.navigation.destination.home.SettingsDestination
import com.tan.gratify.ui.screen.home.MoodScreen
import com.tan.gratify.ui.screen.home.NotificationScreen
import com.tan.gratify.ui.screen.home.RecentlySongsScreen
import com.tan.gratify.ui.screen.home.SettingScreen
import com.tan.gratify.ui.screen.home.analytics.AnalyticsScreen
import com.tan.gratify.ui.screen.home.CreditScreen

fun NavGraphBuilder.homeScreenGraph(
    innerPadding: PaddingValues,
    navController: NavController,
) {
    composable<CreditDestination> {
        CreditScreen(
            paddingValues = innerPadding,
            navController = navController,
        )
    }
    composable<MoodDestination> { entry ->
        val params = entry.toRoute<MoodDestination>().params
        MoodScreen(
            navController = navController,
            params = params,
        )
    }
    composable<NotificationDestination> {
        NotificationScreen(
            navController = navController,
        )
    }
    composable<RecentlySongsDestination> {
        RecentlySongsScreen(
            navController = navController,
            innerPadding = innerPadding,
        )
    }
    composable<SettingsDestination> {
        SettingScreen(
            navController = navController,
            innerPadding = innerPadding,
        )
    }
    composable<AnalyticsDestination> {
        AnalyticsScreen(
            navController = navController,
            innerPadding = innerPadding,
        )
    }
}