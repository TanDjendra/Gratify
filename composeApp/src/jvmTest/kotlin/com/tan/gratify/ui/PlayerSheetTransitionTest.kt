package com.tan.gratify.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.tan.gratify.ui.component.PlayerModalSheet
import com.tan.gratify.ui.component.PlayerSheetDismissal
import com.tan.gratify.ui.theme.AppTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class PlayerSheetTransitionTest {
    @Test fun repeatedCloseAndBackFinishOnlyOnceAfterHide(): Unit = runBlocking {
        val animation = CompletableDeferred<Unit>()
        var hidden = false
        var hides = 0
        var dismissals = 0
        val dismissal = PlayerSheetDismissal({ hides++; animation.await(); hidden = true }, { hidden }, { dismissals++ })
        val closing = launch { dismissal.dismiss() }
        yield()
        dismissal.dismiss()
        dismissal.complete() // Back/swipe completion cannot remove a still-visible panel.
        assertEquals(1, hides)
        assertEquals(0, dismissals)
        animation.complete(Unit)
        closing.join()
        dismissal.complete()
        dismissal.dismiss()
        assertEquals(1, hides)
        assertEquals(1, dismissals)
    }

    @Test fun interruptedCloseCanBeRetriedWithoutRemovingPlayer(): Unit = runBlocking {
        val animation = CompletableDeferred<Unit>()
        var hidden = false
        var hides = 0
        var dismissals = 0
        val dismissal = PlayerSheetDismissal({ hides++; animation.await(); hidden = true }, { hidden }, { dismissals++ })
        val closing = launch { dismissal.dismiss() }
        yield()
        closing.cancelAndJoin()
        assertEquals(0, dismissals)
        animation.complete(Unit)
        dismissal.dismiss()
        assertEquals(2, hides)
        assertEquals(1, dismissals)
    }

    @Test fun gestureCompletionDoesNotRunAnotherHide(): Unit = runBlocking {
        var hides = 0
        var dismissals = 0
        val dismissal = PlayerSheetDismissal({ hides++ }, { true }, { dismissals++ })
        dismissal.complete()
        dismissal.dismiss()
        dismissal.complete()
        assertEquals(0, hides)
        assertEquals(1, dismissals)
    }

    @Test fun vetoedHideKeepsPanelMountedAndAllowsRetry(): Unit = runBlocking {
        var hidden = false
        var dismissals = 0
        val dismissal = PlayerSheetDismissal({}, { hidden }, { dismissals++ })
        dismissal.dismiss()
        assertEquals(0, dismissals)
        hidden = true
        dismissal.dismiss()
        assertEquals(1, dismissals)
    }

    private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
    private suspend fun verifyAnimatedClose(width: Int, height: Int) = runBlocking(Dispatchers.Swing) {
        val mounted = mutableStateOf(true)
        var disposed = 0
        var dismissals = 0
        val scene = ImageComposeScene(width, height, Density(1f), coroutineContext = coroutineContext) {
            AppTheme {
                Text("Home")
                if (mounted.value) PlayerModalSheet(onDismiss = { dismissals++; mounted.value = false }) { dismiss ->
                    DisposableEffect(Unit) { onDispose { disposed++ } }
                    Column(Modifier.fillMaxSize()) {
                        Text("Player")
                        Button(onClick = dismiss) { Text("Close player") }
                    }
                }
            }
        }
        try {
            var time = 0L
            suspend fun frame() { scene.render(time).close(); time += 16_000_000L; delay(16) }
            repeat(40) { frame() }
            val close = scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }.first { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Close player" } == true &&
                    node.config.getOrNull(SemanticsActions.OnClick) != null
            }.config[SemanticsActions.OnClick].action!!
            close.invoke()
            close.invoke()
            frame()
            assertTrue(mounted.value, "Content must remain mounted while sliding out")
            assertEquals(0, dismissals)
            assertEquals(0, disposed)
            repeat(60) { frame() }
            assertFalse(mounted.value)
            assertEquals(1, dismissals)
            assertEquals(1, disposed)
        } finally {
            scene.close()
        }
    }

    @Test fun portraitSheetRetainsContentThroughClose(): Unit = runBlocking { verifyAnimatedClose(390, 844) }
    @Test fun landscapeSheetRetainsContentThroughClose(): Unit = runBlocking { verifyAnimatedClose(900, 420) }
}
