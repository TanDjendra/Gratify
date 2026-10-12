package com.tan.gratify.expect

import androidx.compose.runtime.Composable

data class ClipPlaybackState(val positionMs: Long = 0, val buffering: Boolean = true,
    val completed: Boolean = false, val error: Boolean = false)

/** Plays a bounded excerpt without replacing the main player's queue or position. */
@Composable
expect fun StoryClipAudio(url: String, startMs: Long, durationMs: Long, playing: Boolean,
    replayKey: Int, onState: (ClipPlaybackState) -> Unit)
