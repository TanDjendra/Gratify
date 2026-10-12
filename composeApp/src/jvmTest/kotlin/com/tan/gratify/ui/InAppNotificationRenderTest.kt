package com.tan.gratify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.tan.gratify.ui.component.*
import com.tan.gratify.ui.theme.*
import com.tan.gratify.viewModel.InAppNotificationKind
import com.tan.gratify.viewModel.SnackbarEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class InAppNotificationRenderTest {
    private val follower = SnackbarEvent(
        title = "Pengikut baru", message = "Nara mulai mengikuti kamu.",
        actionLabel = "Lihat", kind = InAppNotificationKind.FOLLOWER, avatarName = "Nara",
    )
    private fun tree(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::tree)
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { tree(it.rootSemanticsNode) }
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
    private suspend fun settle(scene: ImageComposeScene, start: Long = 0) {
        repeat(24) { scene.render(start + it * 16_000_000L).close(); delay(16) }
    }
    private fun capture(scene: ImageComposeScene, name: String) {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first { Files.exists(it.resolve("settings.gradle.kts")) }
        val path = root.resolve("artifacts/notifications/$name.png")
        Files.createDirectories(path.parent)
        scene.render(2_000_000_000L).use { image -> image.encodeToData()!!.use { Files.write(path, it.bytes) } }
    }

    @Test
    fun actionsFitSmallScreensAndLargeText(): Unit = runBlocking(Dispatchers.Swing) {
        for ((width, scale) in listOf(320 to 1f, 360 to 1.5f, 900 to 1f)) {
            var opened = 0
            var dismissed = 0
            val scene = ImageComposeScene(width, 700, Density(1f, scale), coroutineContext = coroutineContext) {
                AppTheme {
                    Box(Modifier.fillMaxSize().background(GratifyColors.Background).padding(16.dp)) {
                        InAppNotificationCard(follower, { dismissed++ }, { opened++ }, Modifier.align(Alignment.TopCenter))
                    }
                }
            }
            try {
                settle(scene)
                val action = nodes(scene).first { text(it) == "Lihat" && it.config.contains(SemanticsActions.OnClick) }
                assertTrue(action.boundsInRoot.left >= 16 && action.boundsInRoot.right <= width - 16)
                assertTrue(action.boundsInRoot.height >= 48f)
                action.config[SemanticsActions.OnClick].action!!.invoke()
                val close = nodes(scene).first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { description -> description.contains("notification", true) || description.contains("notifikasi", true) } == true }
                close.config[SemanticsActions.OnClick].action!!.invoke()
                assertEquals(1, opened)
                assertEquals(1, dismissed)
                capture(scene, "popup-$width")
            } finally { scene.close() }
        }
    }

    @Test
    fun hostPreservesActionAndDismissResults(): Unit = runBlocking(Dispatchers.Swing) {
        val host = SnackbarHostState()
        val scene = ImageComposeScene(390, 700, Density(1f), coroutineContext = coroutineContext) {
            AppTheme { Box(Modifier.padding(16.dp)) { InAppNotificationHost(host) } }
        }
        try {
            val first = async { host.showSnackbar(InAppNotificationVisuals(follower)) }
            settle(scene)
            nodes(scene).first { text(it) == "Lihat" && it.config.contains(SemanticsActions.OnClick) }
                .config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals(SnackbarResult.ActionPerformed, first.await())
            val second = async { host.showSnackbar(InAppNotificationVisuals(follower.copy(actionLabel = null))) }
            settle(scene, 1_000_000_000L)
            assertFalse(nodes(scene).any { text(it) == "Lihat" })
            host.currentSnackbarData!!.dismiss()
            assertEquals(SnackbarResult.Dismissed, second.await())
        } finally { scene.close() }
    }

    @Test
    fun renderNotificationStyles(): Unit = runBlocking(Dispatchers.Swing) {
        val scene = ImageComposeScene(440, 820, Density(1f), coroutineContext = coroutineContext) {
            AppTheme {
                Column(Modifier.fillMaxSize().background(GratifyColors.Background).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("Notifikasi pop-up", style = typo().titleLarge, color = GratifyColors.TextPrimary)
                    Text("Gratify · Pratinjau komponen", style = typo().bodySmall, color = GratifyColors.TextSecondary)
                    InAppNotificationCard(follower, {}, {})
                    InAppNotificationCard(SnackbarEvent(title = "Musik siap dibawa", message = "Unduhan selesai. Dengarkan kapan saja, bahkan saat offline.", kind = InAppNotificationKind.SUCCESS), {}, {})
                    InAppNotificationCard(SnackbarEvent(title = "Koneksi terputus", message = "Periksa koneksi internetmu, lalu coba lagi.", actionLabel = "Coba lagi", kind = InAppNotificationKind.ERROR), {}, {})
                }
            }
        }
        try { settle(scene); capture(scene, "card-preview") } finally { scene.close() }
    }
}
