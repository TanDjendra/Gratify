// Ported from SimpMusic 62e0073ba818d899b22c75909deb9b034516d5db (GPL-3.0).
package com.tan.gratify.ui.screen.player.simp.platform

import androidx.compose.runtime.Composable

/**
 * Handle onto the OS-level media volume (Android's `STREAM_MUSIC`). Distinct from the app's own
 * playback volume ([com.tan.domain.mediaservice.handler.ControlState.volume]) — this is the
 * device slider, used by the Apple Music Now Playing style's volume row.
 */
interface DeviceVolumeController {
    /** Current device volume as a 0f..1f fraction. Read during composition to observe changes. */
    val volumeFraction: Float

    fun setVolumeFraction(fraction: Float)
}

/**
 * Returns a [DeviceVolumeController] for platforms with a controllable device volume, or null
 * where there is none to control (Desktop routes audio through the OS mixer directly).
 */
@Composable
expect fun rememberDeviceVolumeController(): DeviceVolumeController?
