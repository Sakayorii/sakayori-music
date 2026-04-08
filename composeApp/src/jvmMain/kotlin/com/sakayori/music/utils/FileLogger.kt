package com.sakayori.music.utils

import java.io.File
import java.io.PrintWriter
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

object FileLogger {
    private val logDir: File by lazy {
        val baseDir = File(System.getProperty("user.home"), ".sakayori-music/logs")
        if (!baseDir.exists()) baseDir.mkdirs()
        baseDir
    }

    private val logFile: File by lazy {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        File(logDir, "sakayori-$timestamp.log").also {
            if (!it.exists()) it.createNewFile()
        }
    }

    private val queue = ConcurrentLinkedQueue<String>()
    private val running = AtomicBoolean(false)
    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    init {
        startFlushThread()
        cleanOldLogs()
    }

    fun d(tag: String, message: String) = enqueue("DEBUG", tag, message)
    fun i(tag: String, message: String) = enqueue("INFO", tag, message)
    fun w(tag: String, message: String) = enqueue("WARN", tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        enqueue("ERROR", tag, message)
        if (throwable != null) {
            val sw = java.io.StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            enqueue("ERROR", tag, sw.toString())
        }
    }

    private fun enqueue(level: String, tag: String, message: String) {
        val time = LocalDateTime.now().format(formatter)
        queue.add("$time [$level] [$tag] $message")
    }

    private fun startFlushThread() {
        if (!running.compareAndSet(false, true)) return
        Thread {
            while (true) {
                try {
                    Thread.sleep(500)
                    flushQueue()
                } catch (_: InterruptedException) {
                    flushQueue()
                    return@Thread
                } catch (_: Throwable) {
                }
            }
        }.apply {
            isDaemon = true
            name = "FileLogger-Flush"
            start()
        }
        Runtime.getRuntime().addShutdownHook(
            Thread {
                flushQueue()
            }.apply { name = "FileLogger-Shutdown" },
        )
    }

    private fun flushQueue() {
        if (queue.isEmpty()) return
        try {
            val buffer = StringBuilder()
            while (queue.isNotEmpty()) {
                val line = queue.poll() ?: break
                buffer.append(line).append('\n')
            }
            if (buffer.isNotEmpty()) {
                logFile.appendText(buffer.toString())
            }
        } catch (_: Throwable) {
        }
    }

    private fun cleanOldLogs() {
        try {
            val cutoffMs = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
            logDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.startsWith("sakayori-") && file.name.endsWith(".log") && file.lastModified() < cutoffMs) {
                    Files.deleteIfExists(file.toPath())
                }
            }
        } catch (_: Throwable) {
        }
    }

    fun getLogDirectory(): File = logDir

    fun getCurrentLogFile(): File = logFile
}
