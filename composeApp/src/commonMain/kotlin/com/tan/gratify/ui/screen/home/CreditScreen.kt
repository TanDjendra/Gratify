package com.tan.gratify.ui.screen.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tan.gratify.ui.navigation.destination.home.OpenSourceDestination
import com.tan.gratify.ui.theme.typo
import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditScreen(paddingValues: PaddingValues, navController: NavController) {
    val uriHandler = LocalUriHandler.current
    Scaffold(modifier = Modifier.padding(paddingValues), topBar = {
        TopAppBar(title = { Text(stringResource(Res.string.about_developer_credits), style = typo().titleMedium) },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.ui_back))
                }
            })
    }) { contentPadding ->
        Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.TopCenter) {
            DeveloperCreditsContent(uriHandler::openUri, { navController.navigate(OpenSourceDestination) },
                Modifier.widthIn(max = 760.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp).padding(bottom = 24.dp))
        }
    }
}
