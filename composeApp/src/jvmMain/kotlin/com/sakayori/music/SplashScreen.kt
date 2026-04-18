package com.sakayori.music

import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.Point2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

class SplashScreen {
    private var frame: JFrame? = null
    private var contentPanel: ContentPanel? = null
    private var spinTimer: Timer? = null
    private var dotTimer: Timer? = null
    private var currentProgress = 0f
    private var targetProgress = 0f
    private var progressTimer: Timer? = null

    private val taglines = listOf(
        "Ad-Free YouTube Music On Your Device",
        "Your Library. Your Rules.",
        "Cross-Platform. Open Source. Free Forever.",
        "Listen Local. Stream Global.",
        "Music Without Compromise.",
    )

    private class ContentPanel(
        private val image: BufferedImage?,
        private val tagline: String,
        private val versionText: String,
    ) : JPanel() {
        var spinAngle: Double = 0.0
        var progress: Float = 0f
        var status: String = "Starting"
        var dotPhase: Int = 0

        private val accent = Color(0, 188, 212)
        private val accentSoft = Color(0, 188, 212, 40)
        private val bgOuter = Color(8, 8, 10)
        private val bgInner = Color(22, 22, 28)
        private val borderColor = Color(40, 40, 48)
        private val textPrimary = Color(245, 245, 245)
        private val textSoft = Color(180, 180, 190)
        private val textFaint = Color(110, 110, 120)

        init {
            isOpaque = false
            preferredSize = Dimension(520, 320)
            background = bgOuter
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

            val arc = 24f
            val shape = RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), arc, arc)
            g2.clip = shape

            val radialPaint = RadialGradientPaint(
                Point2D.Float(width / 2f, height * 0.35f),
                (width * 0.75f),
                floatArrayOf(0f, 0.6f, 1f),
                arrayOf(bgInner, bgOuter, Color(4, 4, 6)),
            )
            g2.paint = radialPaint
            g2.fillRoundRect(0, 0, width, height, arc.toInt(), arc.toInt())

            g2.paint = GradientPaint(
                0f, 0f, accent,
                width.toFloat(), 0f, accent.darker(),
            )
            g2.fillRect(0, 0, width, 2)

            drawIcon(g2)
            drawTitle(g2)
            drawTagline(g2)
            drawProgress(g2)
            drawStatus(g2)
            drawVersion(g2)

