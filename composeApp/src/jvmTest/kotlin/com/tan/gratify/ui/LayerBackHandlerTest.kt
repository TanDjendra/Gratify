package com.tan.gratify.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.tan.gratify.ui.component.LayerBackHandler
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class LayerBackHandlerTest {
    private class Input : NavigationEventInput() {
        fun back() = dispatchOnBackCompleted()
        fun cancelGesture() { dispatchOnBackStarted(NavigationEvent()); dispatchOnBackCancelled() }
    }

    @Test fun activeLayerClosesBeforeUnderlyingPageAndCancelledGestureDoesNothing(): Unit = runBlocking(Dispatchers.Swing) {
        val dispatcher = NavigationEventDispatcher()
        val owner = object : NavigationEventDispatcherOwner { override val navigationEventDispatcher = dispatcher }
        val input = Input().also(dispatcher::addInput)
        val visible = mutableStateOf(true)
        var pagesBack = 0
        var layersClosed = 0
        val scene = ImageComposeScene(320, 240, Density(1f), coroutineContext = coroutineContext) {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                LayerBackHandler(true) { pagesBack++ }
                LayerBackHandler(visible.value) { layersClosed++; visible.value = false }
            }
        }
        var frame = 0L
        suspend fun settle() { repeat(5) { scene.render(frame++ * 16_000_000L).close(); delay(16) } }
        try {
            settle()
            input.cancelGesture()
            assertEquals(0, layersClosed); assertEquals(0, pagesBack)
            input.back(); settle()
            assertEquals(1, layersClosed); assertEquals(0, pagesBack)
            input.back(); settle()
            assertEquals(1, layersClosed); assertEquals(1, pagesBack)
        } finally { scene.close(); dispatcher.removeInput(input); dispatcher.dispose() }
    }
}
