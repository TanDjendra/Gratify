package com.tan.gratify.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.Libs
import com.tan.gratify.ui.screen.home.*
import com.tan.gratify.ui.theme.AppTheme
import gratify.composeapp.generated.resources.Res
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.jetbrains.compose.resources.ExperimentalResourceApi
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, ExperimentalResourceApi::class)
class AboutSupportTest {
    @Test fun versionNameIsAvailableWithoutAnActivityInitializer() {
        assertTrue(com.tan.gratify.utils.VersionManager.getVersionName().isNotBlank())
    }
    private fun tree(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::tree)
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { tree(it.rootSemanticsNode) }
    private fun texts(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()
    private fun click(scene: ImageComposeScene, title: String) = nodes(scene)
        .first { title in texts(it) && it.config.contains(SemanticsActions.OnClick) }
        .config[SemanticsActions.OnClick].action!!.invoke()
    private suspend fun settle(scene: ImageComposeScene, start: Long = 0) {
        repeat(22) { scene.render(start + it * 16_000_000L).close(); delay(16) }
    }
    private fun snapshot(scene: ImageComposeScene, name: String) {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first { Files.exists(it.resolve("settings.gradle.kts")) }
        val path = root.resolve("artifacts/about-support-2026-10-08/$name.png")
        Files.createDirectories(path.parent)
        scene.render(8_000_000_000L).use { it.encodeToData()!!.use { data -> Files.write(path, data.bytes) } }
    }
    @Test fun versionCopiesInformationAndDeveloperHasAnExplicitEntry(): Unit = runBlocking(Dispatchers.Swing) {
        val copies = mutableListOf<String>(); val links = mutableListOf<String>()
        var developer = 0; var checks = 0; var automatic by mutableStateOf(false)
        val scene = ImageComposeScene(390, 1200, Density(1f), coroutineContext = coroutineContext) {
            AppTheme { AboutSupportContent("1.2.3", 42, "Not checked yet", false, automatic,
                { automatic = it }, { checks++ }, { developer++ }, links::add, copies::add,
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) }
        }
        try {
            settle(scene); click(scene, "Version"); settle(scene, 1_000_000_000L)
            assertEquals(listOf("Gratify v1.2.3 (build 42)"), copies); assertEquals(0, developer)
            assertTrue(nodes(scene).any { "Version information copied." in texts(it) })
            click(scene, "Developer & credits"); assertEquals(1, developer)
            click(scene, "Check for update"); assertEquals(1, checks)
            click(scene, "Automatic check for update"); assertTrue(automatic)
            click(scene, "Get help or report a problem")
            assertEquals(listOf(aboutSupportUrl("1.2.3", 42)), links)
            assertTrue(links.single().startsWith("mailto:supportgratify@gmail.com?"))
            assertFalse(links.single().contains(" "))
            snapshot(scene, "about-support-390")
        } finally { scene.close() }
    }
    @Test fun missingExternalAppsAndClipboardShowRecoverableMessages(): Unit = runBlocking(Dispatchers.Swing) {
        val scene = ImageComposeScene(390, 1200, Density(1f), coroutineContext = coroutineContext) {
            AppTheme { AboutSupportContent("1.2.3", 42, "Checking", true, false, {}, {}, {},
                { error("Synthetic missing handler") }, { error("Synthetic missing clipboard") },
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) }
        }
        try {
            settle(scene)
            assertTrue(nodes(scene).first { "Check for update" in texts(it) && it.config.contains(SemanticsActions.OnClick) }
                .config.contains(SemanticsProperties.Disabled))
            click(scene, "Version"); click(scene, "Get help or report a problem"); settle(scene, 1_000_000_000L)
            assertTrue(nodes(scene).any { "Could not copy. Try again." in texts(it) })
            assertTrue(nodes(scene).any { texts(it).any { text -> text.startsWith("Could not open the link.") } })
            assertTrue(nodes(scene).any { "supportgratify@gmail.com" in texts(it) })
            snapshot(scene, "about-fallbacks")
        } finally { scene.close() }
    }
    @Test fun developerActionsAndCreditsRemainReadableOnSmallAndWideScreens(): Unit = runBlocking(Dispatchers.Swing) {
        for ((width, scale) in listOf(390 to 1f, 320 to 1.5f, 1100 to 1f)) {
            val links = mutableListOf<String>(); var licenses = 0
            val scene = ImageComposeScene(width, 900, Density(1f, scale), coroutineContext = coroutineContext) {
                AppTheme { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    DeveloperCreditsContent(links::add, { licenses++ }, Modifier.widthIn(max = 760.dp).fillMaxWidth()
                        .verticalScroll(rememberScrollState()).padding(16.dp))
                } }
            }
            try {
                settle(scene)
                assertTrue(nodes(scene).any { "Tan" in texts(it) })
                assertTrue(nodes(scene).any { texts(it).any { text -> text.contains("modified version of SimpMusic") } })
                assertFalse(nodes(scene).any { "SimpMusic" in texts(it) && it.config.contains(SemanticsActions.OnClick) })
                for (title in listOf("Developer website", "GitHub", "Instagram", "Gratify source code", "Gratify license", "Saweria")) {
                    assertTrue(click(scene, title))
                }
                assertEquals(listOf("https://tanheradhe.my.id", "https://github.com/TanDjendra", "https://instagram.com/calestaan",
                    "https://github.com/TanDjendra/Gratify",
                    "https://github.com/TanDjendra/Gratify/blob/main/LICENSE", "https://saweria.co/Tanindomie"), links)
                click(scene, "Libraries & licenses"); assertEquals(1, licenses)
                nodes(scene).filter { texts(it).isNotEmpty() && it.boundsInRoot.width > 0 }.forEach {
                    assertTrue(it.boundsInRoot.left >= 0f && it.boundsInRoot.right <= width.toFloat(), "Text must stay within the screen")
                }
                snapshot(scene, "developer-$width")
            } finally { scene.close() }
        }
    }
    @Test fun bundledLibrariesLoadAndDisplayLicenseInformation(): Unit = runBlocking(Dispatchers.Swing) {
        val libraries = Libs.Builder().withJson(Res.readBytes("files/aboutlibraries.json").decodeToString()).build()
        assertTrue(libraries.libraries.size > 100)
        assertTrue(libraries.licenses.isNotEmpty())
        val scene = ImageComposeScene(390, 900, Density(1f), coroutineContext = coroutineContext) {
            AppTheme { OpenSourceContent(libraries, false, {}, Modifier.fillMaxSize()) }
        }
        try {
            settle(scene)
            assertTrue(nodes(scene).any { libraries.libraries.first().name in texts(it) })
            snapshot(scene, "library-licenses")
        } finally { scene.close() }
    }
    @Test fun libraryReadFailureOffersRetry(): Unit = runBlocking(Dispatchers.Swing) {
        var retries = 0
        val scene = ImageComposeScene(320, 600, Density(1f, 1.5f), coroutineContext = coroutineContext) {
            AppTheme { OpenSourceContent(null, true, { retries++ }, Modifier.fillMaxSize()) }
        }
        try {
            settle(scene); click(scene, "Retry"); assertEquals(1, retries)
            snapshot(scene, "licenses-error")
        } finally { scene.close() }
    }

    @Test fun supportActionsFitSmallAndWideScreens(): Unit = runBlocking(Dispatchers.Swing) {
        for ((width, scale) in listOf(320 to 1.5f, 1100 to 1f)) {
            val scene = ImageComposeScene(width, 900, Density(1f, scale), coroutineContext = coroutineContext) {
                AppTheme { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    AboutSupportContent("1.2.3", 42, "Not checked yet", false, false, {}, {}, {}, {}, {},
                        Modifier.widthIn(max = 760.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp))
                } }
            }
            try {
                settle(scene)
                val version = nodes(scene).first { "Version" in texts(it) && it.config.contains(SemanticsActions.OnClick) }
                assertTrue(version.boundsInRoot.height >= 48f)
                nodes(scene).filter { texts(it).isNotEmpty() && it.boundsInRoot.width > 0 }.forEach {
                    assertTrue(it.boundsInRoot.left >= 0f && it.boundsInRoot.right <= width.toFloat())
                }
                snapshot(scene, "about-support-$width")
            } finally { scene.close() }
        }
    }
}
