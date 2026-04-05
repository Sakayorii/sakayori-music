package com.my.kizzy.repository

import com.my.kizzy.remote.ApiService
import com.my.kizzy.utils.toImageAsset

class KizzyRepository {
    private val api = ApiService()

    suspend fun getImage(url: String): String? {
        return api.getImage(url).toImageAsset()
    }

    suspend fun uploadImage(fileName: String, bytes: ByteArray): String? {
        return api.uploadImage(fileName, bytes).toImageAsset()
    }
}

