package com.tan.domain.utils

import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Counts elapsed time while audio is playing. Seeking and playback speed do not add time. */
class ListeningTimeTracker(private val timeSource: TimeSource = TimeSource.Monotonic) {
    private var started: TimeMark? = null
    private var elapsedMillis = 0L
    fun setPlaying(playing: Boolean) {
        if ((started != null) == playing) return
        started?.let { elapsedMillis += it.elapsedNow().inWholeMilliseconds.coerceAtLeast(0) }
        started = if (playing) timeSource.markNow() else null
    }
    fun takeMillis(): Long {
        val playing = started != null
        val result = elapsedMillis + (started?.elapsedNow()?.inWholeMilliseconds ?: 0L).coerceAtLeast(0)
        elapsedMillis = 0
        started = if (playing) timeSource.markNow() else null
        return result
    }
}
