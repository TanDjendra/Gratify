package com.tan.gratify.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.tan.gratify.ui.component.BottomSafeContent
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class BottomSafeContentTest {
    private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)

    @Test fun contentFollowsChangingBarHeightWithoutApplyingBottomPaddingTwice(): Unit = runBlocking(Dispatchers.Swing) {
        for (height in listOf(400, 900)) {
            val bottom = mutableStateOf(156.dp)
            val scene = ImageComposeScene(390, height, Density(1f), coroutineContext = coroutineContext) {
                BottomSafeContent(PaddingValues(start = 12.dp, top = 24.dp, end = 16.dp, bottom = bottom.value)) { padding ->
                    assertEquals(0.dp, padding.calculateBottomPadding())
                    assertEquals(24.dp, padding.calculateTopPadding())
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.align(Alignment.BottomCenter).size(48.dp).semantics { contentDescription = "Last action" })
                    }
                }
            }
            try {
                for ((index, reserved) in listOf(156, 80, 0).withIndex()) {
                    bottom.value = reserved.dp
                    repeat(10) { scene.render(index * 1_000_000_000L + it * 16_000_000L).close(); delay(16) }
                    val last = scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }
                        .first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Last action") == true }
                    assertEquals((height - reserved).toFloat(), last.boundsInRoot.bottom)
                    assertTrue(last.boundsInRoot.top >= 0f)
                }
            } finally { scene.close() }
        }
    }
}
