package com.tan.domain.utils

import com.tan.domain.extension.decodeHtmlEntities
import kotlin.test.*
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

class LaunchRegressionTest {
    @OptIn(ExperimentalTime::class)
    @Test fun listeningTimeCountsOnlyPlaybackIntervalsAndResetsPerTrack() {
        val clock = TestTimeSource(); val tracker = ListeningTimeTracker(clock)
        tracker.setPlaying(true); clock += 5.seconds
        tracker.setPlaying(true); tracker.setPlaying(false); clock += 30.seconds
        tracker.setPlaying(true); clock += 2.seconds
        assertEquals(7000L, tracker.takeMillis())
        clock += 1.seconds; assertEquals(1000L, tracker.takeMillis())
        tracker.setPlaying(false); clock += 60.seconds; assertEquals(0L,tracker.takeMillis())
    }
    @Test fun fileNamesCannotCreateSubdirectoriesOrWindowsDevices() {
        assertEquals("AC_DC", safeExportFileName("AC/DC"))
        assertEquals("a_b_c", safeExportFileName("a\\b:c"))
        assertEquals("_CON.txt", safeExportFileName("CON.txt"))
        assertEquals("Gratify_export", safeExportFileName("..."))
        assertTrue(safeExportFileName("a".repeat(300)).length <= 160)
    }
    @Test fun unicodeEntitiesSupportEmojiAndLeaveInvalidScalarsUnchanged() {
        assertEquals("🎵 & A", decodeHtmlEntities("&#x1F3B5; &amp; &#65;"))
        assertEquals("&#x110000; &#xD800;", decodeHtmlEntities("&#x110000; &#xD800;"))
    }
}
