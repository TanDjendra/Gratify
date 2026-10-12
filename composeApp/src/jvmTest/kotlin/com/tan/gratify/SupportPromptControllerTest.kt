package com.tan.gratify

import com.tan.gratify.viewModel.support.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlin.test.*

class SupportPromptControllerTest {
    private class Store(var value: String? = null) {
        fun controller() = SupportPromptController({ value }, { value = it })
        fun history() = Json.decodeFromString<SupportPromptHistory>(value!!)
    }

    @Test fun firstOpeningAndEveryTwentySubsequentOpeningsSurviveRestarts() = runBlocking {
        val store = Store()
        for (opening in 1..61) {
            val controller = store.controller()
            controller.registerOpening("session-$opening")
            if (opening in listOf(1, 21, 41, 61)) {
                assertEquals(SupportPromptStatus.Pending, controller.status.value, "Opening $opening")
                controller.present()
                assertEquals(SupportPromptStatus.Visible, controller.status.value)
                controller.dismiss(false)
            } else assertEquals(SupportPromptStatus.Idle, controller.status.value, "Opening $opening")
            assertEquals(opening.toLong(), store.history().openings)
        }
    }

    @Test fun duplicateAndConcurrentSessionRegistrationCountOnce() = runBlocking {
        val store = Store(); val controller = store.controller()
        coroutineScope { repeat(30) { launch { controller.registerOpening("same-session") } } }
        assertEquals(1L, store.history().openings)
        controller.present(); controller.registerOpening("same-session")
        assertEquals(SupportPromptStatus.Visible, controller.status.value)
        controller.dismiss(false); controller.registerOpening("same-session")
        assertEquals(SupportPromptStatus.Idle, controller.status.value)
        assertEquals(1L, store.history().openings)
    }

    @Test fun deferredPromptOnlyStartsItsIntervalAfterItIsPresented() = runBlocking {
        val store = Store()
        repeat(8) { opening ->
            val controller = store.controller(); controller.registerOpening("$opening")
            assertEquals(SupportPromptStatus.Pending, controller.status.value)
            assertNull(store.history().lastShownOpening)
        }
        val controller = store.controller(); controller.registerOpening("home")
        controller.present(); controller.present()
        assertEquals(9L, store.history().lastShownOpening)
        controller.dismiss(false)
        for (opening in 10..28) {
            controller.registerOpening("$opening")
            assertEquals(SupportPromptStatus.Idle, controller.status.value)
        }
        controller.registerOpening("29")
        assertEquals(SupportPromptStatus.Pending, controller.status.value)
    }

    @Test fun returningToAnOpenSheetKeepsItsPresentationAndCountsTheOpening() = runBlocking {
        val store = Store(); val controller = store.controller()
        controller.registerOpening("first"); controller.present()
        repeat(25) { controller.registerOpening("foreground-$it") }
        assertEquals(SupportPromptStatus.Visible, controller.status.value)
        assertEquals(26L, store.history().openings)
        assertEquals(1L, store.history().lastShownOpening)
        controller.dismiss(true); controller.registerOpening("next")
        assertEquals(SupportPromptStatus.Idle, controller.status.value)
        assertTrue(store.history().optedOut)
    }

    @Test fun optOutSurvivesNewControllersAndFurtherOpenings() = runBlocking {
        val store = Store(); val first = store.controller()
        first.registerOpening("first"); first.present(); first.dismiss(true)
        assertTrue(store.history().optedOut)
        repeat(100) {
            val controller = store.controller(); controller.registerOpening("new-$it"); controller.present()
            assertEquals(SupportPromptStatus.Idle, controller.status.value)
        }
    }

    @Test fun brokenAndOutOfRangeStoredHistoryRecovers() = runBlocking {
        for (value in listOf("not-json", "", "{\"openings\":-8,\"lastShownOpening\":-2}",
            "{\"openings\":4,\"lastShownOpening\":99}")) {
            val store = Store(value); val controller = store.controller()
            controller.registerOpening("first")
            assertTrue(store.history().openings > 0)
            assertTrue(store.history().lastShownOpening?.let { it in 0..store.history().openings } ?: true)
        }
    }

    @Test fun readOrWriteFailureCannotBlockStartup() = runBlocking {
        val readFailure = SupportPromptController({ error("Unavailable") }, {})
        readFailure.registerOpening("first"); readFailure.present(); readFailure.dismiss(false)
        assertEquals(SupportPromptStatus.Idle, readFailure.status.value)
        val writeFailure = SupportPromptController({ null }, { error("Unavailable") })
        writeFailure.registerOpening("first")
        assertEquals(SupportPromptStatus.Idle, writeFailure.status.value)
    }

    @Test fun cancelledStoragePropagatesCancellation(): Unit = runBlocking {
        val controller = SupportPromptController({ throw CancellationException("cancelled") }, {})
        assertFailsWith<CancellationException> { controller.registerOpening("first") }
    }

    @Test fun failedOptOutWriteStillSuppressesPromptForThisSession() = runBlocking {
        var value: String? = null; var unavailable = false
        val controller = SupportPromptController({ value }, { if (unavailable) error("Unavailable") else value = it })
        controller.registerOpening("first"); controller.present()
        unavailable = true; controller.dismiss(true)
        unavailable = false
        repeat(25) { controller.registerOpening("next-$it") }
        assertEquals(SupportPromptStatus.Idle, controller.status.value)
        assertTrue(Json.decodeFromString<SupportPromptHistory>(value!!).optedOut)
    }
}
