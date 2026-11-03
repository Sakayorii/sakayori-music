package com.sakayori.innertube.utils

import com.sakayori.innertube.YouTube
import com.sakayori.innertube.pages.LibraryPage
import com.sakayori.innertube.pages.PlaylistPage
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.security.MessageDigest

@JvmName("completedLibrary")
suspend fun Result<PlaylistPage>.completed(): Result<PlaylistPage> = runOnIoCatching {
    val page = getOrThrow()
    if (!page.isSnapshotUsableForSync) throw IOException("Incomplete playlist snapshot")
    val songs = page.songs.toMutableList()
    var continuation = page.songsContinuation ?: page.continuation
    val seenContinuations = mutableSetOf<String>()
    var requestCount = 0
    val maxRequests = 50
    var consecutiveEmptyResponses = 0
    
    while (continuation != null) {
        currentCoroutineContext().ensureActive()
        if (requestCount >= maxRequests || !seenContinuations.add(continuation)) {
            throw IOException("Playlist continuation did not complete")
        }
        requestCount++
        
        val continuationPage = YouTube.playlistContinuation(continuation).getOrThrow()
        if (!continuationPage.isSnapshotUsableForSync) throw IOException("Incomplete playlist continuation")
        
        if (continuationPage.songs.isEmpty()) {
            consecutiveEmptyResponses++
            if (consecutiveEmptyResponses >= 2 && continuationPage.continuation != null) {
                throw IOException("Playlist continuation made no progress")
            }
        } else {
            consecutiveEmptyResponses = 0
            songs += continuationPage.songs
        }
        
        continuation = continuationPage.continuation
    }
    PlaylistPage(
        playlist = page.playlist,
        songs = songs,
        songsContinuation = null,
        continuation = page.continuation
    )
}

@JvmName("completedPlaylist")
suspend fun Result<LibraryPage>.completed(): Result<LibraryPage> = runOnIoCatching {
    val page = getOrThrow()
    if (!page.isSnapshotUsableForSync) throw IOException("Incomplete library snapshot")
    val items = page.items.toMutableList()
    var continuation = page.continuation
    val seenContinuations = mutableSetOf<String>()
    var requestCount = 0
    val maxRequests = 50
    var consecutiveEmptyResponses = 0
    
    while (continuation != null) {
        currentCoroutineContext().ensureActive()
        if (requestCount >= maxRequests || !seenContinuations.add(continuation)) {
            throw IOException("Library continuation did not complete")
        }
        requestCount++
        
        val continuationPage = YouTube.libraryContinuation(continuation).getOrThrow()
        if (!continuationPage.isSnapshotUsableForSync) throw IOException("Incomplete library continuation")
        
        if (continuationPage.items.isEmpty()) {
            consecutiveEmptyResponses++
            if (consecutiveEmptyResponses >= 2 && continuationPage.continuation != null) {
                throw IOException("Library continuation made no progress")
            }
        } else {
            consecutiveEmptyResponses = 0
            items += continuationPage.items
        }
        
        continuation = continuationPage.continuation
    }
    LibraryPage(
        items = items,
        continuation = null
    )
}

fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

fun sha1(str: String): String = MessageDigest.getInstance("SHA-1").digest(str.toByteArray()).toHex()

fun parseCookieString(cookie: String): Map<String, String> =
    cookie.split("; ")
        .filter { it.isNotEmpty() }
        .mapNotNull { part ->
            val splitIndex = part.indexOf('=')
            if (splitIndex == -1) null
            else part.substring(0, splitIndex) to part.substring(splitIndex + 1)
        }
        .toMap()

fun String.parseTime(): Int? {
    try {
        // YouTube Music returns duration with locale-dependent separators
        // (":" en-US, "." some locales, "," EU). Accept all.
        val parts = split(Regex("[:.,]")).map { it.toInt() }
        if (parts.size == 2) {
            return parts[0] * 60 + parts[1]
        }
        if (parts.size == 3) {
            return parts[0] * 3600 + parts[1] * 60 + parts[2]
        }
    } catch (e: Exception) {
        return null
    }
    return null
}

fun isPrivateId(browseId: String): Boolean {
    return browseId.contains("privately")
}
