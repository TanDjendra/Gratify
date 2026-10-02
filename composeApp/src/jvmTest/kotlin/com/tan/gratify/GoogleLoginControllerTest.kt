package com.tan.gratify

import com.tan.gratify.viewModel.auth.GoogleLoginController
import com.tan.gratify.viewModel.auth.GoogleLoginState
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

class GoogleLoginControllerTest {
    @Test fun repeatedClicksOpenOnlyOneBrowserAndCancellationAllowsRetry(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        var opened = 0
        var closed = 0
        val controller = GoogleLoginController(this, {
            opened++
            entered.complete(Unit)
            try { gate.await() } finally { closed++ }
        }, {}, { false }, true)
        assertTrue(controller.start())
        entered.await()
        assertFalse(controller.start())
        assertEquals(1, opened)
        assertEquals(GoogleLoginState.Waiting, controller.state.value)
        controller.cancel()
        yield()
        assertEquals(1, closed)
        assertEquals(GoogleLoginState.Idle, controller.state.value)
        assertTrue(controller.start())
        controller.cancel()
        yield()
    }

    @Test fun desktopCallbackClosingWithoutSessionShowsRetryInsteadOfWaiting(): Unit = runBlocking {
        var waits = 0
        val controller = GoogleLoginController(this, {}, { waits++ }, { false }, true)
        controller.start()
        assertIs<GoogleLoginState.Failed>(controller.state.value)
        assertEquals(0, waits)
        assertTrue(controller.start())
    }

    @Test fun mobileBrowserReturningStillWaitsForAuthenticatedSession(): Unit = runBlocking {
        val session = CompletableDeferred<Unit>()
        val controller = GoogleLoginController(this, {}, { session.await() }, { false }, false)
        controller.start()
        assertEquals(GoogleLoginState.Waiting, controller.state.value)
        session.complete(Unit)
        yield()
        assertEquals(GoogleLoginState.Idle, controller.state.value)
    }

    @Test fun timeoutStopsBrowserWaitAndReleasesAttempt(): Unit = runBlocking {
        var closed = false
        val controller = GoogleLoginController(this, {
            try { awaitCancellation() } finally { closed = true }
        }, {}, { false }, true, 30.milliseconds)
        controller.start()
        delay(80)
        assertTrue(closed)
        assertIs<GoogleLoginState.Failed>(controller.state.value)
        assertTrue(controller.start())
        controller.cancel()
        yield()
    }

    @Test fun providerFailureDoesNotExposeSecrets(): Unit = runBlocking {
        val controller = GoogleLoginController(this, { error("token=synthetic-secret") }, {}, { false }, true)
        controller.start()
        val failure = assertIs<GoogleLoginState.Failed>(controller.state.value)
        assertFalse(failure.message.contains("synthetic-secret"))
        assertTrue(controller.start())
    }

    @Test fun successfulDesktopCallbackCompletesAfterSession(): Unit = runBlocking {
        var awaited = false
        val controller = GoogleLoginController(this, {}, { awaited = true }, { true }, true)
        controller.start()
        assertTrue(awaited)
        assertEquals(GoogleLoginState.Idle, controller.state.value)
    }
}
