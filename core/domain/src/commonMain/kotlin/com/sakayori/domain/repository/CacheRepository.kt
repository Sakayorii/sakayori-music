package com.sakayori.domain.repository

interface CacheRepository {
    suspend fun getCacheSize(cacheName: String): Long

    fun clearCache(cacheName: String)

    suspend fun getAllCacheKeys(cacheName: String): List<String>
}
