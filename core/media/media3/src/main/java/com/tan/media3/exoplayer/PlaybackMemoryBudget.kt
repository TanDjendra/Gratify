package com.tan.media3.exoplayer

internal data class PlaybackMemoryBudget(val preloadCount: Int, val bufferBytesPerPlayer: Int) {
    companion object {
        fun forDevice(lowRam: Boolean, memoryClassMb: Int): PlaybackMemoryBudget =
            if (lowRam || memoryClassMb <= 192) PlaybackMemoryBudget(1, 4 * 1024 * 1024)
            else PlaybackMemoryBudget(2, 8 * 1024 * 1024)
    }
}
