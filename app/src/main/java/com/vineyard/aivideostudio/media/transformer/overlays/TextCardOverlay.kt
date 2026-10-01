package com.vineyard.aivideostudio.media.transformer.overlays

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import com.vineyard.aivideostudio.core.model.effects.CardLayout
import com.vineyard.aivideostudio.core.model.effects.TextCardSpec

/**
 * Dedicated Media3 BitmapOverlay engine that renders full-screen instruction slates,
 * summary conclusion boards, and floating modal text cards directly into video frames.
 */
@OptIn(UnstableApi::class)
class TextCardOverlay(
    private val textCards: List<TextCardSpec> = emptyList(),
    private val targetWidth: Int = 1080,
    private val targetHeight: Int = 1920
) : BitmapOverlay() {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private val frameBitmap: Bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    private val canvas: Canvas = Canvas(frameBitmap)
    private val textBounds = Rect()

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val currentTimeMs = presentationTimeUs / 1000L
        frameBitmap.eraseColor(Color.TRANSPARENT)

        val activeCard = textCards.firstOrNull { currentTimeMs in it.startTimeMs..it.endTimeMs }
            ?: return frameBitmap

        renderCard(canvas, activeCard)
        return frameBitmap
    }

    private fun renderCard(canvas: Canvas, card: TextCardSpec) {
        val w = targetWidth.toFloat()
        val h = targetHeight.toFloat()

        when (card.layout) {
            CardLayout.FULL_SCREEN_SLATE -> {
                // 1. Draw solid or deep-slate background plate
                bgPaint.color = parseHexColor(card.backgroundColorHex, "#101216")
                bgPaint.alpha = (card.backgroundOpacity.coerceIn(0.0f, 1.0f) * 255).toInt()
                canvas.drawRect(0f, 0f, w, h, bgPaint)

                // 2. Draw subtle border frame
                borderPaint.color = parseHexColor(card.accentColorHex, "#3A4454")
                borderPaint.alpha = (card.backgroundOpacity.coerceIn(0.0f, 1.0f) * 180).toInt()
                val inset = 48f
                canvas.drawRoundRect(RectF(inset, inset, w - inset, h - inset), 32f, 32f, borderPaint)

                // 3. Render Title & Body Text
                val contentLeft = inset + 64f
                val contentRight = w - inset - 64f
                val contentWidth = contentRight - contentLeft
                var cursorY = h * 0.28f

                // Draw Card Tag / Category (e.g. "INSTRUCTIONS", "SUMMARY")
                if (!card.tag.isNullOrBlank()) {
                    titlePaint.textSize = 34f
                    titlePaint.color = parseHexColor(card.accentColorHex, "#4E9FFF")
                    titlePaint.letterSpacing = 0.15f
                    canvas.drawText(card.tag.uppercase(), w / 2f, cursorY, titlePaint)
                    cursorY += 60f
                }

                // Draw Main Title
                titlePaint.textSize = if (card.titleFontSizeSp > 0) card.titleFontSizeSp * (w / 480f) else 58f
                titlePaint.color = parseHexColor(card.titleColorHex, "#FFFFFF")
                titlePaint.letterSpacing = 0.02f
                val titleLines = wrapText(card.title, titlePaint, contentWidth)
                val titleLineHeight = titlePaint.textSize * 1.25f

                for (line in titleLines) {
                    canvas.drawText(line, w / 2f, cursorY, titlePaint)
                    cursorY += titleLineHeight
                }

                // Draw Accent Divider
                cursorY += 24f
                dividerPaint.color = parseHexColor(card.accentColorHex, "#4E9FFF")
                dividerPaint.alpha = 220
                val dividerWidth = 140f
                canvas.drawLine((w / 2f) - (dividerWidth / 2f), cursorY, (w / 2f) + (dividerWidth / 2f), cursorY, dividerPaint)
                cursorY += 48f

                // Draw Multiline Body / Bullet Points
                if (card.bodyText.isNotBlank()) {
                    bodyPaint.textSize = if (card.bodyFontSizeSp > 0) card.bodyFontSizeSp * (w / 480f) else 38f
                    bodyPaint.color = parseHexColor(card.bodyColorHex, "#E2E8F0")
                    val bodyLineHeight = bodyPaint.textSize * 1.45f

                    val rawParagraphs = card.bodyText.split("\n")
                    for (paragraph in rawParagraphs) {
                        val isBullet = paragraph.trimStart().startsWith("-") || paragraph.trimStart().startsWith("•")
                        val cleanPara = if (isBullet) paragraph.trimStart().removePrefix("-").removePrefix("•").trim() else paragraph
                        val wrappedLines = wrapText(cleanPara, bodyPaint, contentWidth - if (isBullet) 48f else 0f)

                        for ((lineIdx, line) in wrappedLines.withIndex()) {
                            if (isBullet && lineIdx == 0) {
                                // Draw bullet dot
                                val bulletX = contentLeft + 12f
                                val bulletY = cursorY - (bodyPaint.textSize * 0.3f)
                                canvas.drawCircle(bulletX, bulletY, 7f, dividerPaint)
                                canvas.drawText(line, contentLeft + 48f, cursorY, bodyPaint)
                            } else {
                                val textX = if (isBullet) contentLeft + 48f else contentLeft
                                canvas.drawText(line, textX, cursorY, bodyPaint)
                            }
                            cursorY += bodyLineHeight
                        }
                        cursorY += 18f
                    }
                }
            }

            CardLayout.FLOATING_MODAL -> {
                // Floating center card with dark frosted backing
                val cardRect = RectF(w * 0.08f, h * 0.32f, w * 0.92f, h * 0.68f)

                bgPaint.color = parseHexColor(card.backgroundColorHex, "#161B22")
                bgPaint.alpha = (card.backgroundOpacity.coerceIn(0.0f, 1.0f) * 245).toInt()
                canvas.drawRoundRect(cardRect, 36f, 36f, bgPaint)

                borderPaint.color = parseHexColor(card.accentColorHex, "#388BFD")
                borderPaint.alpha = 255
                canvas.drawRoundRect(cardRect, 36f, 36f, borderPaint)

                var cursorY = cardRect.top + 80f
                val contentWidth = cardRect.width() - 80f
                val contentLeft = cardRect.left + 40f

                // Title
                titlePaint.textSize = if (card.titleFontSizeSp > 0) card.titleFontSizeSp * (w / 480f) else 50f
                titlePaint.color = parseHexColor(card.titleColorHex, "#FFFFFF")
                val titleLines = wrapText(card.title, titlePaint, contentWidth)
                for (line in titleLines) {
                    canvas.drawText(line, cardRect.centerX(), cursorY, titlePaint)
                    cursorY += titlePaint.textSize * 1.25f
                }

                cursorY += 24f
                // Body
                if (card.bodyText.isNotBlank()) {
                    bodyPaint.textSize = if (card.bodyFontSizeSp > 0) card.bodyFontSizeSp * (w / 480f) else 34f
                    bodyPaint.color = parseHexColor(card.bodyColorHex, "#E2E8F0")
                    val bodyLines = wrapText(card.bodyText, bodyPaint, contentWidth)
                    for (line in bodyLines) {
                        canvas.drawText(line, contentLeft, cursorY, bodyPaint)
                        cursorY += bodyPaint.textSize * 1.4f
                    }
                }
            }
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(candidate) <= maxWidth) {
                currentLine = StringBuilder(candidate)
            } else {
                if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
        return lines.ifEmpty { listOf(text) }
    }

    private fun parseHexColor(hex: String?, fallback: String): Int {
        if (hex.isNullOrBlank()) return Color.parseColor(fallback)
        return try {
            Color.parseColor(hex)
        } catch (_: Exception) {
            Color.parseColor(fallback)
        }
    }
}