package com.sakayori.data.mediaservice

actual fun createMediaServiceHandler(
    dataStoreManager: com.sakayori.domain.manager.DataStoreManager,
    songRepository: com.sakayori.domain.repository.SongRepository,
    streamRepository: com.sakayori.domain.repository.StreamRepository,
    localPlaylistRepository: com.sakayori.domain.repository.LocalPlaylistRepository,
    analyticsRepository: com.sakayori.domain.repository.AnalyticsRepository,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
): com.sakayori.domain.mediaservice.handler.MediaPlayerHandler {
    throw UnsupportedOperationException("iOS media handler is not yet available")
}
