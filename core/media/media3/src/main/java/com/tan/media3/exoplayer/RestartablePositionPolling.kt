package com.tan.media3.exoplayer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** One position loop per adapter, restartable after a stop or a crossfade interruption. */
internal class RestartablePositionPolling(private val scope: CoroutineScope) {
    private var job: Job? = null

    fun start(shouldContinue: () -> Boolean, intervalMs: () -> Long, sample: () -> Unit) {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive && shouldContinue()) {
                sample()
                delay(intervalMs().coerceAtLeast(1))
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
