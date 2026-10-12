package com.tan.gratify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.*
import com.tan.gratify.ui.navigation.destination.home.SettingsDestination
import com.tan.gratify.ui.screen.home.SettingScreen
import com.tan.gratify.ui.theme.AppTheme

/** Debug-only production Settings screen. Visual audit uses navigation and search, without changing preferences. */
class SettingsPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { AppTheme {
            Box(Modifier.fillMaxSize().systemBarsPadding()) {
                val navigation=rememberNavController()
                NavHost(navigation,startDestination=SettingsDestination) {
                    composable<SettingsDestination> { SettingScreen(PaddingValues(), navigation) }
                    composable<com.tan.gratify.ui.navigation.destination.home.CreditDestination> {
                        com.tan.gratify.ui.screen.home.CreditScreen(PaddingValues(), navigation)
                    }
                    composable<com.tan.gratify.ui.navigation.destination.home.OpenSourceDestination> {
                        com.tan.gratify.ui.screen.home.OpenSourceScreen(PaddingValues(), navigation)
                    }
                }
            }
        } }
    }
}
