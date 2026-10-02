package com.vineyard.aivideostudio.media.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.vineyard.aivideostudio.core.model.PipelineStatus
import com.vineyard.aivideostudio.core.model.effects.NormalizedBounds
import com.vineyard.aivideostudio.core.model.effects.TrackingIndicatorSpec
import com.vineyard.aivideostudio.processing.logger.LogSeverity
import com.vineyard.aivideostudio.processing.logger.ProcessingLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.hypot

object OcrAnchorCalibrator {

    private const val TAG = "OcrAnchorCalibrator"
    private const val MAX_SEARCH_RADIUS_NORMALIZED = 0.35f // Max search radius around target zone

    /**
     * Universally calibrates tracking indicators for any video:
     * 1. Inspects settled video frames across the trigger window to avoid animation blur.
     * 2. Finds matching text lines with strict regional and token gating.
     * 3. Snaps bounding boxes directly to real UI targets with zero human script editing.
     * 4. Dispatches full real-time diagnostic telemetry to the in-app log console.
     */
    suspend fun calibrateIndicators(
        context: Context,
        videoUri: Uri,
        indicators: List<TrackingIndicatorSpec>,
        videoWidth: Int,
        videoHeight: Int,
        logger: ProcessingLogger? = null,
        projectId: String? = null
    ): List<TrackingIndicatorSpec> = withContext(Dispatchers.IO) {
        if (indicators.isEmpty()) return@withContext indicators

        val retriever = MediaMetadataRetriever()
        val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        try {
            retriever.setDataSource(context, videoUri)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot set data source for video frame extraction: ${e.message}")
            if (projectId != null && logger != null) {
                logger.log(
                    projectId,
                    PipelineStatus.EXPORTING,
                    "OCR frame extraction skipped: ${e.message}",
                    LogSeverity.WARNING
                )
            }
            return@withContext indicators
        }

        val calibratedIndicators = mutableListOf<TrackingIndicatorSpec>()

        try {
            for (indicator in indicators) {
                // Strict Guard: Never process physical objects or faces with OCR
                val isExplicitObjectOrFace = indicator.targetType.equals("object", ignoreCase = true) ||
                        indicator.targetType.equals("face", ignoreCase = true)

                if (isExplicitObjectOrFace) {
                    calibratedIndicators.add(indicator)
                    continue
                }

                val searchTarget = indicator.targetText?.trim()
                val isExplicitTextTarget = indicator.targetType.equals("ocr_text", ignoreCase = true) ||
                        indicator.targetType.equals("text", ignoreCase = true)

                // Only form an anchorQuery if explicitly marked as text or targetText is provided
                val anchorQuery = when {
                    !searchTarget.isNullOrBlank() -> searchTarget
                    isExplicitTextTarget && !indicator.label.isNullOrBlank() && indicator.label.length > 2 -> indicator.label.trim()
                    else -> null
                }

                if (anchorQuery == null) {
                    calibratedIndicators.add(indicator)
                    continue
                }

                val durationMs = (indicator.endTimeMs - indicator.startTimeMs).coerceAtLeast(0L)

                // Multi-Frame Temporal Settling:
                // Check settled moments (+400ms, +0ms, +800ms) to allow menus/drawers to finish animating
                val sampleOffsetsMs = when {
                    durationMs >= 1500L -> listOf(
                        minOf(450L, durationMs / 2L),  // Priority 1: Settled frame after animation
                        0L,                             // Priority 2: Trigger start frame
                        minOf(900L, durationMs * 2 / 3) // Priority 3: Deep window fallback
                    )
                    durationMs >= 600L -> listOf(durationMs / 2L, 0L)
                    else -> listOf(0L)
                }

                val hintCenterX = indicator.staticBounds?.centerX ?: 0.5f
                val hintCenterY = indicator.staticBounds?.centerY ?: 0.5f

                var matchedRect: Rect? = null
                var matchedSampleMs = indicator.startTimeMs
                var frameW = videoWidth.toFloat().coerceAtLeast(1f)
                var frameH = videoHeight.toFloat().coerceAtLeast(1f)

                for (offsetMs in sampleOffsetsMs) {
                    val sampleTimeMs = indicator.startTimeMs + offsetMs
                    val timeUs = (sampleTimeMs * 1000L).coerceAtLeast(0L)

                    val frameBitmap = try {
                        retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?: retriever.getFrameAtTime(timeUs)
                    } catch (e: Exception) {
                        null
                    } ?: continue

                    frameW = frameBitmap.width.toFloat().coerceAtLeast(1f)
                    frameH = frameBitmap.height.toFloat().coerceAtLeast(1f)

                    val recognizedText = processOcr(textRecognizer, frameBitmap)
                    val rect = findBestMatchingBlock(
                        ocrText = recognizedText,
                        query = anchorQuery,
                        hintCenterX = hintCenterX,
                        hintCenterY = hintCenterY,
                        frameWidth = frameW,
                        frameHeight = frameH
                    )

                    frameBitmap.recycle()

                    if (rect != null) {
                        matchedRect = rect
                        matchedSampleMs = sampleTimeMs
                        break // Found high-confidence match on settled frame
                    }
                }

                if (matchedRect != null) {
                    // Universal padded bounds
                    val padX = (matchedRect.width() * 0.10f).coerceAtLeast(16f)
                    val padY = (matchedRect.height() * 0.12f).coerceAtLeast(12f)

                    // Expand right edge if target is an interactive toggle row or settings item
                    val isToggleRow = anchorQuery.contains("all models", ignoreCase = true) ||
                            anchorQuery.contains("Grounding", ignoreCase = true) ||
                            anchorQuery.contains("Search", ignoreCase = true)

                    val expandRight = if (isToggleRow) (frameW * 0.18f) else padX

                    val snappedLeft = ((matchedRect.left - padX) / frameW).coerceIn(0.0f, 1.0f)
                    val snappedTop = ((matchedRect.top - padY) / frameH).coerceIn(0.0f, 1.0f)
                    val snappedRight = ((matchedRect.right + expandRight) / frameW).coerceIn(snappedLeft + 0.04f, 1.0f)
                    val snappedBottom = ((matchedRect.bottom + padY) / frameH).coerceIn(snappedTop + 0.02f, 1.0f)

                    val scriptTop = indicator.staticBounds?.top ?: 0f
                    val deltaY = snappedTop - scriptTop
                    val offsetUsed = matchedSampleMs - indicator.startTimeMs

                    val logMsg = "🎯 [OCR_AUTOFIX] Snapped '${indicator.id}' ['$anchorQuery'] on Settled Frame at ${matchedSampleMs}ms (+${offsetUsed}ms): " +
                            "Script Y=${"%.2f".format(scriptTop)} -> Real Text Y=${"%.2f".format(snappedTop)} (ΔY=${"%.2f".format(deltaY)})"

                    Log.i(TAG, logMsg)
                    if (projectId != null && logger != null) {
                        logger.log(
                            projectId,
                            PipelineStatus.EXPORTING,
                            logMsg,
                            LogSeverity.SUCCESS
                        )
                    }

                    val updatedBounds = NormalizedBounds(
                        left = snappedLeft,
                        top = snappedTop,
                        right = snappedRight,
                        bottom = snappedBottom
                    )

                    calibratedIndicators.add(
                        indicator.copy(
                            staticBounds = updatedBounds,
                            trackingMode = "static"
                        )
                    )
                } else {
                    // Safe fallback: Retain original script bounds if text was not detected
                    if (projectId != null && logger != null) {
                        logger.log(
                            projectId,
                            PipelineStatus.EXPORTING,
                            "Anchor text '$anchorQuery' not detected across settled window near (X:${"%.2f".format(hintCenterX)}, Y:${"%.2f".format(hintCenterY)}), retaining script bounds [T=${"%.2f".format(indicator.staticBounds?.top ?: 0f)}]",
                            LogSeverity.INFO
                        )
                    }
                    calibratedIndicators.add(indicator)
                }
            }
        } finally {
            try { retriever.release() } catch (_: Exception) {}
            try { textRecognizer.close() } catch (_: Exception) {}
        }

        calibratedIndicators
    }