            g2.paint = borderColor
            g2.stroke = BasicStroke(1f)
            g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, width - 1f, height - 1f, arc, arc))
        }

        private fun drawIcon(g2: Graphics2D) {
            val iconSize = 88
            val cx = width / 2
            val cy = 95

            val glow = RadialGradientPaint(
                Point2D.Float(cx.toFloat(), cy.toFloat()),
                iconSize.toFloat() * 0.9f,
                floatArrayOf(0f, 0.5f, 1f),
                arrayOf(accentSoft, Color(0, 188, 212, 16), Color(0, 188, 212, 0)),
            )
            val prevPaint = g2.paint
            g2.paint = glow
            g2.fill(Ellipse2D.Float(
                cx - iconSize * 0.95f, cy - iconSize * 0.95f,
                iconSize * 1.9f, iconSize * 1.9f,
            ))
            g2.paint = prevPaint

            if (image != null) {
                val tx = AffineTransform.getRotateInstance(spinAngle, cx.toDouble(), cy.toDouble())
                tx.translate((cx - iconSize / 2).toDouble(), (cy - iconSize / 2).toDouble())
                tx.scale(iconSize.toDouble() / image.width, iconSize.toDouble() / image.height)
                g2.drawImage(image, tx, null)
            }

            g2.paint = Color(0, 188, 212, 140)
            g2.stroke = BasicStroke(1.2f)
            g2.draw(Ellipse2D.Float(
                (cx - iconSize / 2).toFloat(),
                (cy - iconSize / 2).toFloat(),
                iconSize.toFloat(),
                iconSize.toFloat(),
            ))
        }

        private fun drawTitle(g2: Graphics2D) {
            g2.font = Font("Segoe UI", Font.BOLD, 28)
            g2.paint = textPrimary
            val metrics = g2.fontMetrics
            val text = "SakayoriMusic"
            val textWidth = metrics.stringWidth(text)
            g2.drawString(text, (width - textWidth) / 2, 175)

            g2.font = Font("Segoe UI", Font.BOLD, 28)
            val sakayoriWidth = metrics.stringWidth("Sakayori")
            g2.paint = accent
            g2.drawString("Music", (width - textWidth) / 2 + sakayoriWidth, 175)
        }

        private fun drawTagline(g2: Graphics2D) {
            g2.font = Font("Segoe UI", Font.ITALIC, 12)
            g2.paint = textSoft
            val metrics = g2.fontMetrics
            val textWidth = metrics.stringWidth(tagline)
            g2.drawString(tagline, (width - textWidth) / 2, 200)
        }

        private fun drawProgress(g2: Graphics2D) {
            val barY = 235
            val barHeight = 4
            val barInset = 48
            val barWidth = width - (barInset * 2)

            g2.paint = Color(30, 30, 36)
            g2.fill(RoundRectangle2D.Float(
                barInset.toFloat(), barY.toFloat(),
                barWidth.toFloat(), barHeight.toFloat(),
                barHeight.toFloat(), barHeight.toFloat(),
            ))

            if (progress > 0f) {
                val filledWidth = barWidth * progress
                g2.paint = GradientPaint(
                    barInset.toFloat(), 0f, accent,
                    barInset + filledWidth, 0f, Color(38, 198, 218),
                )
                g2.fill(RoundRectangle2D.Float(
                    barInset.toFloat(), barY.toFloat(),
                    filledWidth, barHeight.toFloat(),
                    barHeight.toFloat(), barHeight.toFloat(),
                ))
            }
        }

        private fun drawStatus(g2: Graphics2D) {
            g2.font = Font("JetBrains Mono", Font.PLAIN, 11)
            if (g2.font.family != "JetBrains Mono") {
                g2.font = Font("Consolas", Font.PLAIN, 11)
            }
            g2.paint = textFaint

            val dots = when (dotPhase % 4) {
                0 -> "   "
                1 -> "·  "
                2 -> "·· "
                else -> "···"
            }
            val text = "${status.uppercase()} $dots"
            g2.drawString(text, 48, 265)
        }

        private fun drawVersion(g2: Graphics2D) {
            g2.font = Font("JetBrains Mono", Font.PLAIN, 10)
            if (g2.font.family != "JetBrains Mono") {
                g2.font = Font("Consolas", Font.PLAIN, 10)
            }
            g2.paint = textFaint
            val metrics = g2.fontMetrics
            val textWidth = metrics.stringWidth(versionText)
            g2.drawString(versionText, width - textWidth - 48, 265)
        }
    }

    fun show() {
        SwingUtilities.invokeAndWait {
            val img = loadIcon()
            val tagline = taglines.random()
            val version = "v${com.sakayori.music.utils.VersionManager.getVersionName()}"

            val panel = ContentPanel(img, tagline, version)
            contentPanel = panel

            frame = JFrame().apply {
                isUndecorated = true
                background = Color(0, 0, 0, 0)
                preferredSize = Dimension(520, 320)
                isResizable = false
                defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
                contentPane.background = Color(0, 0, 0, 0)
                contentPane = JPanel(BorderLayout()).apply {
                    isOpaque = false
                    background = Color(0, 0, 0, 0)
                    border = BorderFactory.createEmptyBorder()
                    add(panel, BorderLayout.CENTER)
                }
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

            spinTimer = Timer(16) {
                val p = contentPanel ?: return@Timer
                p.spinAngle += Math.toRadians(1.2)
                p.repaint()
            }
            spinTimer?.start()

            dotTimer = Timer(400) {
                val p = contentPanel ?: return@Timer
                p.dotPhase = (p.dotPhase + 1) % 4
                p.repaint()
            }
            dotTimer?.start()
        }
    }

    private fun loadIcon(): BufferedImage? {
        return try {
            val iconPaths = listOf(
                "composeApp/icon/circle_app_icon.png",
                "icon/circle_app_icon.png",
                "../composeApp/icon/circle_app_icon.png",
            )
            val iconFile = iconPaths.map { java.io.File(it) }.firstOrNull { it.exists() }
            if (iconFile != null) {
                ImageIO.read(iconFile)
            } else {
                val stream = SplashScreen::class.java.getResourceAsStream("/circle_app_icon.png")
                stream?.let { ImageIO.read(it) }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun updateStatus(text: String) {
        SwingUtilities.invokeLater {
            val panel = contentPanel ?: return@invokeLater
            panel.status = text.trimEnd('.', ' ')
            targetProgress = (targetProgress + 0.2f).coerceAtMost(1f)
            animateProgress()
            panel.repaint()
        }
    }

    private fun animateProgress() {
        progressTimer?.stop()
        progressTimer = Timer(16) {
            val panel = contentPanel ?: return@Timer
            if (currentProgress < targetProgress) {
                currentProgress = (currentProgress + 0.015f).coerceAtMost(targetProgress)
                panel.progress = currentProgress
                panel.repaint()
            } else {
                progressTimer?.stop()
            }
        }
        progressTimer?.start()
    }

    fun close() {
        SwingUtilities.invokeLater {
            val f = frame ?: return@invokeLater
            Timer(10) { e ->
                val next = (f.opacity - 0.1f).coerceAtLeast(0f)
                f.opacity = next
                if (next <= 0f) {
                    (e.source as Timer).stop()
                    spinTimer?.stop()
                    spinTimer = null
                    dotTimer?.stop()
                    dotTimer = null
                    progressTimer?.stop()
                    progressTimer = null
                    f.isVisible = false
                    f.dispose()
                    frame = null
                    contentPanel = null
                }
            }.start()
        }
    }
}
