package com.sakayori.data.di.loader

import com.sakayori.music.media_jvm.di.loadVlcModule

actual fun loadMediaService() {
    loadVlcModule()
}
