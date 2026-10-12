package com.tan.gratify.ui.screen.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.tan.gratify.ui.theme.*
import gratify.composeapp.generated.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalResourceApi::class)
@Composable
fun OpenSourceScreen(paddingValues: PaddingValues, navController: NavController) {
    var libraries by remember { mutableStateOf<Libs?>(null) }
    var failed by remember { mutableStateOf(false) }
    var retry by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(retry) {
        failed = false
        try {
            libraries = withContext(Dispatchers.Default) {
                Libs.Builder().withJson(Res.readBytes("files/aboutlibraries.json").decodeToString()).build()
                    .also { require(it.libraries.isNotEmpty()) }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    Scaffold(modifier = Modifier.padding(paddingValues), topBar = {
        TopAppBar(title = { Text(stringResource(Res.string.about_library_licenses), style = typo().titleMedium) }, navigationIcon = {
            IconButton(onClick = { navController.navigateUp() }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.ui_back))
            }
        })
    }) { contentPadding ->
        Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.TopCenter) {
            OpenSourceContent(libraries, failed, { retry++ }, Modifier.widthIn(max = 760.dp).fillMaxSize())
        }
    }
}

@Composable
internal fun OpenSourceContent(libraries: Libs?, failed: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    var failedUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val safeUriHandler = remember(uriHandler) {
        object : UriHandler {
            override fun openUri(uri: String) {
                failedUrl = if (runCatching { uriHandler.openUri(uri) }.isFailure) uri else null
            }
        }
    }
    Column(modifier) {
        Text(stringResource(Res.string.about_library_hint), style = typo().bodyMedium, color = GratifyColors.TextSecondary,
            modifier = Modifier.padding(16.dp))
        failedUrl?.let { url -> SelectionContainer {
            Text("${stringResource(Res.string.about_link_failed)}\n$url", style = typo().bodySmall,
                color = GratifyColors.Error, modifier = Modifier.padding(16.dp))
        } }
        if (failed) {
            Text(stringResource(Res.string.about_licenses_failed), color = GratifyColors.Error, modifier = Modifier.padding(16.dp))
            TextButton(onClick = onRetry, modifier = Modifier.padding(horizontal = 16.dp).heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.retry))
            }
        } else if (libraries == null) {
            Text(stringResource(Res.string.about_licenses_loading), color = GratifyColors.TextSecondary, modifier = Modifier.padding(16.dp))
        } else CompositionLocalProvider(LocalUriHandler provides safeUriHandler) {
            LibrariesContainer(libraries = libraries, modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp), showAuthor = true, showVersion = true,
                showDescription = false, showLicenseBadges = true)
        }
    }
}
