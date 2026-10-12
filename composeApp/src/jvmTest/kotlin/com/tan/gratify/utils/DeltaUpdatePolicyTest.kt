package com.tan.gratify.utils

import com.tan.domain.data.model.update.*
import kotlin.test.*

class DeltaUpdatePolicyTest {
    private val delta = UpdateDelta(61, "a".repeat(64), 1000, "https://github.com/patch", "b".repeat(64), 300)
    private val update = UpdateData("v2.1.1", null, "", "url", versionCode = 62, sizeBytes = 1000, deltas = listOf(delta))
    @Test fun exactBaseSelectsSmallDownload() { assertEquals(delta, selectDelta(update, 61, 1000, "a".repeat(64), false)) }
    @Test fun mismatchSplitAndUnsupportedFormatUseFullApk() {
        assertNull(selectDelta(update, 61, 1000, "a".repeat(64), true))
        assertNull(selectDelta(update, 60, 1000, "a".repeat(64), false))
        assertNull(selectDelta(update, 61, 999, "a".repeat(64), false))
        assertNull(selectDelta(update, 61, 1000, "c".repeat(64), false))
        assertNull(selectDelta(update.copy(deltas = listOf(delta.copy(format = "unknown"))), 61, 1000, "a".repeat(64), false))
    }
    @Test fun savingsMustReachThirtyPercentAndVersionMustIncrease() {
        assertNull(selectDelta(update.copy(deltas = listOf(delta.copy(sizeBytes = 701))), 61, 1000, "a".repeat(64), false))
        assertNull(selectDelta(update.copy(versionCode = 61), 61, 1000, "a".repeat(64), false))
        assertNull(selectDelta(update.copy(versionCode = null), 61, 1000, "a".repeat(64), false))
    }
}
