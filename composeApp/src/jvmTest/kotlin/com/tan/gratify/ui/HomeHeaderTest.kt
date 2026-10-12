package com.tan.gratify.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import com.tan.gratify.ui.screen.home.HomeHeaderContent
import com.tan.gratify.ui.theme.AppTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class HomeHeaderTest {
    private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)

    @Test fun settingsIsAtTheRightEdgeAndWorksWithLargeText(): Unit = runBlocking(Dispatchers.Swing) {
        for ((width, scale) in listOf(320 to 1.5f, 390 to 1f, 1100 to 1.3f)) {
            var opened = false
            val scene = ImageComposeScene(width, 180, Density(1f, scale), coroutineContext = coroutineContext) {
                AppTheme { HomeHeaderContent("Nara", null, {}, { opened = true }) }
            }
            try {
                repeat(15) { scene.render(it * 16_000_000L).close(); delay(16) }
                val settings = scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }
                    .first { it.config.contains(SemanticsActions.OnClick) &&
                        it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { label ->
                            label == "Settings" || label == "Pengaturan" } == true }
                assertTrue(settings.boundsInRoot.width >= 48f)
                assertTrue(settings.boundsInRoot.height >= 48f)
                assertTrue(settings.boundsInRoot.left >= width - 100f)
                assertTrue(settings.boundsInRoot.right <= width)
                settings.config[SemanticsActions.OnClick].action!!.invoke()
                assertTrue(opened)
            } finally { scene.close() }
        }
    }
}
