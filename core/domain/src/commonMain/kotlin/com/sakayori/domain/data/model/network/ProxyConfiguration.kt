package com.sakayori.domain.data.model.network

import com.sakayori.domain.manager.DataStoreManager

data class ProxyConfiguration(
    val host: String,
    val port: Int,
    val type: DataStoreManager.ProxyType
)
