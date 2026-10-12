package com.tan.gratify.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import com.tan.gratify.ui.screen.player.simp.component.LyricsAvailabilityStatus
import com.tan.gratify.ui.theme.AppTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class LyricsAvailabilityStatusTest {
    private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
    @Test fun loadingCannotBeMistakenForMissingLyricsAndFailureCanRetry(): Unit = runBlocking(Dispatchers.Swing) {
        val loading = mutableStateOf(true)
        var retries = 0
        val scene = ImageComposeScene(width = 390, height = 700, coroutineContext = Dispatchers.Swing)
        try {
            scene.setContent { AppTheme { LyricsAvailabilityStatus(loading.value) { retries++ } } }
            repeat(5) { delay(50); scene.render() }
            fun all() = scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }
            assertTrue(all().any { it.config.getOrNull(SemanticsProperties.TestTag) == "lyrics-loading" })
            assertFalse(all().any { it.config.getOrNull(SemanticsActions.OnClick) != null })
            loading.value = false
            repeat(5) { delay(50); scene.render() }
            assertTrue(all().any { it.config.getOrNull(SemanticsProperties.TestTag) == "lyrics-unavailable" })
            all().first { it.config.getOrNull(SemanticsActions.OnClick) != null }
                .config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke()
            assertEquals(1, retries)
        } finally { scene.close() }
    }
}
