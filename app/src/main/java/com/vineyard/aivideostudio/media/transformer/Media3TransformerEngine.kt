package com.vineyard.aivideostudio.media.transformer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Crop
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import com.vineyard.aivideostudio.ai.model.HighlightSegment
import com.vineyard.aivideostudio.core.model.Caption
import com.vineyard.aivideostudio.core.model.effects.BlurSpec
import com.vineyard.aivideostudio.core.model.effects.ColorGradeSpec
import com.vineyard.aivideostudio.core.model.effects.ReplacementOverlaySpec
import com.vineyard.aivideostudio.core.model.effects.SpeedRampSpec
import com.vineyard.aivideostudio.core.model.effects.TextCardSpec
import com.vineyard.aivideostudio.core.model.effects.TrackingIndicatorSpec
import com.vineyard.aivideostudio.core.result.AppError
import com.vineyard.aivideostudio.core.result.AppResult
import com.vineyard.aivideostudio.media.transformer.effects.BlurGlEffect
import com.vineyard.aivideostudio.media.transformer.effects.ColorFilterGlEffect
import com.vineyard.aivideostudio.media.transformer.overlays.DynamicGraphicsOverlay
import com.vineyard.aivideostudio.media.transformer.overlays.TextCardOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

@OptIn(UnstableApi::class)
class Media3TransformerEngine(private val context: Context) {

    /**
     * Clips video boundaries (dead-air removal / pacing cuts).
     */
    suspend fun trimVideo(
        inputUri: Uri,
        outputFile: File,
        startMs: Long,
        endMs: Long,
        stripAudio: Boolean = false
    ): AppResult<File> = withContext(Dispatchers.Main) {
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val mediaItem = MediaItem.Builder()
            .setUri(inputUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()
            )
            .build()

        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(stripAudio)
            .build()

        runTransformer(editedMediaItem, outputFile)
    }

    /**
     * Stitches multiple highlight clips across the video timeline into one continuous highlight montage.
     */
    suspend fun spliceHighlightSegments(
        inputUri: Uri,
        outputFile: File,
        segments: List<HighlightSegment>,
        stripAudio: Boolean = false
    ): AppResult<File> = withContext(Dispatchers.Main) {
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        if (segments.isEmpty()) {
            return@withContext trimVideo(inputUri, outputFile, 0L, 60_000L, stripAudio)
        }

        val editedMediaItems = segments.map { segment ->
            val startMs = (segment.start * 1000L).toLong()
            val endMs = (segment.end * 1000L).toLong()

            val mediaItem = MediaItem.Builder()
                .setUri(inputUri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build()
                )
                .build()

            EditedMediaItem.Builder(mediaItem)
                .setRemoveAudio(stripAudio)
                .build()
        }

        val sequence = EditedMediaItemSequence(editedMediaItems)
        val composition = Composition.Builder(listOf(sequence)).build()

        runTransformer(composition, outputFile)
    }

    /**
     * Crops and reframes video using normalized coordinates.
     */
    suspend fun cropVideo(
        inputUri: Uri,
        outputFile: File,
        normalizedLeft: Float,
        normalizedRight: Float,
        normalizedBottom: Float,
        normalizedTop: Float,
        stripAudio: Boolean = false
    ): AppResult<File> = withContext(Dispatchers.Main) {
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val left = (normalizedLeft * 2f) - 1f
        val right = (normalizedRight * 2f) - 1f
        val bottom = (normalizedBottom * 2f) - 1f
        val top = (normalizedTop * 2f) - 1f

        val cropEffect = Crop(left, right, bottom, top)
        val effects = Effects(emptyList(), listOf(cropEffect))

        val mediaItem = MediaItem.fromUri(inputUri)
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(stripAudio)
            .setEffects(effects)
            .build()

        runTransformer(editedMediaItem, outputFile)
    }

    /**
     * Applies punch-in zoom scaling to the video frames.
     */
    suspend fun zoomVideo(
        inputUri: Uri,
        outputFile: File,
        scale: Float,
        stripAudio: Boolean = false
    ): AppResult<File> = withContext(Dispatchers.Main) {
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val scaleEffect = ScaleAndRotateTransformation.Builder()
            .setScale(scale, scale)
            .build()
        val effects = Effects(emptyList(), listOf(scaleEffect))

        val mediaItem = MediaItem.fromUri(inputUri)
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(stripAudio)
            .setEffects(effects)
            .build()

        runTransformer(editedMediaItem, outputFile)
    }