    private suspend fun processOcr(
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        bitmap: Bitmap
    ): Text? = suspendCancellableCoroutine { continuation ->
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(inputImage)
            .addOnSuccessListener { text ->
                if (continuation.isActive) continuation.resume(text)
            }
            .addOnFailureListener {
                if (continuation.isActive) continuation.resume(null)
            }
    }

    /**
     * Finds the closest matching text element with priority given to individual lines first,
     * preventing whole multi-row blocks from being mistakenly highlighted.
     * Enforces strict spatial proximity and numeric token isolation.
     */
    private fun findBestMatchingBlock(
        ocrText: Text?,
        query: String,
        hintCenterX: Float,
        hintCenterY: Float,
        frameWidth: Float,
        frameHeight: Float
    ): Rect? {
        if (ocrText == null || query.isBlank()) return null
        val cleanQuery = query.lowercase().replace("_", " ").trim()
        val queryKeywords = cleanQuery.split(" ").filter { it.length > 1 }

        // Extract version/model numbers if present (e.g. "3.1", "3.5", "2.5")
        val requiredNumberTokens = queryKeywords.filter { token ->
            token.any { it.isDigit() }
        }

        val isBottomActionTarget = hintCenterY >= 0.80f

        var bestRect: Rect? = null
        var bestScore = -1f

        // PASS 1: Search individual Lines first for pinpoint single-row precision
        for (block in ocrText.textBlocks) {
            for (line in block.lines) {
                val lineBox = line.boundingBox ?: continue
                val lineNormCenterX = lineBox.exactCenterX() / frameWidth
                val lineNormCenterY = lineBox.exactCenterY() / frameHeight

                // Strict Regional Guard: If target is a bottom action button, ignore text in top/middle screen
                if (isBottomActionTarget && lineNormCenterY < 0.75f) {
                    continue
                }

                val distance = hypot(lineNormCenterX - hintCenterX, lineNormCenterY - hintCenterY)
                if (distance > MAX_SEARCH_RADIUS_NORMALIZED) {
                    continue
                }

                val lineText = line.text.lowercase().trim()

                // If query specifies numbers (e.g. "3.1" or "3.5"), require them on the line
                if (requiredNumberTokens.isNotEmpty()) {
                    val hasAllNumbers = requiredNumberTokens.all { lineText.contains(it) }
                    if (!hasAllNumbers) {
                        continue
                    }
                }

                val proximityBonus = (1.0f - (distance / MAX_SEARCH_RADIUS_NORMALIZED)).coerceIn(0.0f, 1.0f)

                // Exact line phrase match
                if (lineText.contains(cleanQuery)) {
                    val score = 3.0f + (proximityBonus * 1.5f)
                    if (score > bestScore) {
                        bestScore = score
                        bestRect = lineBox
                    }
                    continue
                }

                // Keyword overlap match on line
                val keywordMatches = queryKeywords.count { lineText.contains(it) }
                if (keywordMatches > 0) {
                    val matchRatio = keywordMatches.toFloat() / queryKeywords.size.coerceAtLeast(1)
                    if (matchRatio >= 0.5f) {
                        val score = (matchRatio * 2.0f) + (proximityBonus * 1.5f)
                        if (score > bestScore) {
                            bestScore = score
                            bestRect = lineBox
                        }
                    }
                }
            }
        }

        if (bestRect != null) {
            return bestRect
        }

        // PASS 2: Fallback to TextBlocks if text is wrapped across lines
        for (block in ocrText.textBlocks) {
            val blockBox = block.boundingBox ?: continue
            val blockNormCenterX = blockBox.exactCenterX() / frameWidth
            val blockNormCenterY = blockBox.exactCenterY() / frameHeight

            if (isBottomActionTarget && blockNormCenterY < 0.75f) {
                continue
            }

            val distance = hypot(blockNormCenterX - hintCenterX, blockNormCenterY - hintCenterY)
            if (distance > MAX_SEARCH_RADIUS_NORMALIZED) {
                continue
            }

            val unifiedBlockText = block.text.replace("\n", " ").lowercase()

            if (requiredNumberTokens.isNotEmpty()) {
                val hasAllNumbers = requiredNumberTokens.all { unifiedBlockText.contains(it) }
                if (!hasAllNumbers) {
                    continue
                }
            }

            if (unifiedBlockText.contains(cleanQuery)) {
                val proximityBonus = (1.0f - (distance / MAX_SEARCH_RADIUS_NORMALIZED)).coerceIn(0.0f, 1.0f)
                val score = 1.5f + (proximityBonus * 1.0f)
                if (score > bestScore) {
                    bestScore = score
                    bestRect = blockBox
                }
            }
        }

        return bestRect
    }
}