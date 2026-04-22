package com.sakayori.music

import com.sakayori.music.utils.VersionManager
import io.sentry.Sentry
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.PrintWriter
import java.io.StringWriter
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.UIManager
import kotlin.system.exitProcess

object CrashDialog {

    fun install() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (throwable is OutOfMemoryError) {
                try {
                    System.err.println(
                        "OutOfMemoryError in ${thread.name}: ${throwable.message}. Force-halting to prevent hang.",
                    )
                    val log = java.io.File(System.getProperty("user.home"), ".sakayori-music/oom.log")
                    log.parentFile.mkdirs()
                    log.appendText("[${java.time.LocalDateTime.now()}] OOM in ${thread.name}: ${throwable.message}\n")
                } catch (_: Throwable) {
                }
                Runtime.getRuntime().halt(139)
                return@setDefaultUncaughtExceptionHandler
            }

            try {
                val crashLog = java.io.File(System.getProperty("user.home"), ".sakayori-music/uncaught.log")
                crashLog.parentFile.mkdirs()
                val entry = buildString {
                    append("[${java.time.LocalDateTime.now()}] Thread: ${thread.name}\n")
                    append("${throwable.javaClass.name}: ${throwable.message}\n")
                    throwable.stackTrace.take(20).forEach { append("  at $it\n") }
                    var cause = throwable.cause
                    while (cause != null) {
                        append("Caused by: ${cause.javaClass.name}: ${cause.message}\n")
                        cause.stackTrace.take(10).forEach { append("  at $it\n") }
                        cause = cause.cause
                    }
                    append("\n")
                }
                crashLog.appendText(entry)
            } catch (_: Throwable) {
            }

            try {
                if (com.sakayori.music.utils.DesktopCrashReporting.isEnabled()) {
                    Sentry.captureException(throwable)
                    Sentry.flush(5000L)
                }
            } catch (_: Exception) {
            }

            val isFatalThread = thread.name == "main" ||
                thread.name.startsWith("AWT-EventQueue") ||
                SwingUtilities.isEventDispatchThread()

            if (!isFatalThread) {
                System.err.println("Non-fatal uncaught exception in ${thread.name}: ${throwable.message}")
                return@setDefaultUncaughtExceptionHandler
            }

            Thread {
                try { Thread.sleep(10_000) } catch (_: Throwable) {}
                System.err.println("Crash dialog hanging >10s, force-halting")
                Runtime.getRuntime().halt(140)
            }.apply { isDaemon = true; name = "CrashDialogWatchdog" }.start()

            try {
                if (SwingUtilities.isEventDispatchThread()) {
                    showCrashDialog(thread, throwable)
                } else {
                    SwingUtilities.invokeAndWait {
                        showCrashDialog(thread, throwable)
                    }
                }
            } catch (_: Exception) {
                System.err.println("Fatal crash in thread ${thread.name}:")
            }

            exitProcess(1)
        }
    }

    private fun showCrashDialog(thread: Thread, throwable: Throwable) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        } catch (_: Exception) {
        }

        val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val versionInfo = try {
            "SakayoriMusic Desktop v${VersionManager.getVersionName()}"
        } catch (_: Exception) {
            "SakayoriMusic Desktop"
        }

        val dialog = JDialog().apply {
            title = "SakayoriMusic - Unexpected Error"
            isModal = true
            defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
            preferredSize = Dimension(700, 500)
            minimumSize = Dimension(500, 350)
        }

        val contentPanel = JPanel(BorderLayout(0, 12)).apply {
            border = BorderFactory.createEmptyBorder(16, 16, 16, 16)
        }

        val headerPanel = JPanel(BorderLayout(8, 4)).apply {
            val titleLabel = JLabel("SakayoriMusic has crashed").apply {
                font = font.deriveFont(Font.BOLD, 16f)
            }
            val subtitleLabel = JLabel(
                "<html>An unexpected error occurred. The stack trace below may help diagnose the issue.<br>" +
                    "<font color='gray'>$versionInfo · Thread: ${thread.name}</font></html>"
            ).apply {
                font = font.deriveFont(12f)
            }
            add(titleLabel, BorderLayout.NORTH)
            add(subtitleLabel, BorderLayout.CENTER)
        }

        val textArea = JTextArea(stackTrace).apply {
            isEditable = false
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            caretPosition = 0
            background = Color(30, 30, 30)
            foreground = Color(210, 210, 210)
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        }

        val scrollPane = JScrollPane(textArea).apply {
            border = BorderFactory.createLineBorder(Color(80, 80, 80))
        }

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))

        val copyButton = JButton("Copy Stack Trace").apply {
            addActionListener {
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(StringSelection(stackTrace), null)
                text = "Copied!"
                isEnabled = false
            }
        }

        val closeButton = JButton("Close").apply {
            addActionListener {
                dialog.dispose()
            }
        }

        buttonPanel.add(copyButton)
        buttonPanel.add(closeButton)

        contentPanel.add(headerPanel, BorderLayout.NORTH)
        contentPanel.add(scrollPane, BorderLayout.CENTER)
        contentPanel.add(buttonPanel, BorderLayout.SOUTH)

        dialog.contentPane = contentPanel
        dialog.pack()
        dialog.setLocationRelativeTo(null)
        dialog.isVisible = true
    }
}
