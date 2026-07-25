package com.tan.media3.service.mediasourcefactory

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.tan.common.MERGING_DATA_TYPE
import com.tan.domain.manager.DataStoreManager
import com.tan.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@UnstableApi
internal class MergingMediaSourceFactory(
    private val defaultMediaSourceFactory: DefaultMediaSourceFactory,
    private val dataStoreManager: DataStoreManager,
) : MediaSource.Factory {

    // Cached preference value — updated reactively via observePreference().
    // createMediaSource() is called from ExoPlayer's internal playback thread;
    // the previous runBlocking(Dispatchers.IO) blocked that thread and could
    // contribute to deadlocks when Dispatchers.IO is contended.
    @Volatile
    private var cachedWatchVideo: Boolean = false

    init {
        // Seed the cache synchronously so the first createMediaSource call has a value.
        // This runs once during DI init on Main, which is acceptable.
        cachedWatchVideo = runBlocking(Dispatchers.IO) {
            dataStoreManager.watchVideoInsteadOfPlayingAudio.first()
        } == DataStoreManager.Values.TRUE
    }

    /**
     * Start observing the preference reactively. Call once after construction
     * (from the service scope) to keep cachedWatchVideo up to date without blocking.
     */
    fun observePreference(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            dataStoreManager.watchVideoInsteadOfPlayingAudio.collectLatest { value ->
                cachedWatchVideo = (value == DataStoreManager.Values.TRUE)
            }
        }
    }

    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
        defaultMediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory {
        defaultMediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        return this
    }

    override fun getSupportedTypes(): IntArray = defaultMediaSourceFactory.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        Logger.w("Merging Media Source", mediaItem.mediaMetadata.description.toString())
        val getVideo = cachedWatchVideo
        Logger.w("Merging Media Source", getVideo.toString())
        if (mediaItem.mediaMetadata.description == MERGING_DATA_TYPE.VIDEO && getVideo) {
            val videoItem =
                mediaItem
                    .buildUpon()
                    .setMediaId("${MERGING_DATA_TYPE.VIDEO}${mediaItem.mediaId}")
                    .setCustomCacheKey("${MERGING_DATA_TYPE.VIDEO}${mediaItem.mediaId}")
                    .build()
            return MergingMediaSource(
                defaultMediaSourceFactory.createMediaSource(videoItem),
                defaultMediaSourceFactory.createMediaSource(mediaItem),
            )
        } else {
            return defaultMediaSourceFactory.createMediaSource(mediaItem)
        }
    }
}