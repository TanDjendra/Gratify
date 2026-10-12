package com.tan.gratify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.*
import com.tan.gratify.ui.navigation.destination.login.LoginLandingDestination
import com.tan.gratify.ui.navigation.graph.loginScreenGraph
import com.tan.gratify.ui.theme.AppTheme

/** Debug-only production auth navigation. Visual checks never submit real credentials. */
class AuthPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { AppTheme {
            Box(Modifier.fillMaxSize().systemBarsPadding()) {
                val navigation = rememberNavController()
                NavHost(navigation, startDestination = LoginLandingDestination) {
                    loginScreenGraph(PaddingValues(), navigation, {}, {})
                }
            }
        } }
    }
}
