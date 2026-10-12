package com.tan.domain.utils

import kotlin.test.*

class RecordedListeningTest {
    @Test fun requiresThirtySecondsOfElapsedListening() {
        assertFalse(shouldRecordListening(-1)); assertFalse(shouldRecordListening(0)); assertFalse(shouldRecordListening(29_999))
        assertTrue(shouldRecordListening(30_000)); assertTrue(shouldRecordListening(180_000))
    }
}