    /**
     * Comprehensive production export pass:
     * 1. Purges original copyrighted audio track from all video segments.
     * 2. Muxes synthesized TTS audio track as the sole soundtrack.
     * 3. Applies hardware-accelerated Speed Ramping, GPU Shaders, and dynamic Canvas Overlays.
     * 4. Renders Presentation Text Cards (Instruction & Conclusion Slates).
     */
    suspend fun exportVideo(
        inputUri: Uri,
        outputFile: File,
        commentaryAudioUri: Uri? = null,
        stripOriginalAudio: Boolean = true,
        captions: List<Caption> = emptyList(),
        targetAspectRatio: String = "ORIGINAL",
        zoomScale: Float = 1.0f,
        speedRamps: List<SpeedRampSpec> = emptyList(),
        blurSpecs: List<BlurSpec> = emptyList(),
        replacementOverlays: List<ReplacementOverlaySpec> = emptyList(),
        colorGrade: ColorGradeSpec? = null,
        trackingIndicators: List<TrackingIndicatorSpec> = emptyList(),
        textCards: List<TextCardSpec> = emptyList()
    ): AppResult<File> = withContext(Dispatchers.Main) {
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val sharedVideoEffects = mutableListOf<Effect>()

        // 1. Aspect Ratio Presentation Effect
        when (targetAspectRatio) {
            "9:16" -> sharedVideoEffects.add(Presentation.createForAspectRatio(9f / 16f, Presentation.LAYOUT_SCALE_TO_FIT))
            "16:9" -> sharedVideoEffects.add(Presentation.createForAspectRatio(16f / 9f, Presentation.LAYOUT_SCALE_TO_FIT))
            "1:1" -> sharedVideoEffects.add(Presentation.createForAspectRatio(1f, Presentation.LAYOUT_SCALE_TO_FIT))
            else -> {}
        }

        // 2. Zoom Punch-In Effect
        if (zoomScale > 1.0f) {
            sharedVideoEffects.add(
                ScaleAndRotateTransformation.Builder()
                    .setScale(zoomScale, zoomScale)
                    .build()
            )
        }

        // 3. Selective Gaussian / Mosaic Blur Shader
        if (blurSpecs.isNotEmpty()) {
            sharedVideoEffects.add(BlurGlEffect(blurSpecs))
        }

        // 4. Color Grading Shader
        if (colorGrade != null) {
            sharedVideoEffects.add(ColorFilterGlEffect(colorGrade))
        }

        // 5. Overlays (Captions, Dynamic Graphics & Presentation Text Cards)
        val overlayList = mutableListOf<TextureOverlay>()

        if (captions.isNotEmpty()) {
            overlayList.add(SubtitleBitmapOverlay(captions))
        }

        if (replacementOverlays.isNotEmpty() || trackingIndicators.isNotEmpty()) {
            overlayList.add(DynamicGraphicsOverlay(replacementOverlays, trackingIndicators))
        }

        if (textCards.isNotEmpty()) {
            overlayList.add(TextCardOverlay(textCards))
        }

        if (overlayList.isNotEmpty()) {
            sharedVideoEffects.add(OverlayEffect(ImmutableList.copyOf(overlayList)))
        }

        // Strict audio enforcement: If replacement commentary is provided, force strip original audio
        val shouldStripAudio = stripOriginalAudio || (commentaryAudioUri != null)

        // 6. Build Video Sequence
        val videoSequence = if (speedRamps.isNotEmpty()) {
            buildSpeedRampedSequence(inputUri, shouldStripAudio, speedRamps, sharedVideoEffects)
        } else {
            val videoMediaItem = MediaItem.fromUri(inputUri)
            val editedVideoItem = EditedMediaItem.Builder(videoMediaItem)
                .setRemoveAudio(shouldStripAudio)
                .setEffects(Effects(emptyList(), sharedVideoEffects))
                .build()
            EditedMediaItemSequence(editedVideoItem)
        }

        // 7. Validate & Inject Replacement TTS Commentary Audio Track
        val isCommentaryAudioValid = commentaryAudioUri != null && try {
            val path = commentaryAudioUri.path ?: ""
            val f = File(path)
            f.exists() && f.length() > 0L
        } catch (_: Exception) {
            true
        }

        val composition = if (isCommentaryAudioValid && commentaryAudioUri != null) {
            val audioMediaItem = MediaItem.fromUri(commentaryAudioUri)
            val editedAudioItem = EditedMediaItem.Builder(audioMediaItem)
                .setRemoveVideo(true)
                .build()
            val audioSequence = EditedMediaItemSequence(editedAudioItem)

            Composition.Builder(listOf(videoSequence, audioSequence)).build()
        } else {
            Composition.Builder(listOf(videoSequence)).build()
        }

        runTransformer(composition, outputFile)
    }

