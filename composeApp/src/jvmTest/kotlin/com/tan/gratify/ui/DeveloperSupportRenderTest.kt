package com.tan.gratify.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.tan.gratify.ui.component.*
import com.tan.gratify.ui.theme.AppTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.nio.file.*
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class DeveloperSupportRenderTest {
    private fun tree(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::tree)
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { tree(it.rootSemanticsNode) }
    private fun texts(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()
    private fun button(scene: ImageComposeScene, label: String) = nodes(scene).first { label in texts(it) && it.config.contains(SemanticsActions.OnClick) }
    private suspend fun settle(scene: ImageComposeScene, time: Long = 0) { repeat(20) { scene.render(time + it * 16_000_000L).close(); delay(16) } }
    private suspend fun reveal(scene: ImageComposeScene, label: String): SemanticsNode {
        repeat(8) { step ->
            val action = button(scene, label)
            if (action.boundsInRoot.height >= if (label == "Maybe later") 48 else 52) return action
            nodes(scene).first { it.config.contains(SemanticsActions.ScrollBy) }
                .config[SemanticsActions.ScrollBy].action!!.invoke(0f, 140f)
            settle(scene, (step + 2) * 1_000_000_000L)
        }
        error("Action is not reachable by scrolling: $label")
    }
    private fun snapshot(scene: ImageComposeScene, name: String) {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first { Files.exists(it.resolve("settings.gradle.kts")) }
        val path = root.resolve("artifacts/support-popup-2026-10-10/$name.png"); Files.createDirectories(path.parent)
        scene.render(8_000_000_000L).use { it.encodeToData()!!.use { data -> Files.write(path, data.bytes) } }
    }

    @Test fun nativeActionsAndOptOutRemainAccessibleAcrossSizes() = runBlocking(Dispatchers.Swing) {
        for ((width, height, scale) in listOf(Triple(390, 780, 1f), Triple(320, 780, 1.5f), Triple(1100, 900, 1f))) {
            var optedOut by mutableStateOf(false); var changes = 0; var dismissals = 0
            val urls = mutableListOf<String>()
            val scene = ImageComposeScene(width, height, Density(1f, scale), coroutineContext = coroutineContext) {
                AppTheme { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    DeveloperSupportContent(optedOut, { optedOut = it; changes++ }, { dismissals++ }, { urls += it },
                        Modifier.widthIn(max = 480.dp).fillMaxHeight(.88f))
                } }
            }
            try {
                settle(scene)
                assertTrue(nodes(scene).any { it.config.contains(SemanticsProperties.Heading) })
                val check = nodes(scene).single { it.config.getOrNull(SemanticsProperties.Role) == Role.Checkbox && it.config.contains(SemanticsActions.OnClick) }
                assertTrue(check.boundsInRoot.height >= 48)
                assertTrue(check.boundsInRoot.bottom <= height + 1)
                check.config[SemanticsActions.OnClick].action!!.invoke(); settle(scene, 1_000_000_000L)
                assertTrue(optedOut); assertEquals(1, changes)
                for ((label, url) in listOf("Instagram · @calestaan" to DEVELOPER_INSTAGRAM_URL, "GitHub · TanDjendra" to DEVELOPER_GITHUB_URL)) {
                    val action = reveal(scene, label)
                    assertTrue(action.boundsInRoot.width <= 432 + 1)
                    assertTrue(action.boundsInRoot.height >= 52)
                    action.config[SemanticsActions.OnClick].action!!.invoke()
                    assertEquals(url, urls.last())
                }
                reveal(scene, "Maybe later").config[SemanticsActions.OnClick].action!!.invoke()
                assertEquals(3, dismissals)
                snapshot(scene, "support-$width")
            } finally { scene.close() }
        }
    }

    @Test fun unavailableLinkKeepsSheetOpenAndShowsCopyableAddress() = runBlocking(Dispatchers.Swing) {
        var dismissals = 0
        val scene = ImageComposeScene(390, 1000, Density(1f), coroutineContext = coroutineContext) {
            AppTheme { DeveloperSupportContent(false, {}, { dismissals++ }, { error("No handler") }) }
        }
        try {
            settle(scene)
            button(scene, "Instagram · @calestaan").config[SemanticsActions.OnClick].action!!.invoke()
            settle(scene, 1_000_000_000L)
            assertEquals(0, dismissals)
            assertTrue(nodes(scene).any { DEVELOPER_INSTAGRAM_URL in texts(it) })
            assertTrue(nodes(scene).any { it.config.getOrNull(SemanticsProperties.LiveRegion) == LiveRegionMode.Polite })
            snapshot(scene, "support-link-fallback")
        } finally { scene.close() }
    }
}
