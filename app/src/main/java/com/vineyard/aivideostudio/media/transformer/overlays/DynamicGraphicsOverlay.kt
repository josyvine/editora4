package com.vineyard.aivideostudio.media.transformer.overlays

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Base64
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import com.vineyard.aivideostudio.core.model.effects.ArrowDirection
import com.vineyard.aivideostudio.core.model.effects.OverlayType
import com.vineyard.aivideostudio.core.model.effects.ReplacementOverlaySpec
import com.vineyard.aivideostudio.core.model.effects.TrackingIndicatorSpec
import com.vineyard.aivideostudio.core.model.effects.TrackingKeyframe
import com.vineyard.aivideostudio.core.model.effects.TrackingStyle
import java.io.File
import kotlin.math.sin

/**
 * Universal High-performance Media3 BitmapOverlay engine:
 * Supports any aspect ratio (9:16, 20:9, 16:9, 1:1) and any video resolution (720p, 1080p, 4K)
 * without stretching, drifting, or coordinate distortion.
 */
@OptIn(UnstableApi::class)
class DynamicGraphicsOverlay(
    private val replacements: List<ReplacementOverlaySpec> = emptyList(),
    private val trackingIndicators: List<TrackingIndicatorSpec> = emptyList(),
    private val targetWidth: Int = 1080,
    private val targetHeight: Int = 1920
) : BitmapOverlay() {

    private val safeWidth = targetWidth.coerceAtLeast(1)
    private val safeHeight = targetHeight.coerceAtLeast(1)

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val arrowPath = Path()
    private val textBounds = Rect()

    // Pre-allocated reusable canvas buffer matched 1:1 to video resolution
    private val frameBitmap: Bitmap = Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888)
    private val canvas: Canvas = Canvas(frameBitmap)

    // Decoded bitmap cache for logo / image replacements
    private val bitmapCache = mutableMapOf<String, Bitmap>()

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val currentTimeMs = presentationTimeUs / 1000L
        frameBitmap.eraseColor(Color.TRANSPARENT)

        val width = safeWidth.toFloat()
        val height = safeHeight.toFloat()

        // Universal UI scaling factor (normalizes stroke and text size across 720p, 1080p, 4K)
        val scaleFactor = (minOf(width, height) / 1080f).coerceIn(0.5f, 4.0f)

        // 1. Render 1:1 Brand / Watermark Replacements & Emojis
        renderReplacements(canvas, currentTimeMs, width, height)

        // 2. Render Pointing Arrows, Button Highlights, Tracking Pillars & Spotlights
        renderTrackingIndicators(canvas, currentTimeMs, width, height, scaleFactor)

        return frameBitmap
    }

    private fun renderReplacements(
        canvas: Canvas,
        currentTimeMs: Long,
        canvasWidth: Float,
        canvasHeight: Float
    ) {
        val activeReplacements = replacements.filter {
            currentTimeMs in it.startTimeMs..it.endTimeMs
        }

        for (spec in activeReplacements) {
            val left = spec.bounds.left.coerceIn(0f, 1f) * canvasWidth
            val top = spec.bounds.top.coerceIn(0f, 1f) * canvasHeight
            val right = spec.bounds.right.coerceIn(0f, 1f) * canvasWidth
            val bottom = spec.bounds.bottom.coerceIn(0f, 1f) * canvasHeight
            val rect = RectF(minOf(left, right), minOf(top, bottom), maxOf(left, right), maxOf(top, bottom))

            canvas.save()
            if (spec.rotationDegrees != 0f) {
                canvas.rotate(spec.rotationDegrees, rect.centerX(), rect.centerY())
            }

            when (spec.type) {
                OverlayType.SOLID_BADGE -> {
                    fillPaint.color = try {
                        Color.parseColor(spec.contentValue)
                    } catch (e: Exception) {
                        Color.BLACK
                    }
                    fillPaint.alpha = (spec.opacity * 255).toInt().coerceIn(0, 255)
                    val cornerRadius = (rect.height() * 0.2f).coerceAtMost(16f)
                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)
                }

                OverlayType.EMOJI -> {
                    // Opaque concealment background pill
                    fillPaint.color = Color.BLACK
                    fillPaint.alpha = 230
                    val cornerRadius = (rect.height() * 0.25f).coerceAtMost(20f)
                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)

                    // Draw Emoji centered inside bounds
                    textPaint.textSize = rect.height() * 0.75f
                    textPaint.alpha = (spec.opacity * 255).toInt().coerceIn(0, 255)
                    textPaint.getTextBounds(spec.contentValue, 0, spec.contentValue.length, textBounds)
                    val textY = rect.centerY() + (textBounds.height() / 2f) - textBounds.bottom
                    canvas.drawText(spec.contentValue, rect.centerX(), textY, textPaint)
                }

                OverlayType.BRAND_LOGO, OverlayType.STICKER -> {
                    val bitmap = getOrLoadBitmap(spec.contentValue)
                    if (bitmap != null) {
                        fillPaint.alpha = (spec.opacity * 255).toInt().coerceIn(0, 255)
                        canvas.drawBitmap(bitmap, null, rect, fillPaint)
                    } else {
                        fillPaint.color = Color.DKGRAY
                        canvas.drawRoundRect(rect, 8f, 8f, fillPaint)
                    }
                }
            }

            canvas.restore()
        }
    }

    private fun renderTrackingIndicators(
        canvas: Canvas,
        currentTimeMs: Long,
        canvasWidth: Float,
        canvasHeight: Float,
        scaleFactor: Float
    ) {
        for (indicator in trackingIndicators) {
            // Check active time window
            val isWithinWindow = currentTimeMs in indicator.startTimeMs..indicator.endTimeMs
            if (!isWithinWindow) continue

            // Evaluate trackingMode: "static", "keyframes", or "auto"
            val mode = indicator.trackingMode.lowercase()
            val preferKeyframes = mode == "keyframes" || (mode == "auto" && indicator.keyframes.isNotEmpty())

            val rect: RectF = if (preferKeyframes && indicator.keyframes.isNotEmpty()) {
                val currentFrame = interpolateKeyframe(indicator.keyframes, currentTimeMs)
                if (currentFrame != null) {
                    val cx = currentFrame.x.coerceIn(0f, 1f) * canvasWidth
                    val cy = currentFrame.y.coerceIn(0f, 1f) * canvasHeight
                    val bw = currentFrame.width.coerceAtLeast(0.01f) * canvasWidth
                    val bh = currentFrame.height.coerceAtLeast(0.01f) * canvasHeight
                    RectF(cx - (bw / 2f), cy - (bh / 2f), cx + (bw / 2f), cy + (bh / 2f))
                } else if (indicator.staticBounds != null) {
                    val l = minOf(indicator.staticBounds.left, indicator.staticBounds.right).coerceIn(0f, 1f) * canvasWidth
                    val r = maxOf(indicator.staticBounds.left, indicator.staticBounds.right).coerceIn(0f, 1f) * canvasWidth
                    val t = minOf(indicator.staticBounds.top, indicator.staticBounds.bottom).coerceIn(0f, 1f) * canvasHeight
                    val b = maxOf(indicator.staticBounds.top, indicator.staticBounds.bottom).coerceIn(0f, 1f) * canvasHeight
                    RectF(l, t, r, b)
                } else {
                    continue
                }
            } else if (indicator.staticBounds != null) {
                // Static Bounds Lock (Universal aspect ratio scaling)
                val l = minOf(indicator.staticBounds.left, indicator.staticBounds.right).coerceIn(0f, 1f) * canvasWidth
                val r = maxOf(indicator.staticBounds.left, indicator.staticBounds.right).coerceIn(0f, 1f) * canvasWidth
                val t = minOf(indicator.staticBounds.top, indicator.staticBounds.bottom).coerceIn(0f, 1f) * canvasHeight
                val b = maxOf(indicator.staticBounds.top, indicator.staticBounds.bottom).coerceIn(0f, 1f) * canvasHeight
                RectF(l, t, r, b)
            } else if (indicator.keyframes.isNotEmpty()) {
                val currentFrame = interpolateKeyframe(indicator.keyframes, currentTimeMs) ?: continue
                val cx = currentFrame.x.coerceIn(0f, 1f) * canvasWidth
                val cy = currentFrame.y.coerceIn(0f, 1f) * canvasHeight
                val bw = currentFrame.width.coerceAtLeast(0.01f) * canvasWidth
                val bh = currentFrame.height.coerceAtLeast(0.01f) * canvasHeight
                RectF(cx - (bw / 2f), cy - (bh / 2f), cx + (bw / 2f), cy + (bh / 2f))
            } else {
                continue
            }

            // 1. Spotlight Dimming
            if (indicator.dimBackgroundOpacity > 0f) {
                renderBackgroundDimming(canvas, rect, canvasWidth, canvasHeight, indicator.dimBackgroundOpacity)
            }

            val baseColor = try {
                Color.parseColor(indicator.colorHex)
            } catch (e: Exception) {
                Color.RED
            }

            when (indicator.style) {
                TrackingStyle.BUTTON_HIGHLIGHT -> {
                    // Pulsating highlight box for UI buttons and icons
                    val pulse = (sin(currentTimeMs * 0.010) * 0.5 + 0.5).toFloat()
                    val strokeW = (indicator.strokeWidthPx * scaleFactor) + (pulse * 2.5f * scaleFactor)

                    strokePaint.color = baseColor
                    strokePaint.strokeWidth = strokeW
                    strokePaint.alpha = (180 + (pulse * 75)).toInt().coerceIn(0, 255)

                    val pad = (8f * scaleFactor) + (pulse * 4f * scaleFactor)
                    val highlightRect = RectF(
                        (rect.left - pad).coerceAtLeast(0f),
                        (rect.top - pad).coerceAtLeast(0f),
                        (rect.right + pad).coerceAtMost(canvasWidth),
                        (rect.bottom + pad).coerceAtMost(canvasHeight)
                    )

                    // Subtle pulsating translucent fill to make target area unmistakable
                    fillPaint.color = baseColor
                    fillPaint.alpha = (25 + (pulse * 30)).toInt()
                    val cornerRadius = 8f * scaleFactor
                    canvas.drawRoundRect(highlightRect, cornerRadius, cornerRadius, fillPaint)

                    // High-visibility rounded stroke & corner brackets
                    canvas.drawRoundRect(highlightRect, cornerRadius, cornerRadius, strokePaint)
                    drawCornerBrackets(canvas, highlightRect, strokePaint)

                    indicator.label?.let { label ->
                        drawLabelBadge(canvas, label, highlightRect.centerX(), highlightRect.top - (8f * scaleFactor), scaleFactor)
                    }
                }

                TrackingStyle.VERTICAL_COLUMN -> {
                    strokePaint.color = baseColor
                    strokePaint.strokeWidth = indicator.strokeWidthPx * scaleFactor
                    val pillarRect = RectF(rect.left, 40f * scaleFactor, rect.right, canvasHeight - (40f * scaleFactor))
                    val cornerRadius = 24f * scaleFactor
                    canvas.drawRoundRect(pillarRect, cornerRadius, cornerRadius, strokePaint)

                    indicator.label?.let { label ->
                        drawLabelBadge(canvas, label, pillarRect.centerX(), pillarRect.top + (60f * scaleFactor), scaleFactor)
                    }
                }

                TrackingStyle.FLASHING_ARROW -> {
                    renderDirectionalArrow(canvas, rect, indicator.arrowDirection, baseColor, currentTimeMs, scaleFactor, canvasWidth, canvasHeight)
                }

                TrackingStyle.RED_BOX -> {
                    strokePaint.color = baseColor
                    strokePaint.strokeWidth = indicator.strokeWidthPx * scaleFactor
                    val cornerRadius = 12f * scaleFactor
                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint)

                    indicator.label?.let { label ->
                        drawLabelBadge(canvas, label, rect.centerX(), rect.top - (10f * scaleFactor), scaleFactor)
                    }
                }

                TrackingStyle.HIGHLIGHT_CIRCLE -> {
                    strokePaint.color = baseColor
                    strokePaint.strokeWidth = indicator.strokeWidthPx * scaleFactor
                    val radius = (rect.width().coerceAtLeast(rect.height()) / 2f) + (6f * scaleFactor)
                    canvas.drawCircle(rect.centerX(), rect.centerY(), radius, strokePaint)
                }

                TrackingStyle.SPOTLIGHT -> {
                    fillPaint.color = baseColor
                    fillPaint.alpha = 50
                    canvas.drawOval(rect, fillPaint)
                }
            }
        }
    }

    private fun renderDirectionalArrow(
        canvas: Canvas,
        targetRect: RectF,
        direction: ArrowDirection,
        color: Int,
        currentTimeMs: Long,
        scaleFactor: Float,
        canvasWidth: Float,
        canvasHeight: Float
    ) {
        val pulse = (sin(currentTimeMs * 0.012) * 0.5 + 0.5).toFloat()
        val bounceOffset = pulse * 10f * scaleFactor

        fillPaint.color = color
        fillPaint.alpha = (210 + (pulse * 45)).toInt().coerceIn(0, 255)

        val arrowWidth = 32f * scaleFactor
        val arrowLength = 40f * scaleFactor
        arrowPath.reset()

        when (direction) {
            ArrowDirection.DOWN -> {
                // Pin arrow tip accurately to the top boundary of the targeted object
                val tipX = targetRect.centerX().coerceIn(arrowWidth, canvasWidth - arrowWidth)
                val tipY = (targetRect.top - (4f * scaleFactor) + bounceOffset).coerceIn(arrowLength + 4f, canvasHeight - 4f)
                arrowPath.moveTo(tipX, tipY)
                arrowPath.lineTo(tipX - (arrowWidth / 2f), tipY - arrowLength)
                arrowPath.lineTo(tipX + (arrowWidth / 2f), tipY - arrowLength)
            }
            ArrowDirection.UP -> {
                val tipX = targetRect.centerX().coerceIn(arrowWidth, canvasWidth - arrowWidth)
                val tipY = (targetRect.bottom + (4f * scaleFactor) - bounceOffset).coerceIn(4f, canvasHeight - arrowLength - 4f)
                arrowPath.moveTo(tipX, tipY)
                arrowPath.lineTo(tipX - (arrowWidth / 2f), tipY + arrowLength)
                arrowPath.lineTo(tipX + (arrowWidth / 2f), tipY + arrowLength)
            }
            ArrowDirection.RIGHT -> {
                val tipX = (targetRect.left - (4f * scaleFactor) + bounceOffset).coerceIn(arrowLength + 4f, canvasWidth - 4f)
                val tipY = targetRect.centerY().coerceIn(arrowWidth, canvasHeight - arrowWidth)
                arrowPath.moveTo(tipX, tipY)
                arrowPath.lineTo(tipX - arrowLength, tipY - (arrowWidth / 2f))
                arrowPath.lineTo(tipX - arrowLength, tipY + (arrowWidth / 2f))
            }
            ArrowDirection.LEFT -> {
                val tipX = (targetRect.right + (4f * scaleFactor) - bounceOffset).coerceIn(4f, canvasWidth - arrowLength - 4f)
                val tipY = targetRect.centerY().coerceIn(arrowWidth, canvasHeight - arrowWidth)
                arrowPath.moveTo(tipX, tipY)
                arrowPath.lineTo(tipX + arrowLength, tipY - (arrowWidth / 2f))
                arrowPath.lineTo(tipX + arrowLength, tipY + (arrowWidth / 2f))
            }
        }

        arrowPath.close()

        // Crisp dark outline so arrow remains sharp against any background
        strokePaint.color = Color.BLACK
        strokePaint.strokeWidth = 3.5f * scaleFactor
        strokePaint.alpha = 200
        canvas.drawPath(arrowPath, strokePaint)

        // Arrow Fill
        canvas.drawPath(arrowPath, fillPaint)
    }

    private fun drawCornerBrackets(canvas: Canvas, r: RectF, paint: Paint) {
        val len = minOf(r.width(), r.height()) * 0.28f

        // Top-Left
        canvas.drawLine(r.left, r.top, r.left + len, r.top, paint)
        canvas.drawLine(r.left, r.top, r.left, r.top + len, paint)

        // Top-Right
        canvas.drawLine(r.right, r.top, r.right - len, r.top, paint)
        canvas.drawLine(r.right, r.top, r.right, r.top + len, paint)

        // Bottom-Left
        canvas.drawLine(r.left, r.bottom, r.left + len, r.bottom, paint)
        canvas.drawLine(r.left, r.bottom, r.left, r.bottom - len, paint)

        // Bottom-Right
        canvas.drawLine(r.right, r.bottom, r.right - len, r.bottom, paint)
        canvas.drawLine(r.right, r.bottom, r.right, r.bottom - len, paint)
    }

    private fun renderBackgroundDimming(
        canvas: Canvas,
        cutoutRect: RectF,
        canvasWidth: Float,
        canvasHeight: Float,
        dimOpacity: Float
    ) {
        fillPaint.color = Color.BLACK
        fillPaint.alpha = (dimOpacity.coerceIn(0.0f, 1.0f) * 255).toInt()

        // Draw 4 rectangles around the cutout region
        canvas.drawRect(0f, 0f, canvasWidth, cutoutRect.top, fillPaint)
        canvas.drawRect(0f, cutoutRect.bottom, canvasWidth, canvasHeight, fillPaint)
        canvas.drawRect(0f, cutoutRect.top, cutoutRect.left, cutoutRect.bottom, fillPaint)
        canvas.drawRect(cutoutRect.right, cutoutRect.top, canvasWidth, cutoutRect.bottom, fillPaint)
    }

    private fun drawLabelBadge(canvas: Canvas, label: String, centerX: Float, bottomY: Float, scaleFactor: Float) {
        textPaint.textSize = 28f * scaleFactor
        textPaint.color = Color.WHITE
        textPaint.getTextBounds(label, 0, label.length, textBounds)

        val paddingHorizontal = 16f * scaleFactor
        val paddingVertical = 8f * scaleFactor
        val totalBadgeHeight = textBounds.height() + (paddingVertical * 2)

        val resolvedBottomY = if (bottomY - totalBadgeHeight < (8f * scaleFactor)) {
            bottomY + totalBadgeHeight + (32f * scaleFactor)
        } else {
            bottomY
        }

        val badgeRect = RectF(
            centerX - (textBounds.width() / 2f) - paddingHorizontal,
            resolvedBottomY - totalBadgeHeight,
            centerX + (textBounds.width() / 2f) + paddingHorizontal,
            resolvedBottomY
        )

        fillPaint.color = Color.parseColor("#CC000000")
        canvas.drawRoundRect(badgeRect, 8f * scaleFactor, 8f * scaleFactor, fillPaint)

        val textY = badgeRect.centerY() + (textBounds.height() / 2f) - textBounds.bottom
        canvas.drawText(label, centerX, textY, textPaint)
    }

    private fun interpolateKeyframe(keyframes: List<TrackingKeyframe>, currentTimeMs: Long): TrackingKeyframe? {
        if (keyframes.isEmpty()) return null

        // CLAMP: Prevent blinking/vanishing when timeline reaches boundary keyframes
        if (currentTimeMs <= keyframes.first().timeMs) return keyframes.first()
        if (currentTimeMs >= keyframes.last().timeMs) return keyframes.last()

        val nextIndex = keyframes.indexOfFirst { it.timeMs >= currentTimeMs }
        if (nextIndex <= 0) return keyframes.first()

        val prev = keyframes[nextIndex - 1]
        val next = keyframes[nextIndex]

        val totalDuration = (next.timeMs - prev.timeMs).toFloat()
        if (totalDuration <= 0f) return prev

        val progress = ((currentTimeMs - prev.timeMs) / totalDuration).coerceIn(0f, 1f)

        return TrackingKeyframe(
            timeMs = currentTimeMs,
            x = prev.x + (next.x - prev.x) * progress,
            y = prev.y + (next.y - prev.y) * progress,
            width = prev.width + (next.width - prev.width) * progress,
            height = prev.height + (next.height - prev.height) * progress
        )
    }

    private fun getOrLoadBitmap(pathOrBase64: String): Bitmap? {
        if (bitmapCache.containsKey(pathOrBase64)) {
            return bitmapCache[pathOrBase64]
        }

        val bitmap = try {
            if (pathOrBase64.startsWith("/") || pathOrBase64.startsWith("file://")) {
                val cleanPath = pathOrBase64.removePrefix("file://")
                val file = File(cleanPath)
                if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
            } else {
                val decodedBytes = Base64.decode(pathOrBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            }
        } catch (e: Exception) {
            null
        }

        if (bitmap != null) {
            bitmapCache[pathOrBase64] = bitmap
        }
        return bitmap
    }
}