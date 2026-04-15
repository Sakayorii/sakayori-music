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
import javax.swing.UIManager
import javax.swing.plaf.basic.BasicProgressBarUI

class SplashScreen {
    private var frame: JFrame? = null
    private var statusLabel: JLabel? = null
    private var progressBar: JProgressBar? = null

    fun show() {
        SwingUtilities.invokeAndWait {
            val bg = Color(12, 12, 12)
            val accent = Color(0, 188, 212)
            val textDim = Color(100, 100, 100)

            frame = JFrame().apply {
                isUndecorated = true
                background = bg
                preferredSize = Dimension(380, 220)
                isResizable = false
                defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
            }

            val mainPanel = object : JPanel(BorderLayout(0, 0)) {
                override fun paintComponent(g: Graphics) {
                    val g2 = g as Graphics2D
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.color = bg
                    g2.fillRoundRect(0, 0, width, height, 16, 16)
                }
            }
            mainPanel.isOpaque = false
            mainPanel.background = bg
            mainPanel.border = BorderFactory.createEmptyBorder(24, 24, 20, 24)

            val topPanel = JPanel(FlowLayout(FlowLayout.CENTER, 12, 0))
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
                    val scaled = img.getScaledInstance(48, 48, java.awt.Image.SCALE_SMOOTH)
                    topPanel.add(JLabel(ImageIcon(scaled)))
                }
            } catch (_: Exception) {}

            val titleLabel = JLabel("SakayoriMusic")
            titleLabel.font = Font("Segoe UI", Font.BOLD, 20)
            titleLabel.foreground = Color.WHITE
            topPanel.add(titleLabel)

            mainPanel.add(topPanel, BorderLayout.NORTH)

            val centerPanel = JPanel(BorderLayout(0, 8))
            centerPanel.isOpaque = false
            centerPanel.border = BorderFactory.createEmptyBorder(20, 0, 0, 0)

            progressBar = JProgressBar().apply {
                isIndeterminate = true
                preferredSize = Dimension(332, 3)
                background = Color(30, 30, 30)
                foreground = accent
                isBorderPainted = false
                setUI(object : BasicProgressBarUI() {
                    override fun getPreferredSize(c: javax.swing.JComponent): Dimension {
                        return Dimension(super.getPreferredSize(c).width, 3)
                    }
                })
            }
            centerPanel.add(progressBar, BorderLayout.NORTH)

            statusLabel = JLabel("Starting...").apply {
                font = Font("Segoe UI", Font.PLAIN, 11)
                foreground = textDim
                horizontalAlignment = SwingConstants.LEFT
                border = BorderFactory.createEmptyBorder(6, 0, 0, 0)
            }
            centerPanel.add(statusLabel, BorderLayout.CENTER)

            val versionLabel = JLabel("v${com.sakayori.music.utils.VersionManager.getVersionName()}")
            versionLabel.font = Font("Segoe UI", Font.PLAIN, 10)
            versionLabel.foreground = Color(60, 60, 60)
            versionLabel.horizontalAlignment = SwingConstants.RIGHT
            centerPanel.add(versionLabel, BorderLayout.SOUTH)

            mainPanel.add(centerPanel, BorderLayout.CENTER)

            frame?.apply {
                contentPane = mainPanel
                pack()
                setLocationRelativeTo(null)
                isVisible = true
            }
        }
    }

    fun updateStatus(text: String) {
        SwingUtilities.invokeLater {
            statusLabel?.text = text
        }
    }

    fun close() {
        SwingUtilities.invokeLater {
            frame?.isVisible = false
            frame?.dispose()
            frame = null
        }
    }
}
