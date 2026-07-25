package com.my.kizzy.rpc

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object ArtworkCache {
    private val cache = mutableMapOf<String, String>()
    private val mutex = Mutex()

    suspend fun getOrFetch(key: String, fetch: suspend () -> String?): String? {
        mutex.withLock {
            cache[key]?.let { return it }
        }
        val fetched = fetch()
        if (fetched != null) {
            mutex.withLock {
                cache[key] = fetched
            }
        }
        return fetched
    }
}

