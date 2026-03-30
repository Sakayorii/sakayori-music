package com.sakayori.data.di.loader

import com.sakayori.data.di.databaseModule
import com.sakayori.data.di.mediaHandlerModule
import com.sakayori.data.di.repositoryModule
import org.koin.core.context.loadKoinModules

fun loadAllModules() {
    loadKoinModules(
        listOf(
            databaseModule,
            repositoryModule,
        ),
    )
    loadKoinModules(mediaHandlerModule)
    loadMediaService()
}

expect fun loadMediaService()
