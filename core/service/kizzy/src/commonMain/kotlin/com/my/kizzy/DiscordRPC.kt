package com.my.kizzy

import com.sakayori.domain.data.entities.SongEntity
import com.my.kizzy.rpc.KizzyRPC
import com.my.kizzy.rpc.RpcImage
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class DiscordRPC(
    token: String
) : KizzyRPC(token) {
    @OptIn(ExperimentalTime::class)
    suspend fun updateSong(song: SongEntity) = runCatching {
        setActivity(
            name = APP_NAME,
            details = song.title,
            state = song.artistName?.joinToString(", "),
            largeImage = song.thumbnails?.let { RpcImage.ExternalImage(it) },
            smallImage = RpcImage.ExternalImage(APP_ICON),
            largeText = song.albumName,
            smallText = song.artistName?.firstOrNull(),
            buttons = listOf(
                "Listen on SakayoriMusic" to "https://music.sakayori.dev/play/${song.videoId}",
                "Visit SakayoriMusic" to "https://music.sakayori.dev"
            ),
            type = Type.LISTENING,
            since = Clock.System.now().toEpochMilliseconds(),
            applicationId = APPLICATION_ID
        )
    }

    companion object {
        private const val APPLICATION_ID = "1271273225120125040"
        private const val APP_NAME: String = "SakayoriMusic"
        private const val APP_ICON: String =
            "https://raw.githubusercontent.com/Sakayorii/sakayori-music/main/composeApp/icon/circle_app_icon.png"
    }
}
