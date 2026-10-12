package com.tan.gratify.ui.component

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tan.gratify.viewModel.support.SupportPromptController
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Counts foreground entries, retaining the first token across activity recreation. */
@Composable
fun TrackApplicationOpenings(controller: SupportPromptController) {
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var session by rememberSaveable { mutableStateOf(Random.nextLong().toString()) }
    DisposableEffect(owner, controller) {
        var firstStart = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                if (!firstStart) session = Random.nextLong().toString()
                firstStart = false
                val opening = session
                scope.launch { controller.registerOpening(opening) }
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
