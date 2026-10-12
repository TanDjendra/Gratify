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
import kotlinx.coroutines.launch

@UnstableApi
internal class MergingMediaSourceFactory(
    private val defaultMediaSourceFactory: DefaultMediaSourceFactory,
    private val dataStoreManager: DataStoreManager,
) : MediaSource.Factory {

    // Start audio-only until preferences arrive. Never wait for DataStore on
    // the main thread during DI initialization or on ExoPlayer's playback thread.
    @Volatile
    private var cachedWatchVideo: Boolean = false

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
