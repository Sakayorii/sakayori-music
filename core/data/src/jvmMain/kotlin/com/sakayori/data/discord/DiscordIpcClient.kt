package com.sakayori.data.discord

import com.sakayori.domain.data.entities.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.RandomAccessFile
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class DiscordIpcClient {
    private var transport: Transport? = null
    private var connected = false
    private var songStartTime = 0L

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        for (i in 0..9) {
            val candidate = openTransport(i) ?: continue
            try {
                val handshake = json.encodeToString(
                    IpcHandshake(v = 1, client_id = APPLICATION_ID)
                )
                candidate.send(Opcode.HANDSHAKE, handshake)
                val response = candidate.receive()
                if (response != null) {
                    transport = candidate
                    connected = true
                    return@withContext true
                }
                candidate.close()
            } catch (_: Exception) {
                candidate.close()
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
            val durationMs = song.durationSeconds.toLong() * 1000L
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
            transport?.send(Opcode.FRAME, payload)
            transport?.receive()
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
            transport?.send(Opcode.FRAME, payload)
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
                transport?.send(Opcode.FRAME, payload)
                Thread.sleep(100)
                transport?.send(Opcode.CLOSE, "{}")
            }
        } catch (_: Exception) {} finally {
            try { transport?.close() } catch (_: Exception) {}
            transport = null
            connected = false
        }
    }

    fun isConnected() = connected

    private fun openTransport(index: Int): Transport? {
        val osName = System.getProperty("os.name").orEmpty().lowercase()
        return if (osName.contains("win")) {
            openWindowsPipe(index)
        } else {
            openUnixSocket(index)
        }
    }

    private fun openWindowsPipe(index: Int): Transport? = try {
        val raf = RandomAccessFile("\\\\.\\pipe\\discord-ipc-$index", "rw")
        WindowsPipeTransport(raf)
    } catch (_: Exception) {
        null
    }

    private fun openUnixSocket(index: Int): Transport? {
        for (dir in unixSocketDirs()) {
            val path = dir.resolve("discord-ipc-$index")
            if (!Files.exists(path)) continue
            try {
                val ch = SocketChannel.open(StandardProtocolFamily.UNIX)
                ch.connect(UnixDomainSocketAddress.of(path))
                return UnixSocketTransport(ch)
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun unixSocketDirs(): List<Path> {
        val dirs = linkedSetOf<String>()
        System.getenv("XDG_RUNTIME_DIR")?.takeIf { it.isNotBlank() }?.let { dirs += it }
        System.getenv("TMPDIR")?.takeIf { it.isNotBlank() }?.let { dirs += it }
        System.getenv("TMP")?.takeIf { it.isNotBlank() }?.let { dirs += it }
        System.getenv("TEMP")?.takeIf { it.isNotBlank() }?.let { dirs += it }
        dirs += "/tmp"

        val result = mutableListOf<Path>()
        for (d in dirs) {
            val base = Paths.get(d)
            result.add(base)
            result.add(base.resolve("app/com.discordapp.Discord"))
            result.add(base.resolve("app/com.discordapp.DiscordCanary"))
            result.add(base.resolve("snap.discord"))
            result.add(base.resolve("snap.discord-canary"))
        }
        return result
    }

    private interface Transport {
        fun send(opcode: Opcode, data: String)
        fun receive(): String?
        fun close()
    }

    private class WindowsPipeTransport(private val raf: RandomAccessFile) : Transport {
        override fun send(opcode: Opcode, data: String) {
            val bytes = data.toByteArray(Charsets.UTF_8)
            val buffer = ByteBuffer.allocate(8 + bytes.size)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(opcode.code)
                .putInt(bytes.size)
                .put(bytes)
            raf.write(buffer.array())
        }

        override fun receive(): String? {
            val header = ByteArray(8)
            raf.readFully(header)
            val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            buf.getInt()
            val length = buf.getInt()
            if (length <= 0 || length > 65536) return null
            val data = ByteArray(length)
            raf.readFully(data)
            return String(data, Charsets.UTF_8)
        }

        override fun close() {
            try { raf.close() } catch (_: Exception) {}
        }
    }

    private class UnixSocketTransport(private val channel: SocketChannel) : Transport {
        override fun send(opcode: Opcode, data: String) {
            val bytes = data.toByteArray(Charsets.UTF_8)
            val buffer = ByteBuffer.allocate(8 + bytes.size)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(opcode.code)
                .putInt(bytes.size)
                .put(bytes)
            buffer.flip()
            while (buffer.hasRemaining()) {
                channel.write(buffer)
            }
        }

        override fun receive(): String? {
            val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            readFully(header) ?: return null
            header.flip()
            header.getInt()
            val length = header.getInt()
            if (length <= 0 || length > 65536) return null
            val body = ByteBuffer.allocate(length)
            readFully(body) ?: return null
            return String(body.array(), 0, length, Charsets.UTF_8)
        }

        private fun readFully(buf: ByteBuffer): Unit? {
            while (buf.hasRemaining()) {
                val n = channel.read(buf)
                if (n < 0) return null
            }
            return Unit
        }

        override fun close() {
            try { channel.close() } catch (_: Exception) {}
        }
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
