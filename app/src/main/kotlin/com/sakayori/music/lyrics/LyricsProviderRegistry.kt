/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.lyrics

import androidx.datastore.preferences.core.Preferences
import com.sakayori.music.constants.EnableBetterLyricsKey
import com.sakayori.music.constants.EnablePaxsenixKey
import com.sakayori.music.constants.EnableLrcLibKey
import com.sakayori.music.constants.EnableKugouKey
import com.sakayori.music.constants.EnableLyricsPlus

object LyricsProviderRegistry {
    private val providerMap = mapOf(
        "BetterLyrics" to BetterLyricsProvider,
        "Paxsenix" to PaxsenixLyricsProvider,
        "LrcLib" to LrcLibLyricsProvider,
        "KuGou" to KuGouLyricsProvider,
        "LyricsPlus" to LyricsPlusProvider,
        "YouTubeSubtitle" to YouTubeSubtitleLyricsProvider,
        "YouTube" to YouTubeLyricsProvider,
    )

    val providerNames = providerMap.keys.toList()

    fun getProviderByName(name: String): LyricsProvider? = providerMap[name]

    fun getProviderName(provider: LyricsProvider): String? =
        providerMap.entries.find { it.value == provider }?.key

    /** Same switches/defaults as provider.isEnabled, from one suspending snapshot. */
    internal fun enabledFromSnapshot(provider: LyricsProvider, preferences: Preferences): Boolean? =
        when (getProviderName(provider)) {
            "BetterLyrics" -> preferences[EnableBetterLyricsKey] ?: true
            "Paxsenix" -> preferences[EnablePaxsenixKey] ?: true
            "LrcLib" -> preferences[EnableLrcLibKey] ?: true
            "KuGou" -> preferences[EnableKugouKey] ?: true
            "LyricsPlus" -> preferences[EnableLyricsPlus] ?: false
            "YouTubeSubtitle", "YouTube" -> true
            else -> null
        }

    fun deserializeProviderOrder(orderString: String): List<String> {
        if (orderString.isBlank()) {
            return getDefaultProviderOrder()
        }
        return orderString.split(",").map { it.trim() }.filter { it in providerNames }
    }

    fun serializeProviderOrder(providers: List<String>): String {
        return providers.filter { it in providerNames }.joinToString(",")
    }

    fun getDefaultProviderOrder(): List<String> = listOf(
        "BetterLyrics",
        "LrcLib",
        "KuGou",
        "Paxsenix",
        "LyricsPlus",
        "YouTubeSubtitle",
        "YouTube",
    )

    fun getOrderedProviders(orderString: String): List<LyricsProvider> {
        val order = deserializeProviderOrder(orderString)
        return order.mapNotNull { getProviderByName(it) }
    }
}
