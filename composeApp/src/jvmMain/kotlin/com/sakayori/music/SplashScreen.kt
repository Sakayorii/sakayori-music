package com.sakayori.music

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.ImageIcon
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.plaf.basic.BasicProgressBarUI

class SplashScreen {
    private var frame: JFrame? = null
    private var statusLabel: JLabel? = null
    private var progressBar: JProgressBar? = null
    private var taglineLabel: JLabel? = null
    private var currentProgress = 0

    private val taglines = listOf(
        "Ad-free YouTube Music on your device",
        "Your library. Your rules.",
        "Cross-platform. Open source. Free forever.",
        "Listen local. Stream global.",
        "Music without compromise.",
    )

    fun show() {
        SwingUtilities.invokeAndWait {
            val bg = Color(12, 12, 12)
            val accent = Color(0, 188, 212)
            val textDim = Color(120, 120, 120)

            frame = JFrame().apply {
                isUndecorated = true
                background = bg
                preferredSize = Dimension(420, 260)
                isResizable = false
                defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
            }

            val mainPanel = object : JPanel(BorderLayout(0, 0)) {
                override fun paintComponent(g: Graphics) {
                    val g2 = g as Graphics2D
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.color = bg
                    g2.fillRoundRect(0, 0, width, height, 20, 20)
                    g2.color = Color(25, 25, 25)
                    g2.drawRoundRect(0, 0, width - 1, height - 1, 20, 20)
                }
            }
            mainPanel.isOpaque = false
            mainPanel.background = bg
            mainPanel.border = BorderFactory.createEmptyBorder(28, 28, 24, 28)

            val topPanel = JPanel(FlowLayout(FlowLayout.CENTER, 14, 0))
            topPanel.isOpaque = false

            try {
                val iconPaths = listOf(
                    "composeApp/icon/circle_app_icon.png",
                    "icon/circle_app_icon.png",
                    "../composeApp/icon/circle_app_icon.png",
                )
                val iconFile = iconPaths.map { java.io.File(it) }.firstOrNull { it.exists() }
                val img = if (iconFile != null) {
                    ImageIO.read(iconFile)
                } else {
                    val stream = SplashScreen::class.java.getResourceAsStream("/circle_app_icon.png")
                    stream?.let { ImageIO.read(it) }
                }
                if (img != null) {
                    val scaled = img.getScaledInstance(56, 56, java.awt.Image.SCALE_SMOOTH)
                    topPanel.add(JLabel(ImageIcon(scaled)))
                }
            } catch (_: Exception) {}

            val titleColumn = JPanel()
            titleColumn.isOpaque = false
            titleColumn.layout = javax.swing.BoxLayout(titleColumn, javax.swing.BoxLayout.Y_AXIS)

            val titleLabel = JLabel("SakayoriMusic")
            titleLabel.font = Font("Segoe UI", Font.BOLD, 22)
            titleLabel.foreground = Color.WHITE
            titleLabel.alignmentX = java.awt.Component.LEFT_ALIGNMENT
            titleColumn.add(titleLabel)

            taglineLabel = JLabel(taglines.random())
            taglineLabel!!.font = Font("Segoe UI", Font.PLAIN, 11)
            taglineLabel!!.foreground = accent
            taglineLabel!!.alignmentX = java.awt.Component.LEFT_ALIGNMENT
            titleColumn.add(taglineLabel)

            topPanel.add(titleColumn)
            mainPanel.add(topPanel, BorderLayout.NORTH)

            val centerPanel = JPanel(BorderLayout(0, 10))
            centerPanel.isOpaque = false
            centerPanel.border = BorderFactory.createEmptyBorder(32, 0, 0, 0)

            progressBar = JProgressBar(0, 100).apply {
                value = 0
                isIndeterminate = false
                preferredSize = Dimension(364, 4)
                background = Color(28, 28, 28)
                foreground = accent
                isBorderPainted = false
                setUI(object : BasicProgressBarUI() {
                    override fun getPreferredSize(c: javax.swing.JComponent): Dimension {
                        return Dimension(super.getPreferredSize(c).width, 4)
                    }
                })
            }
            centerPanel.add(progressBar, BorderLayout.NORTH)

            statusLabel = JLabel("Starting...").apply {
                font = Font("Segoe UI", Font.PLAIN, 11)
                foreground = textDim
                horizontalAlignment = SwingConstants.LEFT
                border = BorderFactory.createEmptyBorder(10, 0, 0, 0)
            }
            centerPanel.add(statusLabel, BorderLayout.CENTER)

            val versionLabel = JLabel("v${com.sakayori.music.utils.VersionManager.getVersionName()}")
            versionLabel.font = Font("Segoe UI", Font.PLAIN, 10)
            versionLabel.foreground = Color(70, 70, 70)
            versionLabel.horizontalAlignment = SwingConstants.RIGHT
            centerPanel.add(versionLabel, BorderLayout.SOUTH)

            mainPanel.add(centerPanel, BorderLayout.CENTER)

            frame?.apply {
                contentPane = mainPanel
                pack()
                setLocationRelativeTo(null)
                opacity = 0f
                isVisible = true
            }

            Timer(10) { e ->
                val f = frame ?: return@Timer
                val next = (f.opacity + 0.08f).coerceAtMost(1f)
                f.opacity = next
                if (next >= 1f) {
                    (e.source as Timer).stop()
                }
            }.start()
        }
    }

    fun updateStatus(text: String) {
        SwingUtilities.invokeLater {
            statusLabel?.text = text
            currentProgress = (currentProgress + 20).coerceAtMost(100)
            animateProgress(currentProgress)
        }
    }

    private fun animateProgress(target: Int) {
        val current = progressBar?.value ?: 0
        if (current >= target) return
        Timer(12) { e ->
            val bar = progressBar ?: return@Timer
            if (bar.value < target) {
                bar.value += 1
            } else {
                (e.source as Timer).stop()
            }
        }.start()
    }

    fun close() {
        SwingUtilities.invokeLater {
            val f = frame ?: return@invokeLater
            Timer(10) { e ->
                val next = (f.opacity - 0.1f).coerceAtLeast(0f)
                f.opacity = next
                if (next <= 0f) {
                    (e.source as Timer).stop()
                    f.isVisible = false
                    f.dispose()
                    frame = null
                }
            }.start()
        }
    }
}