    /**
     * Slices the video into contiguous intervals across the timeline,
     * applying Media3 SpeedChangeEffect on accelerated sections.
     */
    private fun buildSpeedRampedSequence(
        inputUri: Uri,
        stripAudio: Boolean,
        speedRamps: List<SpeedRampSpec>,
        sharedEffects: List<Effect>
    ): EditedMediaItemSequence {
        val totalDurationMs = getVideoDurationMs(inputUri)
        val sortedRamps = speedRamps.sortedBy { it.startTimeMs }
        val editedItems = mutableListOf<EditedMediaItem>()
        var cursorMs = 0L

        for (ramp in sortedRamps) {
            val rampStart = ramp.startTimeMs.coerceIn(0L, totalDurationMs)
            val rampEnd = ramp.endTimeMs.coerceIn(rampStart, totalDurationMs)

            // Add normal speed interval (1.0x) prior to ramp
            if (rampStart > cursorMs) {
                val normalItem = createSegmentItem(inputUri, cursorMs, rampStart, 1.0f, stripAudio, sharedEffects)
                editedItems.add(normalItem)
            }

            // Add accelerated interval
            if (rampEnd > rampStart) {
                val fastItem = createSegmentItem(inputUri, rampStart, rampEnd, ramp.speedMultiplier, stripAudio, sharedEffects)
                editedItems.add(fastItem)
            }

            cursorMs = rampEnd
        }

        // Add trailing normal speed interval
        if (cursorMs < totalDurationMs) {
            val trailingItem = createSegmentItem(inputUri, cursorMs, totalDurationMs, 1.0f, stripAudio, sharedEffects)
            editedItems.add(trailingItem)
        }

        if (editedItems.isEmpty()) {
            val fallbackItem = EditedMediaItem.Builder(MediaItem.fromUri(inputUri))
                .setRemoveAudio(stripAudio)
                .setEffects(Effects(emptyList(), sharedEffects))
                .build()
            return EditedMediaItemSequence(fallbackItem)
        }

        return EditedMediaItemSequence(editedItems)
    }

    private fun createSegmentItem(
        uri: Uri,
        startMs: Long,
        endMs: Long,
        speedMultiplier: Float,
        stripAudio: Boolean,
        sharedEffects: List<Effect>
    ): EditedMediaItem {
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()
            )
            .build()

        val segmentEffects = mutableListOf<Effect>()
        if (speedMultiplier != 1.0f && speedMultiplier > 0.0f) {
            segmentEffects.add(SpeedChangeEffect(speedMultiplier))
        }
        segmentEffects.addAll(sharedEffects)

