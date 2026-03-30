package com.sakayori.data.mediaservice

import com.sakayori.domain.repository.AnalyticsRepository

actual fun createMediaServiceHandler(
    dataStoreManager: com.sakayori.domain.manager.DataStoreManager,
    songRepository: com.sakayori.domain.repository.SongRepository,
    streamRepository: com.sakayori.domain.repository.StreamRepository,
    localPlaylistRepository: com.sakayori.domain.repository.LocalPlaylistRepository,
    analyticsRepository: AnalyticsRepository,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
): com.sakayori.domain.mediaservice.handler.MediaPlayerHandler =
    MediaServiceHandlerImpl(
        dataStoreManager,
        songRepository,
        streamRepository,
        localPlaylistRepository,
        analyticsRepository,
        coroutineScope,
    )
