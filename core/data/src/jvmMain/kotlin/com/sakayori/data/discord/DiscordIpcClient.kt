package com.sakayori.data.discord

import com.sakayori.domain.data.entities.SongEntity
import com.sakayori.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DiscordIpcClient {
    private var pipe: RandomAccessFile? = null
    private var connected = false
    private var songStartTime = 0L

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        for (i in 0..9) {
            try {
                val pipePath = "\\\\.\\pipe\\discord-ipc-$i"
                pipe = RandomAccessFile(pipePath, "rw")
                val handshake = json.encodeToString(
                    IpcHandshake(v = 1, client_id = APPLICATION_ID)
                )
                sendPacket(Opcode.HANDSHAKE, handshake)
                val response = readPacket()
                if (response != null) {
                    connected = true
                    return@withContext true
                }
            } catch (_: Exception) {
                pipe?.close()
                pipe = null
            }
        }
        connected = false
        false
    }

    suspend fun updateActivity(song: SongEntity) = withContext(Dispatchers.IO) {
        if (!connected) {
            if (!connect()) return@withContext
        }
        try {
            songStartTime = System.currentTimeMillis()
            val durationMs = (song.durationSeconds?.toLong() ?: 0L) * 1000L
            val payload = json.encodeToString(
                IpcSetActivity(
                    cmd = "SET_ACTIVITY",
                    args = SetActivityArgs(
                        pid = ProcessHandle.current().pid().toInt(),
                        activity = RpcActivity(
                            type = 2,
                            details = song.title,
                            state = song.artistName?.joinToString(", "),
                            assets = RpcAssets(
                                large_image = song.thumbnails?.let { "https://i.ytimg.com/vi/${song.videoId}/maxresdefault.jpg" },
                                large_text = song.albumName ?: song.title,
                                small_image = "app_icon",
                                small_text = APP_NAME,
                            ),
                            timestamps = if (durationMs > 0) {
                                RpcTimestamps(end = songStartTime + durationMs)
                            } else {
                                RpcTimestamps(start = songStartTime)
                            },
                        ),
                    ),
                    nonce = System.currentTimeMillis().toString(),
                )
            )
            sendPacket(Opcode.FRAME, payload)
            readPacket()
        } catch (e: Exception) {
            disconnect()
        }
    }

    suspend fun clearActivity() = withContext(Dispatchers.IO) {
        if (!connected) return@withContext
        try {
            val payload = json.encodeToString(
                IpcSetActivity(
                    cmd = "SET_ACTIVITY",
                    args = SetActivityArgs(
                        pid = ProcessHandle.current().pid().toInt(),
                        activity = null,
                    ),
                    nonce = System.currentTimeMillis().toString(),
                )
            )
            sendPacket(Opcode.FRAME, payload)
        } catch (_: Exception) {}
    }

    fun disconnect() {
        try {
            if (connected) {
                val payload = json.encodeToString(
                    IpcSetActivity(
                        cmd = "SET_ACTIVITY",
                        args = SetActivityArgs(
                            pid = ProcessHandle.current().pid().toInt(),
                            activity = null,
                        ),
                        nonce = System.currentTimeMillis().toString(),
                    )
                )
                sendPacket(Opcode.FRAME, payload)
                Thread.sleep(100)
                sendPacket(Opcode.CLOSE, "{}")
            }
        } catch (_: Exception) {} finally {
            try { pipe?.close() } catch (_: Exception) {}
            pipe = null
            connected = false
        }
    }

    fun isConnected() = connected

    private fun sendPacket(opcode: Opcode, data: String) {
        val p = pipe ?: return
        val bytes = data.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(8 + bytes.size)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(opcode.code)
        buffer.putInt(bytes.size)
        buffer.put(bytes)
        p.write(buffer.array())
    }

    private fun readPacket(): String? {
        val p = pipe ?: return null
        val header = ByteArray(8)
        p.readFully(header)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        buffer.getInt()
        val length = buffer.getInt()
        if (length <= 0 || length > 65536) return null
        val data = ByteArray(length)
        p.readFully(data)
        return String(data, Charsets.UTF_8)
    }

    private enum class Opcode(val code: Int) {
        HANDSHAKE(0),
        FRAME(1),
        CLOSE(2),
    }

    companion object {
        private const val APPLICATION_ID = "1493865560013017160"
        private const val APP_NAME = "SakayoriMusic"
    }
}

@Serializable
private data class IpcHandshake(val v: Int, val client_id: String)

@Serializable
private data class IpcSetActivity(
    val cmd: String,
    val args: SetActivityArgs,
    val nonce: String,
)

@Serializable
private data class SetActivityArgs(
    val pid: Int,
    val activity: RpcActivity?,
)

@Serializable
private data class RpcActivity(
    val type: Int? = null,
    val details: String? = null,
    val state: String? = null,
    val assets: RpcAssets? = null,
    val timestamps: RpcTimestamps? = null,
)

@Serializable
private data class RpcAssets(
    val large_image: String? = null,
    val large_text: String? = null,
    val small_image: String? = null,
    val small_text: String? = null,
)

@Serializable
private data class RpcTimestamps(
    val start: Long? = null,
    val end: Long? = null,
)
