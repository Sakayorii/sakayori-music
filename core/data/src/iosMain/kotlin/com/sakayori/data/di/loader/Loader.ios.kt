package com.sakayori.data.di.loader

import com.sakayori.common.Config.SERVICE_SCOPE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import org.koin.core.context.loadKoinModules
import org.koin.core.qualifier.named
import org.koin.dsl.module

private val iosMediaModule = module {
    single<CoroutineScope>(qualifier = named(SERVICE_SCOPE)) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

actual fun loadMediaService() {
    loadKoinModules(iosMediaModule)
}
