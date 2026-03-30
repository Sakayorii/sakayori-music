package com.sakayori.data.mediaservice

import com.sakayori.domain.manager.DataStoreManager
import com.sakayori.domain.mediaservice.handler.MediaPlayerHandler
import com.sakayori.domain.repository.AnalyticsRepository
import com.sakayori.domain.repository.LocalPlaylistRepository
import com.sakayori.domain.repository.SongRepository
import com.sakayori.domain.repository.StreamRepository
import kotlinx.coroutines.CoroutineScope

expect fun createMediaServiceHandler(
    dataStoreManager: DataStoreManager,
    songRepository: SongRepository,
    streamRepository: StreamRepository,
    localPlaylistRepository: LocalPlaylistRepository,
    analyticsRepository: AnalyticsRepository,
    coroutineScope: CoroutineScope,
): MediaPlayerHandler