        return EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(stripAudio)
            .setEffects(Effects(emptyList(), segmentEffects))
            .build()
    }

    private fun getVideoDurationMs(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull() ?: 60_000L
        } catch (_: Exception) {
            60_000L
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private suspend fun runTransformer(
        editedMediaItem: EditedMediaItem,
        outputFile: File
    ): AppResult<File> {
        val sequence = EditedMediaItemSequence(editedMediaItem)
        val composition = Composition.Builder(listOf(sequence)).build()
        return runTransformer(composition, outputFile)
    }

    private suspend fun runTransformer(
        composition: Composition,
        outputFile: File
    ): AppResult<File> = suspendCancellableCoroutine { continuation ->
        var activeTransformer: Transformer? = null

        val listener = object : Transformer.Listener {
            override fun onCompleted(comp: Composition, exportResult: ExportResult) {
                if (continuation.isActive) {
                    if (outputFile.exists() && outputFile.length() > 0L) {
                        continuation.resume(AppResult.Success(outputFile))
                    } else {
                        continuation.resume(
                            AppResult.Error(AppError.MediaProcessingError("Transformer finished but output file is empty (0 bytes)."))
                        )
                    }
                }
            }

            override fun onError(
                comp: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                if (continuation.isActive) {
                    val errorCode = exportException.errorCode
                    val errorCodeName = exportException.errorCodeName
                    val underlyingCause = exportException.cause?.message ?: "None"
                    val fullStackTrace = exportException.stackTraceToString()

                    val deepDiagnosticMessage = "Media3 Export Failed [$errorCodeName / Code: $errorCode]: " +
                            "${exportException.message} | Cause: $underlyingCause\nStackTrace: $fullStackTrace"

                    continuation.resume(
                        AppResult.Error(
                            AppError.MediaProcessingError(
                                message = deepDiagnosticMessage,
                                cause = exportException
                            )
                        )
                    )
                }
            }
        }

        try {
            activeTransformer = Transformer.Builder(context)
                .addListener(listener)
                .build()

            activeTransformer.start(composition, outputFile.absolutePath)
        } catch (e: Exception) {
            if (continuation.isActive) {
                val fullStackTrace = e.stackTraceToString()
                continuation.resume(
                    AppResult.Error(
                        AppError.MediaProcessingError(
                            message = "Failed starting Media3 Transformer: ${e.message}\nStackTrace: $fullStackTrace",
                            cause = e
                        )
                    )
                )
            }
        }

        continuation.invokeOnCancellation {
            try {
                activeTransformer?.cancel()
            } catch (_: Exception) {}
        }
    }

    /**
     * Dynamic BitmapOverlay that renders synchronized multiline subtitles while seamlessly concealing
     * original hardcoded subtitles located in the lower-third zone.
     */
    private class SubtitleBitmapOverlay(
        private val captions: List<Caption>,
        private val targetWidth: Int = 1080,
        private val targetHeight: Int = 1920
    ) : BitmapOverlay() {

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            style = Paint.Style.STROKE
            strokeWidth = 10f
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        private val backgroundPillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        private val frameBitmap: Bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        private val canvas: Canvas = Canvas(frameBitmap)

        override fun getBitmap(presentationTimeUs: Long): Bitmap {
            val currentSec = presentationTimeUs / 1_000_000.0
            frameBitmap.eraseColor(Color.TRANSPARENT)

            val activeCaption = captions.firstOrNull { currentSec >= it.start && currentSec <= it.end }
            if (activeCaption != null && activeCaption.text.isNotBlank()) {
                val posX = if (activeCaption.x > 1.0f) activeCaption.x / 100f else activeCaption.x

                val rawY = if (activeCaption.y > 1.0f) activeCaption.y / 100f else activeCaption.y
                val targetY = if (rawY in 0.75f..0.96f) 0.90f else rawY

                val x = targetWidth * posX.coerceIn(0.05f, 0.95f)
                val y = targetHeight * targetY.coerceIn(0.1f, 0.95f)

                val scaledFontSize = if (activeCaption.fontSizeSp > 0f) {
                    activeCaption.fontSizeSp * (targetWidth / 480f)
                } else {
                    54f
                }

                textPaint.textSize = scaledFontSize
                strokePaint.textSize = scaledFontSize

                val fontHex = activeCaption.fontColorHex
                if (!fontHex.isNullOrBlank()) {
                    try {
                        textPaint.color = Color.parseColor(fontHex)
                    } catch (_: Exception) {
                        textPaint.color = Color.WHITE
                    }
                } else {
                    textPaint.color = Color.WHITE
                }

                val maxTextWidth = targetWidth * 0.76f
                val words = activeCaption.text.split(" ")
                val lines = mutableListOf<String>()
                var currentLine = StringBuilder()

                for (word in words) {
                    val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                    if (textPaint.measureText(testLine) <= maxTextWidth) {
                        currentLine = StringBuilder(testLine)
                    } else {
                        if (currentLine.isNotEmpty()) {
                            lines.add(currentLine.toString())
                        }
                        currentLine = StringBuilder(word)
                    }
                }
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                }
                if (lines.isEmpty()) {
                    lines.add(activeCaption.text)
                }

                val lineHeight = scaledFontSize * 1.25f
                val totalTextHeight = lines.size * lineHeight
                val longestLineWidth = lines.maxOfOrNull { textPaint.measureText(it) } ?: maxTextWidth

                val padH = 36f
                val padV = 20f
                val minConcealerWidth = targetWidth * 0.82f
                val maskWidth = maxOf(longestLineWidth + (padH * 2), minConcealerWidth)

                val pillRect = RectF(
                    x - (maskWidth / 2f),
                    y - (totalTextHeight / 2f) - padV,
                    x + (maskWidth / 2f),
                    y + (totalTextHeight / 2f) + padV
                )

                val bgHex = activeCaption.backgroundColorHex
                backgroundPillPaint.color = try {
                    if (!bgHex.isNullOrBlank() && bgHex != "#00000000") {
                        Color.parseColor(bgHex)
                    } else {
                        Color.BLACK
                    }
                } catch (_: Exception) {
                    Color.BLACK
                }
                backgroundPillPaint.alpha = 255

                canvas.drawRoundRect(pillRect, 22f, 22f, backgroundPillPaint)

                val startY = y - (totalTextHeight / 2f) + scaledFontSize * 0.85f
                for ((lineIdx, lineText) in lines.withIndex()) {
                    val lineY = startY + (lineIdx * lineHeight)
                    canvas.drawText(lineText, x, lineY, strokePaint)
                    canvas.drawText(lineText, x, lineY, textPaint)
                }
            }

            return frameBitmap
        }
    }
}