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
        single {
            Converters()
        }
        single {
            getDatabaseBuilder(
                get<Converters>()
            )
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        }
        single {
            get<MusicDatabase>().getDatabaseDao()
        }
        single {
            LocalDataSource(get<DatabaseDao>())
        }
        single {
            AnalyticsDatasource(get<DatabaseDao>())
        }
        single {
            createDataStoreInstance()
        }
        single<DataStoreManager> {
            DataStoreManagerImpl(get<DataStore<Preferences>>())
        }

        single {
            YouTube()
        }

        single {
            Spotify()
        }

        single {
            AiClient()
        }

        single {
            SakayoriMusicLyricsClient()
        }
    }
