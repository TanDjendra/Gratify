package com.tan.media3.service

import org.junit.Assert.*
import org.junit.Test

class MediaServiceConnectionTest {
    @Test
    fun allowedStartBindsOnceAndReturnsBindingResult() {
        val calls = mutableListOf<String>()
        assertTrue(startAndBindMediaService(
            start = { calls += "start" },
            bind = { calls += "bind"; true },
            onStartRestricted = { calls += "restricted" },
        ))
        assertEquals(listOf("start", "bind"), calls)
    }

    @Test
    fun backgroundRejectionStillBindsWithoutStartingAgain() {
        val calls = mutableListOf<String>()
        assertTrue(startAndBindMediaService(
            start = { calls += "start"; throw IllegalStateException("Background start denied") },
            bind = { calls += "bind"; true },
            onStartRestricted = { calls += "restricted" },
        ))
        assertEquals(listOf("start", "restricted", "bind"), calls)
    }

    @Test
    fun failedBindingIsReportedToActivityForRetry() {
        assertFalse(startAndBindMediaService({}, { false }, { fail("Unexpected restriction") }))
    }

    @Test
    fun missingPermissionIsNotMistakenForBackgroundRestriction() {
        val failure = SecurityException("Missing permission")
        val actual = assertThrows(SecurityException::class.java) {
            startAndBindMediaService(
                { throw failure }, { fail("Must not bind"); false }, { fail("Must not swallow") },
            )
        }
        assertSame(failure, actual)
    }
}
