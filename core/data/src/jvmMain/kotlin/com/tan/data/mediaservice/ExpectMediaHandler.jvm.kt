package com.tan.data.mediaservice

import com.tan.domain.repository.AnalyticsRepository

actual fun createMediaServiceHandler(
    dataStoreManager: com.tan.domain.manager.DataStoreManager,
    songRepository: com.tan.domain.repository.SongRepository,
    streamRepository: com.tan.domain.repository.StreamRepository,
    localPlaylistRepository: com.tan.domain.repository.LocalPlaylistRepository,
    analyticsRepository: AnalyticsRepository,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
): com.tan.domain.mediaservice.handler.MediaPlayerHandler =
    JvmMediaPlayerHandlerImpl(
        dataStoreManager = dataStoreManager,
        songRepository = songRepository,
        streamRepository = streamRepository,
        localPlaylistRepository = localPlaylistRepository,
        analyticsRepository = analyticsRepository,
        coroutineScope = coroutineScope,
    )