package com.sakayori.data.di

import com.sakayori.common.Config.SERVICE_SCOPE
import com.sakayori.data.io.fileDir
import com.sakayori.data.repository.AccountRepositoryImpl
import com.sakayori.data.repository.AlbumRepositoryImpl
import com.sakayori.data.repository.AnalyticsRepositoryImpl
import com.sakayori.data.repository.ArtistRepositoryImpl
import com.sakayori.data.repository.CommonRepositoryImpl
import com.sakayori.data.repository.HomeRepositoryImpl
import com.sakayori.data.repository.LocalPlaylistRepositoryImpl
import com.sakayori.data.repository.LyricsCanvasRepositoryImpl
import com.sakayori.data.repository.PlaylistRepositoryImpl
import com.sakayori.data.repository.PodcastRepositoryImpl
import com.sakayori.data.repository.SearchRepositoryImpl
import com.sakayori.data.repository.SongRepositoryImpl
import com.sakayori.data.repository.StreamRepositoryImpl
import com.sakayori.data.repository.UpdateRepositoryImpl
import com.sakayori.domain.repository.AccountRepository
import com.sakayori.domain.repository.AlbumRepository
import com.sakayori.domain.repository.AnalyticsRepository
import com.sakayori.domain.repository.ArtistRepository
import com.sakayori.domain.repository.CommonRepository
import com.sakayori.domain.repository.HomeRepository
import com.sakayori.domain.repository.LocalPlaylistRepository
import com.sakayori.domain.repository.LyricsCanvasRepository
import com.sakayori.domain.repository.PlaylistRepository
import com.sakayori.domain.repository.PodcastRepository
import com.sakayori.domain.repository.SearchRepository
import com.sakayori.domain.repository.SongRepository
import com.sakayori.domain.repository.StreamRepository
import com.sakayori.domain.repository.UpdateRepository
import org.koin.core.qualifier.named
import org.koin.dsl.module

val repositoryModule =
    module {
        single<AccountRepository>(createdAtStart = true) {
            AccountRepositoryImpl(get(), get())
        }

        single<AlbumRepository>(createdAtStart = true) {
            AlbumRepositoryImpl(get(), get())
        }

        single<ArtistRepository>(createdAtStart = true) {
            ArtistRepositoryImpl(get(), get())
        }

        single<CommonRepository>(createdAtStart = true) {
            CommonRepositoryImpl(get(named(SERVICE_SCOPE)), get(), get(), get(), get(), get()).apply {
                this.init("${fileDir()}/ytdlp-cookie.txt", get())
            }
        }

        single<HomeRepository>(createdAtStart = true) {
            HomeRepositoryImpl(get(), get())
        }

        single<LocalPlaylistRepository>(createdAtStart = true) {
            LocalPlaylistRepositoryImpl(get(), get(), get())
        }

        single<LyricsCanvasRepository>(createdAtStart = true) {
            LyricsCanvasRepositoryImpl(get(), get(), get(), get(), get())
        }

        single<PlaylistRepository>(createdAtStart = true) {
            PlaylistRepositoryImpl(get(), get(), get())
        }

        single<PodcastRepository>(createdAtStart = true) {
            PodcastRepositoryImpl(get(), get())
        }

        single<SearchRepository>(createdAtStart = true) {
            SearchRepositoryImpl(get(), get())
        }

        single<SongRepository>(createdAtStart = true) {
            SongRepositoryImpl(get(), get(), get())
        }

        single<StreamRepository>(createdAtStart = true) {
            StreamRepositoryImpl(get(), get())
        }

        single<UpdateRepository>(createdAtStart = true) {
            UpdateRepositoryImpl(get())
        }

        single<AnalyticsRepository>(createdAtStart = true) {
            AnalyticsRepositoryImpl(get())
        }
    }
