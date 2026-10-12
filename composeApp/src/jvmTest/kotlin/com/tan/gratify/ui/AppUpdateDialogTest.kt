package com.tan.gratify.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import com.tan.domain.data.model.update.UpdateData
import com.tan.gratify.expect.*
import com.tan.gratify.ui.component.*
import com.tan.gratify.ui.theme.AppTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.nio.file.*
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class AppUpdateDialogTest {
    private val update = UpdateData("v2.1.1", null, "Perbaikan pemutaran dan Friends.", "https://github.com/", sizeBytes = 50 * 1024 * 1024L)
    private fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()
    private fun click(scene: ImageComposeScene, label: String) {
        val node = nodes(scene).first { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
    }
    private suspend fun settle(scene: ImageComposeScene) { repeat(35) { scene.render(it * 16_000_000L).close(); delay(12) } }
    private fun capture(scene: ImageComposeScene, name: String) {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first { Files.exists(it.resolve("settings.gradle.kts")) }
        val path = root.resolve("artifacts/auto-update-audit-2026-10-03/$name.png")
        scene.render(1_000_000_000).use { image -> image.encodeToData()!!.use { Files.write(path, it.bytes) } }
    }

    @Test fun postponingAnyActiveStageLeavesTheUpdateAndAppAccessible(): Unit = runBlocking(Dispatchers.Swing) {
        for (stage in listOf(UpdateStage.PREPARING, UpdateStage.DOWNLOADING, UpdateStage.APPLYING, UpdateStage.VERIFYING, UpdateStage.READY, UpdateStage.NEED_PERMISSION, UpdateStage.INSTALLING)) {
            var dismisses = 0; var cancels = 0; var installs = 0
            val scene = ImageComposeScene(360, 820, Density(1f, 1.15f), coroutineContext = coroutineContext) {
                AppTheme { AppUpdateDialog(update, UpdateInstallState(stage, update, 7, 25 * 1024 * 1024L, 50 * 1024 * 1024L),
                    androidInstaller = true, actions = AppUpdateActions({}, { installs++ }, {}, { cancels++ })) { dismisses++ } }
            }
            try {
                settle(scene)
                click(scene, "Nanti")
                assertEquals(1, dismisses); assertEquals(0, cancels); assertEquals(0, installs)
                if (stage == UpdateStage.DOWNLOADING) {
                    assertTrue(nodes(scene).any { text(it).contains("50%") })
                    click(scene, "Batalkan"); assertEquals(1, cancels)
                    capture(scene, "update-downloading")
                }
                if (stage == UpdateStage.READY) { click(scene, "Pasang"); assertEquals(1, installs); capture(scene, "update-ready") }
            } finally { scene.close() }
        }
    }
    @Test fun incompatibleSignerOffersHelpAndDoesNotOfferRetryOrInstall(): Unit = runBlocking(Dispatchers.Swing) {
        val scene = ImageComposeScene(360, 820, Density(1f, 1.15f), coroutineContext = coroutineContext) {
            AppTheme { AppUpdateDialog(update, UpdateInstallState(UpdateStage.ERROR, update, 7,
                message = "Pembaruan memakai tanda tangan berbeda. Jangan hapus aplikasi lama sebelum mencadangkan data.", canRetry = false),
                androidInstaller = true) {} }
        }
        try {
            settle(scene)
            assertTrue(nodes(scene).any { text(it) == "Hubungi dukungan" })
            assertFalse(nodes(scene).any { text(it) == "Coba lagi" || text(it) == "Pasang" })
            assertTrue(nodes(scene).any { text(it).contains("lagu unduhan") })
            capture(scene, "update-incompatible-signer")
        } finally { scene.close() }
    }
    @Test fun failedDeltaOffersFullDownloadOnlyOnExplicitTap(): Unit = runBlocking(Dispatchers.Swing) {
        var downloads = 0
        val selected = update.copy(deltas = listOf(com.tan.domain.data.model.update.UpdateDelta(61, "a".repeat(64), 1000, "url", "b".repeat(64), 10 * 1024 * 1024)))
        val scene = ImageComposeScene(360, 820, Density(1f, 1.15f), coroutineContext = coroutineContext) {
            AppTheme { AppUpdateDialog(selected, UpdateInstallState(UpdateStage.ERROR, selected, 7, totalBytes = 10 * 1024 * 1024,
                message = "Patch belum berhasil. Pilih APK lengkap untuk melanjutkan.", delta = true, canDownloadFull = true),
                androidInstaller = true, actions = AppUpdateActions(download = { assertTrue(it.deltas.isEmpty()); downloads++ })) {} }
        }
        try {
            settle(scene); assertEquals(0, downloads)
            assertTrue(nodes(scene).any { text(it).contains("10 MB") })
            assertTrue(nodes(scene).any { text(it).contains("50 MB") })
            click(scene, "Unduh APK lengkap"); assertEquals(1, downloads)
            capture(scene, "update-delta-fallback")
        } finally { scene.close() }
    }
}
