package com.tan.gratify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.tan.domain.repository.YouTubeConnectionStatus
import com.tan.gratify.ui.component.YouTubeConnectionCard
import com.tan.gratify.ui.theme.*
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class YouTubeConnectionRenderTest {
    private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
    @Test fun connectionStatesHaveUsableActionsAtPhoneWidthWithLargeText(): Unit = runBlocking(Dispatchers.Swing) {
        for (status in YouTubeConnectionStatus.entries) {
            var connections = 0; var retries = 0; var disconnects = 0
            val scene = ImageComposeScene(390, 860, Density(1f, 1.3f), coroutineContext = coroutineContext) {
                AppTheme {
                    Column(Modifier.fillMaxSize().background(GratifyColors.Background).padding(16.dp)) {
                        YouTubeConnectionCard(status, { connections++ }, { retries++ }, onDisconnect = { disconnects++ })
                    }
                }
            }
            try {
                repeat(15) { scene.render(it * 16_000_000L).close(); delay(16) }
                val buttons = scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }
                    .filter { it.config.contains(SemanticsActions.OnClick) }
                if (status == YouTubeConnectionStatus.LOADING) assertTrue(buttons.isEmpty())
                else {
                    assertEquals(1, buttons.size)
                    assertTrue(buttons.all { it.boundsInRoot.left >= 0 && it.boundsInRoot.right <= 390 && it.boundsInRoot.bottom <= 860 })
                    buttons.single().config[SemanticsActions.OnClick].action!!.invoke()
                    when (status) {
                        YouTubeConnectionStatus.CONNECTED -> assertEquals(1, disconnects)
                        YouTubeConnectionStatus.CONFIGURATION_REQUIRED, YouTubeConnectionStatus.ERROR -> assertEquals(1, retries)
                        else -> assertEquals(1, connections)
                    }
                }
                if (status == YouTubeConnectionStatus.DISCONNECTED || status == YouTubeConnectionStatus.CONFIGURATION_REQUIRED) {
                    val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                        .first { Files.exists(it.resolve("settings.gradle.kts")) }
                    val output = root.resolve("artifacts/youtube-oauth/connection-${status.name.lowercase()}.png")
                    Files.createDirectories(output.parent)
                    scene.render(1_000_000_000L).use { it.encodeToData()!!.use { data -> Files.write(output, data.bytes) } }
                }
            } finally { scene.close() }
        }
    }
}
