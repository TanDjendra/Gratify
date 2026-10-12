package com.tan.media3.service

import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSessionService

/** Preserve the queue for a user-initiated retry after Android rejects playback. */
@UnstableApi
internal class ForegroundPlaybackFailureListener(
    private val pausePlayback: () -> Unit,
) : MediaSessionService.Listener {
    override fun onForegroundServiceStartNotAllowedException() {
        pausePlayback()
    }
}
