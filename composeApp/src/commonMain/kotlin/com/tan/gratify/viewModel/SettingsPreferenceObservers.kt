package com.tan.gratify.viewModel

import kotlinx.coroutines.*

/** Keeps one live subscription for each preference, including after settings are reopened. */
internal class SettingsPreferenceObservers(private val scope: CoroutineScope, private val onFailure: () -> Unit) {
    private val jobs = mutableMapOf<String, Job>()
    fun observe(key: String, block: suspend CoroutineScope.() -> Unit) {
        if (jobs[key]?.isActive == true) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try { block() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { onFailure() }
        }
        jobs[key] = job
        job.start()
    }
}
