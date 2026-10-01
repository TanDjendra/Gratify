package com.tan.gratify.lyrics.parser

import kotlin.test.*

class LrcRegressionTest {
    @Test fun precisionMultipleStampsOffsetsAndNumericSorting() {
        val result = parseSyncedLyrics("[offset:-50]\n[01:00.123]Third\n[00:10.5][00:11.05]Repeated\n[00:01]\n[00:99.00]Invalid").lyrics!!.lines!!
        assertEquals(listOf("950", "10450", "11000", "60073"), result.map { it.startTimeMs })
        assertEquals(listOf("♫", "Repeated", "Repeated", "Third"), result.map { it.words })
    }
}
