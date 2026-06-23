package com.tan.data.mediaservice

actual fun createMediaServiceHandler(
    dataStoreManager: com.tan.domain.manager.DataStoreManager,
    songRepository: com.tan.domain.repository.SongRepository,
    streamRepository: com.tan.domain.repository.StreamRepository,
    localPlaylistRepository: com.tan.domain.repository.LocalPlaylistRepository,
    analyticsRepository: com.tan.domain.repository.AnalyticsRepository,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
): com.tan.domain.mediaservice.handler.MediaPlayerHandler {
    TODO("Not yet implemented")
}