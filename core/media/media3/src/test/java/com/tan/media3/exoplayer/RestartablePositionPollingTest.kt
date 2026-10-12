package com.tan.media3.exoplayer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class RestartablePositionPollingTest {
    @Test fun resumeAfterStopContinuesSamplingTheSameTrack(): Unit = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val polling = RestartablePositionPolling(scope)
        val positions = Channel<Long>(Channel.UNLIMITED)
        var position = 54_000L
        fun resume() = polling.start({ true }, { 5L }) { positions.trySend(position) }
        try {
            resume()
            assertEquals(54_000L, withTimeout(2_000) { positions.receive() })
            polling.stop()
            yield()
            while (positions.tryReceive().isSuccess) { }
            position = 200_000L
            delay(25)
            assertTrue(positions.tryReceive().isFailure)
            resume()
            assertEquals(200_000L, withTimeout(2_000) { positions.receive() })
        } finally { scope.cancel() }
    }

    @Test fun repeatedReadyCallbacksKeepOneLoopAndStoppingDoesNotCancelItsOwner(): Unit = runBlocking {
        val owner = SupervisorJob()
        val scope = CoroutineScope(coroutineContext + owner)
        val polling = RestartablePositionPolling(scope)
        val ticks = Channel<Int>(Channel.UNLIMITED)
        try {
            polling.start({ true }, { 5L }) { ticks.trySend(1) }
            repeat(100) { polling.start({ true }, { 5L }) { ticks.trySend(2) } }
            assertEquals(1, withTimeout(2_000) { ticks.receive() })
            delay(25)
            polling.stop()
            yield()
            while (true) {
                val value = ticks.tryReceive().getOrNull() ?: break
                assertEquals(1, value)
            }
            assertTrue(owner.isActive)
        } finally { scope.cancel() }
    }

    @Test fun finishedLoopCanRestartAfterAPlayerIsAttachedAgain(): Unit = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val polling = RestartablePositionPolling(scope)
        val ticks = Channel<Int>(Channel.UNLIMITED)
        var attached = false
        try {
            polling.start({ attached }, { 5L }) { ticks.trySend(1) }
            yield()
            attached = true
            polling.start({ attached }, { 5L }) { ticks.trySend(2) }
            assertEquals(2, withTimeout(2_000) { ticks.receive() })
        } finally { scope.cancel() }
    }
}
