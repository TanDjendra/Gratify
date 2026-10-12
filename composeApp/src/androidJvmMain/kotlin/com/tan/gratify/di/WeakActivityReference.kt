package com.tan.gratify.di

import java.lang.ref.WeakReference

/**
 * A process-lived lookup must not own its Activity. Keep the reference until destroy,
 * not pause/stop: permission prompts and external sharing temporarily stop the host.
 */
internal class WeakActivityReference<T : Any>(private val isUsable: (T) -> Boolean) {
    private var activity = WeakReference<T>(null)

    @Synchronized
    fun attach(owner: T) {
        activity = WeakReference(owner)
    }

    @Synchronized
    fun detach(owner: T) {
        // A replacement may attach before the previous Activity finishes destroying.
        if (activity.get() === owner) activity.clear()
    }

    @Synchronized
    fun currentOrNull(): T? = activity.get()?.takeIf(isUsable)
}
