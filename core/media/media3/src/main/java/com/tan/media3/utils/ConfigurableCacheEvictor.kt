package com.tan.media3.utils

import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.common.util.UnstableApi

/** Apply the saved cache budget after preferences load, without blocking startup. */
@UnstableApi
internal class ConfigurableCacheEvictor : CacheEvictor {
    // Do not evict saved audio before the user's actual budget has been read.
    private var delegate = LeastRecentlyUsedCacheEvictor(Long.MAX_VALUE)

    fun setMaxBytes(cache: Cache, maxBytes: Long) {
        require(maxBytes >= 0)
        // SimpleCache invokes its evictor under this same monitor. Budget changes
        // run on IO and cannot race with a cache write or cache initialization.
        synchronized(cache) {
            val existingSpans = cache.keys.flatMap { cache.getCachedSpans(it) }
            delegate = LeastRecentlyUsedCacheEvictor(maxBytes)
            existingSpans.forEach { delegate.onSpanAdded(cache, it) }
        }
    }

    override fun requiresCacheSpanTouches() = true
    override fun onCacheInitialized() = Unit
    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) =
        delegate.onStartFile(cache, key, position, length)
    override fun onSpanAdded(cache: Cache, span: CacheSpan) = delegate.onSpanAdded(cache, span)
    override fun onSpanRemoved(cache: Cache, span: CacheSpan) = delegate.onSpanRemoved(cache, span)
    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) =
        delegate.onSpanTouched(cache, oldSpan, newSpan)
}
