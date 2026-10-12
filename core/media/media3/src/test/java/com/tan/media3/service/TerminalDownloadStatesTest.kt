package com.tan.media3.service

import com.tan.media3.service.download.TerminalDownloadStates
import org.junit.Assert.*
import org.junit.Test

class TerminalDownloadStatesTest {
    @Test fun repeatedTerminalCallbacksAreDeduplicatedButRetryAndRemovalAllowANewNotification() {
        val states = TerminalDownloadStates()
        assertTrue(states.changed("track", 4))
        assertFalse(states.changed("track", 4))
        assertTrue(states.changed("track", 3))
        states.removed("track")
        assertTrue(states.changed("track", 3))
    }

    @Test fun largeDownloadHistoryEvictsOldEntriesWhileKeepingRecentEntriesDeduplicated() {
        val states = TerminalDownloadStates(3)
        repeat(10_000) { assertTrue(states.changed("track-$it", 3)) }
        assertFalse(states.changed("track-9999", 3))
        assertFalse(states.changed("track-9998", 3))
        assertFalse(states.changed("track-9997", 3))
        assertTrue(states.changed("track-0", 3))
    }
}
