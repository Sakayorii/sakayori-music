package com.sakayori.music.utils

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference

object SingleInstance {
    private const val PORT = 51847
    private const val FOCUS_COMMAND = "SAKAYORI_FOCUS"
    private const val LOCALHOST = "127.0.0.1"

    @Volatile
    private var serverSocket: ServerSocket? = null
    private val focusListener = AtomicReference<(() -> Unit)?>(null)

    fun acquire(): Boolean {
        return try {
            serverSocket = ServerSocket(PORT, 50, InetAddress.getByName(LOCALHOST))
            startListener()
            Runtime.getRuntime().addShutdownHook(
                Thread {
                    try {
                        serverSocket?.close()
                    } catch (_: Throwable) {
                    }
                },
            )
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun signalExisting(): Boolean {
        return try {
            Socket(LOCALHOST, PORT).use { socket ->
                socket.soTimeout = 1000
                PrintWriter(socket.getOutputStream(), true).use { writer ->
                    writer.println(FOCUS_COMMAND)
                }
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun setOnFocusRequested(listener: () -> Unit) {
        focusListener.set(listener)
    }

    private fun startListener() {
        Thread {
            while (true) {
                try {
                    val socket = serverSocket?.accept() ?: break
                    Thread {
                        try {
                            socket.use { s ->
                                BufferedReader(InputStreamReader(s.getInputStream())).use { reader ->
                                    val line = reader.readLine()
                                    if (line == FOCUS_COMMAND) {
                                        focusListener.get()?.invoke()
                                    }
                                }
                            }
                        } catch (_: Throwable) {
                        }
                    }.apply {
                        isDaemon = true
                        name = "SingleInstance-Handler"
                    }.start()
                } catch (_: Throwable) {
                    break
                }
            }
        }.apply {
            isDaemon = true
            name = "SingleInstance-Listener"
        }.start()
    }
}
