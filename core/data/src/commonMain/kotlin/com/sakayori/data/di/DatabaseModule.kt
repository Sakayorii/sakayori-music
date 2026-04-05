package com.sakayori.data.di

import DatabaseDao
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.sakayori.data.dataStore.DataStoreManagerImpl
import com.sakayori.data.dataStore.createDataStoreInstance
import com.sakayori.data.db.Converters
import com.sakayori.data.db.MusicDatabase
import com.sakayori.data.db.datasource.AnalyticsDatasource
import com.sakayori.data.db.datasource.LocalDataSource
import com.sakayori.data.db.getDatabaseBuilder
import com.sakayori.domain.manager.DataStoreManager
import com.sakayori.kotlinytmusicscraper.YouTube
import com.sakayori.spotify.Spotify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.dsl.module
import org.SakayoriMusic.aiservice.AiClient
import org.SakayoriMusic.lyrics.SakayoriMusicLyricsClient
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
val databaseModule =
    module {
        single(createdAtStart = true) {
            Converters()
        }
        single(createdAtStart = true) {
            getDatabaseBuilder(
                get<Converters>()
            )
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        }
        single(createdAtStart = true) {
            get<MusicDatabase>().getDatabaseDao()
        }
        single(createdAtStart = true) {
            LocalDataSource(get<DatabaseDao>())
        }
        single(createdAtStart = true) {
            AnalyticsDatasource(get<DatabaseDao>())
        }
        single(createdAtStart = true) {
            createDataStoreInstance()
        }
        single<DataStoreManager>(createdAtStart = true) {
            DataStoreManagerImpl(get<DataStore<Preferences>>())
        }

        single(createdAtStart = true) {
            YouTube()
        }

        single(createdAtStart = true) {
            Spotify()
        }

        single(createdAtStart = true) {
            AiClient()
        }

        single(createdAtStart = true) {
            SakayoriMusicLyricsClient()
        }
    }
