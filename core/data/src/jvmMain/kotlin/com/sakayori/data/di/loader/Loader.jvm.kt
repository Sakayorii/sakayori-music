package com.sakayori.data.di.loader

import com.SakayoriMusic.media_jvm.di.loadVlcModule

actual fun loadMediaService() {
    loadVlcModule()
}

