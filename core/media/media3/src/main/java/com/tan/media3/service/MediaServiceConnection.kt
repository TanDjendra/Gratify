package com.tan.media3.service

/** A rejected background start must not be retried as a foreground start. */
internal fun startAndBindMediaService(
    start: () -> Unit,
    bind: () -> Boolean,
    onStartRestricted: () -> Unit,
): Boolean {
    try {
        start()
    } catch (_: IllegalStateException) {
        onStartRestricted()
    }
    // Binding from the visible Activity is still allowed. Media3 promotes the
    // service only when playback needs it, without a premature foreground deadline.
    return bind()
}
