package com.tan.media3.exoplayer

import org.junit.Assert.*
import org.junit.Test

class PlaybackMemoryBudgetTest {
    @Test fun lowRamAndSmallApplicationHeapsKeepOnlyOneUpcomingPlayer() {
        for (memoryClass in listOf(64, 128, 192)) {
            val budget = PlaybackMemoryBudget.forDevice(false, memoryClass)
            assertEquals(1, budget.preloadCount)
            assertTrue((budget.preloadCount + 1) * budget.bufferBytesPerPlayer <= 8 * 1024 * 1024)
        }
        assertEquals(1, PlaybackMemoryBudget.forDevice(true, 512).preloadCount)
    }

    @Test fun largerDevicesKeepTwoPreloadsWithinTwentyFourMiBOfAudioBufferTargets() {
        for (memoryClass in listOf(256, 512, 1024)) {
            val budget = PlaybackMemoryBudget.forDevice(false, memoryClass)
            assertEquals(2, budget.preloadCount)
            assertEquals(24 * 1024 * 1024, (budget.preloadCount + 1) * budget.bufferBytesPerPlayer)
        }
    }
}

