package com.tan.gratify.viewModel

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.test.*

class SettingsPreferenceObserversTest {
    @Test fun repeatedReadsKeepOneSubscriptionAndReceiveNewValues() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val preference = MutableStateFlow("initial"); val seen = mutableListOf<String>()
        val observers = SettingsPreferenceObservers(scope) { error("unexpected failure") }
        try {
            repeat(100) { observers.observe("language") { preference.collect { seen.add(it) } } }
            assertEquals(1, preference.subscriptionCount.value)
            preference.value = "id-ID"
            assertEquals(listOf("initial", "id-ID"), seen)
            scope.cancel(); assertEquals(0, preference.subscriptionCount.value)
        } finally { scope.cancel() }
    }
    @Test fun failedObserverCanRetryAndOtherPreferencesKeepWorking() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var failures = 0; var attempts = 0; val preference = MutableStateFlow(false)
        val observers = SettingsPreferenceObservers(scope) { failures++ }
        try {
            observers.observe("bad") { attempts++; error("synthetic storage failure") }
            observers.observe("good") { preference.collect {} }
            observers.observe("bad") { attempts++; preference.collect {} }
            assertEquals(1, failures); assertEquals(2, attempts); assertEquals(2, preference.subscriptionCount.value)
        } finally { scope.cancel() }
    }
    @Test fun cancellationIsNotReportedAsReadFailure() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var failures = 0
        val observers = SettingsPreferenceObservers(scope) { failures++ }
        observers.observe("one") { awaitCancellation() }
        scope.cancel(); assertEquals(0, failures)
    }
}
