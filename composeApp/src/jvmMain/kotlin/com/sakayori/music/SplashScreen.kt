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
import java.awt.geom.Arc2D
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
import kotlin.math.cos
import kotlin.math.sin

class SplashScreen {
    private var frame: JFrame? = null
    private var contentPanel: ContentPanel? = null
    private var spinTimer: Timer? = null
    private var pulseTimer: Timer? = null
    private var dotTimer: Timer? = null
    private var equalizerTimer: Timer? = null
    private var progressTimer: Timer? = null
    private var currentProgress = 0f
    private var targetProgress = 0f

    private val taglines = listOf(
        "Listen Local. Stream Global.",
        "Your Library. Your Rules.",
        "Music Without Compromise.",
        "Open Source. Free Forever.",
        "No Ads. No Tracking. Ever.",
    )

    private class ContentPanel(
        private val image: BufferedImage?,
        private val tagline: String,
        private val versionText: String,
    ) : JPanel() {
        var pulsePhase: Float = 0f
        var ringRotation: Double = 0.0
        var progress: Float = 0f
        var status: String = "Starting"
        var dotPhase: Int = 0
        var equalizerBars: FloatArray = FloatArray(EQ_BARS) { 0.3f }

        private val accent = Color(0, 188, 212)
        private val accentBright = Color(38, 198, 218)
        private val accentSoft = Color(0, 188, 212, 60)
        private val accentVerySoft = Color(0, 188, 212, 22)
        private val bgOuter = Color(6, 6, 8)
        private val bgInner = Color(20, 22, 28)
        private val bgEdge = Color(2, 2, 4)
        private val borderColor = Color(45, 48, 56)
        private val textPrimary = Color(248, 248, 250)
        private val textSoft = Color(180, 182, 188)
        private val textFaint = Color(110, 112, 118)

        init {
            isOpaque = false
            preferredSize = Dimension(WIDTH_DP, HEIGHT_DP)
            background = bgOuter
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)

            val arc = 28f
            val shape = RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), arc, arc)
            g2.clip = shape

            drawBackground(g2)
            drawTopAccentLine(g2)
            drawCornerGlow(g2)
            drawIcon(g2)
            drawTitle(g2)
            drawTagline(g2)
            drawEqualizer(g2)
            drawProgress(g2)
            drawStatus(g2)
            drawVersionPill(g2)
            drawBorder(g2, arc)
        }

        private fun drawBackground(g2: Graphics2D) {
            val grad = GradientPaint(
                0f, 0f, bgInner,
                width.toFloat(), height.toFloat(), bgEdge,
            )
            g2.paint = grad
            g2.fillRect(0, 0, width, height)
        }

        private fun drawCornerGlow(g2: Graphics2D) {
            val pulse = (sin(pulsePhase.toDouble()) * 0.5 + 0.5).toFloat()
            val intensity = (40 + 60 * pulse).toInt()
            val tlGlow = RadialGradientPaint(
                Point2D.Float(0f, 0f),
                360f,
                floatArrayOf(0f, 1f),
                arrayOf(Color(0, 188, 212, intensity), Color(0, 188, 212, 0)),
            )
            g2.paint = tlGlow
            g2.fillOval(-200, -200, 720, 720)

            val brGlow = RadialGradientPaint(
                Point2D.Float(width.toFloat(), height.toFloat()),
                300f,
                floatArrayOf(0f, 1f),
                arrayOf(Color(38, 198, 218, 30), Color(0, 188, 212, 0)),
            )
            g2.paint = brGlow
            g2.fillOval(width - 320, height - 320, 640, 640)
        }

        private fun drawTopAccentLine(g2: Graphics2D) {
            g2.paint = GradientPaint(
                0f, 0f, accent,
                width.toFloat(), 0f, accentBright,
            )
            g2.fillRect(0, 0, width, 2)
        }

        private fun drawIcon(g2: Graphics2D) {
            val iconSize = 110
            val cx = width / 2
            val cy = 130

            val pulse = (sin(pulsePhase.toDouble()) * 0.5 + 0.5).toFloat()
            val glowSize = iconSize * (2.0f + pulse * 0.3f)
            val glow = RadialGradientPaint(
                Point2D.Float(cx.toFloat(), cy.toFloat()),
                glowSize / 2f,
                floatArrayOf(0f, 0.3f, 0.6f, 1f),
                arrayOf(
                    Color(0, 188, 212, (50 + 30 * pulse).toInt()),
                    Color(0, 188, 212, (25 + 15 * pulse).toInt()),
                    Color(0, 188, 212, 8),
                    Color(0, 188, 212, 0),
                ),
            )
            g2.paint = glow
            g2.fillOval(
                (cx - glowSize / 2f).toInt(), (cy - glowSize / 2f).toInt(),
                glowSize.toInt(), glowSize.toInt(),
            )

            drawRotatingArc(g2, cx, cy, iconSize / 2 + 14, 1.6f, 70.0, ringRotation)
            drawRotatingArc(g2, cx, cy, iconSize / 2 + 14, 1.6f, 70.0, ringRotation + Math.PI)
            drawRotatingArc(g2, cx, cy, iconSize / 2 + 22, 1.0f, 30.0, -ringRotation * 1.3)
            drawRotatingArc(g2, cx, cy, iconSize / 2 + 22, 1.0f, 30.0, -ringRotation * 1.3 + Math.PI)

            if (image != null) {
                val savedClip = g2.clip
                g2.clip(
                    Ellipse2D.Float(
                        (cx - iconSize / 2).toFloat(),
                        (cy - iconSize / 2).toFloat(),
                        iconSize.toFloat(),
                        iconSize.toFloat(),
                    ),
                )
                val tx = java.awt.geom.AffineTransform()
                tx.translate((cx - iconSize / 2).toDouble(), (cy - iconSize / 2).toDouble())
                tx.scale(iconSize.toDouble() / image.width, iconSize.toDouble() / image.height)
                g2.drawImage(image, tx, null)
                g2.clip = savedClip
            }

            g2.paint = Color(0, 188, 212, 200)
            g2.stroke = BasicStroke(1.5f)
            g2.draw(
                Ellipse2D.Float(
                    (cx - iconSize / 2).toFloat(),
                    (cy - iconSize / 2).toFloat(),
                    iconSize.toFloat(),
                    iconSize.toFloat(),
                ),
            )
        }

        private fun drawRotatingArc(g2: Graphics2D, cx: Int, cy: Int, radius: Int, stroke: Float, sweep: Double, startAngle: Double) {
            val arc = Arc2D.Float(
                (cx - radius).toFloat(),
                (cy - radius).toFloat(),
                (radius * 2).toFloat(),
                (radius * 2).toFloat(),
                Math.toDegrees(startAngle).toFloat(),
                sweep.toFloat(),
                Arc2D.OPEN,
            )
            g2.paint = Color(0, 188, 212, 150)
            g2.stroke = BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g2.draw(arc)
        }

        private fun drawTitle(g2: Graphics2D) {
            g2.font = Font("Segoe UI", Font.BOLD, 36)
            val metrics = g2.fontMetrics
            val full = "SakayoriMusic"
            val sakayori = "Sakayori"
            val music = "Music"
            val totalWidth = metrics.stringWidth(full)
            val sakayoriWidth = metrics.stringWidth(sakayori)
            val startX = (width - totalWidth) / 2
            val baseY = 240

            g2.paint = textPrimary
            g2.drawString(sakayori, startX, baseY)

            g2.paint = accent
            g2.drawString(music, startX + sakayoriWidth, baseY)
        }

        private fun drawTagline(g2: Graphics2D) {
            g2.font = Font("Segoe UI", Font.ITALIC, 13)
            g2.paint = textSoft
            val metrics = g2.fontMetrics
            val textWidth = metrics.stringWidth(tagline)
            g2.drawString(tagline, (width - textWidth) / 2, 264)
        }

        private fun drawEqualizer(g2: Graphics2D) {
            val barWidth = 3
            val gap = 5
            val centerY = 295
            val totalWidth = (barWidth + gap) * EQ_BARS - gap
            val startX = (width - totalWidth) / 2

            for (i in 0 until EQ_BARS) {
                val h = (equalizerBars[i] * 22f).coerceAtLeast(3f)
                val x = startX + i * (barWidth + gap)
                val y = centerY - h / 2f
                val intensity = (180 * equalizerBars[i] + 75).toInt().coerceIn(75, 255)
                g2.paint = Color(0, 188, 212, intensity)
                g2.fill(
                    RoundRectangle2D.Float(
                        x.toFloat(), y, barWidth.toFloat(), h, barWidth.toFloat(), barWidth.toFloat(),
                    ),
                )
            }
        }

        private fun drawProgress(g2: Graphics2D) {
            val barY = 332
            val barHeight = 3
            val barInset = 80
            val barWidth = width - (barInset * 2)

            g2.paint = Color(28, 30, 36)
            g2.fill(
                RoundRectangle2D.Float(
                    barInset.toFloat(), barY.toFloat(),
                    barWidth.toFloat(), barHeight.toFloat(),
                    barHeight.toFloat(), barHeight.toFloat(),
                ),
            )

            if (progress > 0f) {
                val filledWidth = barWidth * progress
                g2.paint = GradientPaint(
                    barInset.toFloat(), 0f, accent,
                    barInset + filledWidth, 0f, accentBright,
                )
                g2.fill(
                    RoundRectangle2D.Float(
                        barInset.toFloat(), barY.toFloat(),
                        filledWidth, barHeight.toFloat(),
                        barHeight.toFloat(), barHeight.toFloat(),
                    ),
                )

                g2.paint = Color(0, 188, 212, 80)
                g2.fill(
                    Ellipse2D.Float(
                        barInset + filledWidth - 6f, barY - 2f, 8f, 8f,
                    ),
                )
            }
        }

        private fun drawStatus(g2: Graphics2D) {
            g2.font = pickMonoFont(11)
            g2.paint = textFaint

            val dots = when (dotPhase % 4) {
                0 -> "   "
                1 -> "●  "
                2 -> "● ●"
                else -> "● ● ●"
            }
            val text = "${status.uppercase()}  $dots"
            g2.drawString(text, 80, 360)
        }

        private fun drawVersionPill(g2: Graphics2D) {
            g2.font = pickMonoFont(10)
            val metrics = g2.fontMetrics
            val pillW = metrics.stringWidth(versionText) + 18
            val pillH = 18
            val px = width - pillW - 24
            val py = 22

            g2.paint = accentVerySoft
            g2.fill(
                RoundRectangle2D.Float(
                    px.toFloat(), py.toFloat(),
                    pillW.toFloat(), pillH.toFloat(),
                    pillH.toFloat(), pillH.toFloat(),
                ),
            )
            g2.paint = accentSoft
            g2.stroke = BasicStroke(0.8f)
            g2.draw(
                RoundRectangle2D.Float(
                    px.toFloat(), py.toFloat(),
                    pillW.toFloat(), pillH.toFloat(),
                    pillH.toFloat(), pillH.toFloat(),
                ),
            )
            g2.paint = accent
            g2.drawString(versionText, px + 9, py + 13)
        }

        private fun drawBorder(g2: Graphics2D, arc: Float) {
            g2.paint = borderColor
            g2.stroke = BasicStroke(1f)
            g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, width - 1f, height - 1f, arc, arc))
        }

        private fun pickMonoFont(size: Int): Font {
            val candidates = listOf("JetBrains Mono", "Cascadia Mono", "Consolas", "Menlo", "Monaco", "monospaced")
            for (name in candidates) {
                val f = Font(name, Font.PLAIN, size)
                if (f.family.equals(name, ignoreCase = true)) return f
            }
            return Font(Font.MONOSPACED, Font.PLAIN, size)
        }

        companion object {
            const val WIDTH_DP = 600
            const val HEIGHT_DP = 380
            const val EQ_BARS = 24
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
                preferredSize = Dimension(ContentPanel.WIDTH_DP, ContentPanel.HEIGHT_DP)
                isResizable = false
                defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
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
                val next = (f.opacity + 0.06f).coerceAtMost(1f)
                f.opacity = next
                if (next >= 1f) {
                    (e.source as Timer).stop()
                }
            }.start()

            spinTimer = Timer(16) {
                val p = contentPanel ?: return@Timer
                p.ringRotation += Math.toRadians(0.9)
                p.repaint()
            }
            spinTimer?.start()

            pulseTimer = Timer(33) {
                val p = contentPanel ?: return@Timer
                p.pulsePhase += 0.05f
                if (p.pulsePhase > Math.PI.toFloat() * 2) p.pulsePhase -= Math.PI.toFloat() * 2
            }
            pulseTimer?.start()

            dotTimer = Timer(420) {
                val p = contentPanel ?: return@Timer
                p.dotPhase = (p.dotPhase + 1) % 4
            }
            dotTimer?.start()

            val rng = java.util.Random()
            equalizerTimer = Timer(90) {
                val p = contentPanel ?: return@Timer
                for (i in p.equalizerBars.indices) {
                    val target = 0.25f + rng.nextFloat() * 0.75f
                    p.equalizerBars[i] = p.equalizerBars[i] * 0.55f + target * 0.45f
                }
            }
            equalizerTimer?.start()
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
                currentProgress = (currentProgress + 0.012f).coerceAtMost(targetProgress)
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
                val next = (f.opacity - 0.08f).coerceAtLeast(0f)
                f.opacity = next
                if (next <= 0f) {
                    (e.source as Timer).stop()
                    spinTimer?.stop()
                    pulseTimer?.stop()
                    dotTimer?.stop()
                    equalizerTimer?.stop()
                    progressTimer?.stop()
                    spinTimer = null
                    pulseTimer = null
                    dotTimer = null
                    equalizerTimer = null
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
