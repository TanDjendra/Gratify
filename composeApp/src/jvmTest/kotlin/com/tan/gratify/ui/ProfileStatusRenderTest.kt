package com.tan.gratify.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import com.tan.gratify.ui.screen.social.ProfileLoadStatus
import com.tan.gratify.ui.theme.AppTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class ProfileStatusRenderTest {
    private fun nodes(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::nodes)
    private fun all(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }
    private suspend fun settle(scene: ImageComposeScene) { repeat(12) { scene.render(it * 16_000_000L).close(); delay(16) } }
    @Test fun failureKeepsBackRetryAndReadableTextOnSmallScreens(): Unit = runBlocking(Dispatchers.Swing) {
        var back = 0; var retry = 0
        val message = "Profil belum dapat dimuat. Periksa koneksi dan coba lagi."
        val scene = ImageComposeScene(320, 600, Density(1f, 1.5f), coroutineContext = coroutineContext) {
            AppTheme { ProfileLoadStatus(false, message, { back++ }, { retry++ }) }
        }
        try {
            settle(scene)
            val texts = all(scene).filter { it.config.contains(SemanticsProperties.Text) }
            val error = texts.first { it.config[SemanticsProperties.Text].any { it.text == message } }
            assertTrue(error.boundsInRoot.left >= 0 && error.boundsInRoot.right <= 320)
            val backNode = all(scene).first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Kembali") == true && it.config.contains(SemanticsActions.OnClick) }
            backNode.config[SemanticsActions.OnClick].action!!.invoke()
            val retryNode = texts.first { it.config[SemanticsProperties.Text].any { it.text == "Coba lagi" } && it.config.contains(SemanticsActions.OnClick) }
            retryNode.config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals(1, back); assertEquals(1, retry)
            val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first { Files.exists(it.resolve("settings.gradle.kts")) }
            val file = root.resolve("artifacts/profile-fix/profile-error-320.png"); Files.createDirectories(file.parent)
            scene.render(1_000_000_000L).use { it.encodeToData()!!.use { data -> Files.write(file, data.bytes) } }
        } finally { scene.close() }
    }
    @Test fun missingAccountHasNoMisleadingRetryAction(): Unit = runBlocking(Dispatchers.Swing) {
        val scene = ImageComposeScene(360, 600, Density(1f), coroutineContext = coroutineContext) {
            AppTheme { ProfileLoadStatus(false, "Pengguna tidak ditemukan", {}, {}, retryAvailable = false) }
        }
        try {
            settle(scene)
            assertFalse(all(scene).any { it.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Coba lagi" } == true })
            assertTrue(all(scene).any { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Kembali") == true })
        } finally { scene.close() }
    }
    @Test fun identityHeaderFitsLongNamesAndLargeTextWithoutOverlappingActions(): Unit = runBlocking(Dispatchers.Swing) {
        for ((width, scale) in listOf(320 to 1.5f, 360 to 2f, 900 to 1.3f)) {
            var opened = ""
            val followers = "99999 pengikut terlihat"
            val scene = ImageComposeScene(width, 900, Density(1f, scale), coroutineContext = coroutineContext) {
                AppTheme { androidx.compose.foundation.layout.Column {
                    com.tan.gratify.ui.component.ProfileIdentityHeader("Nama Pengguna Gratify Yang Panjang Sekali", null,
                        followers, "Memuat mengikuti…", true, { opened = "back" }, { opened = "followers" }, { opened = "following" })
                } }
            }
            try {
                settle(scene)
                val labels = all(scene).filter { it.config.contains(SemanticsProperties.Text) }
                assertTrue(labels.all { it.boundsInRoot.left >= 0 && it.boundsInRoot.right <= width }, "Overflow at $width/$scale")
                val counter = labels.first { it.config[SemanticsProperties.Text].any { it.text == followers } && it.config.contains(SemanticsActions.OnClick) }
                counter.config[SemanticsActions.OnClick].action!!.invoke(); assertEquals("followers", opened)
                val back = all(scene).first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Kembali") == true && it.config.contains(SemanticsActions.OnClick) }
                assertTrue(counter.boundsInRoot.top >= back.boundsInRoot.bottom)
                val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first { Files.exists(it.resolve("settings.gradle.kts")) }
                val file = root.resolve("artifacts/profile-fix/profile-header-$width.png"); Files.createDirectories(file.parent)
                scene.render(1_000_000_000L).use { it.encodeToData()!!.use { data -> Files.write(file, data.bytes) } }
            } finally { scene.close() }
        }
    }

}
