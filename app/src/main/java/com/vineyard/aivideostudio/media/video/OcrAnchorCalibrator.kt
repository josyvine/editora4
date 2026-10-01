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
import kotlin.math.hypot

object OcrAnchorCalibrator {

    private const val TAG = "OcrAnchorCalibrator"
    private const val MAX_SEARCH_RADIUS_NORMALIZED = 0.30f // Search within 30% of estimated area

    /**
     * Universally calibrates tracking indicators for any video:
     * 1. Inspects video frames at trigger times.
     * 2. Finds matching text blocks near the estimated coordinates.
     * 3. Snaps bounding boxes directly to the real UI targets with zero human script editing.
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
                val searchTarget = indicator.targetText?.trim()
                val hasSearchTarget = !searchTarget.isNullOrBlank()

                val anchorQuery = if (hasSearchTarget) {
                    searchTarget
                } else if (!indicator.label.isNullOrBlank() && indicator.label.length > 2) {
                    indicator.label.trim()
                } else {
                    null
                }

                if (anchorQuery == null) {
                    calibratedIndicators.add(indicator)
                    continue
                }

                // 1. Extract snapshot frame at the indicator's trigger millisecond
                val timeUs = (indicator.startTimeMs * 1000L).coerceAtLeast(0L)
                val frameBitmap = try {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.getFrameAtTime(timeUs)
                } catch (e: Exception) {
                    null
                }

                if (frameBitmap == null) {
                    calibratedIndicators.add(indicator)
                    continue
                }

                val frameW = frameBitmap.width.toFloat().coerceAtLeast(1f)
                val frameH = frameBitmap.height.toFloat().coerceAtLeast(1f)

                // 2. Resolve estimated center point from script
                val hintCenterX = indicator.staticBounds?.centerX ?: 0.5f
                val hintCenterY = indicator.staticBounds?.centerY ?: 0.5f

                if (projectId != null && logger != null) {
                    logger.log(
                        projectId,
                        PipelineStatus.EXPORTING,
                        "Scanning frame at ${indicator.startTimeMs}ms for on-screen anchor '$anchorQuery'...",
                        LogSeverity.INFO
                    )
                }

                // 3. Scan frame using on-device ML Kit OCR
                val recognizedText = processOcr(textRecognizer, frameBitmap)
                val matchedRect = findBestMatchingBlock(
                    ocrText = recognizedText,
                    query = anchorQuery,
                    hintCenterX = hintCenterX,
                    hintCenterY = hintCenterY,
                    frameWidth = frameW,
                    frameHeight = frameH
                )

                if (matchedRect != null) {
                    // 4. Calculate universal padded bounds
                    val padX = (matchedRect.width() * 0.12f).coerceAtLeast(20f)
                    val padY = (matchedRect.height() * 0.15f).coerceAtLeast(14f)

                    // Expand right edge if target is an interactive toggle row
                    val isToggleRow = anchorQuery.contains("Grounding", ignoreCase = true) ||
                            anchorQuery.contains("context", ignoreCase = true) ||
                            anchorQuery.contains("Search", ignoreCase = true) ||
                            anchorQuery.contains("model", ignoreCase = true)

                    val expandRight = if (isToggleRow) (frameW * 0.22f) else padX

                    val snappedLeft = ((matchedRect.left - padX) / frameW).coerceIn(0.0f, 1.0f)
                    val snappedTop = ((matchedRect.top - padY) / frameH).coerceIn(0.0f, 1.0f)
                    val snappedRight = ((matchedRect.right + expandRight) / frameW).coerceIn(snappedLeft + 0.05f, 1.0f)
                    val snappedBottom = ((matchedRect.bottom + padY) / frameH).coerceIn(snappedTop + 0.02f, 1.0f)

                    val logMsg = "[OCR_AUTOFIX] Snapped '${indicator.id}' ['$anchorQuery'] " +
                            "from Script Y=${"%.2f".format(indicator.staticBounds?.top ?: 0f)} -> Real Text Y=${"%.2f".format(snappedTop)}"

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
                            "Anchor text '$anchorQuery' not detected on frame, retaining script bounds [T=${"%.2f".format(indicator.staticBounds?.top ?: 0f)}]",
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
     * Finds the closest matching text block to the estimated target coordinates,
     * handling multi-line text wrapping and rejecting far-away false positives.
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
        val queryKeywords = cleanQuery.split(" ").filter { it.length > 2 }

        var bestRect: Rect? = null
        var bestScore = -1f

        for (block in ocrText.textBlocks) {
            val blockBox = block.boundingBox ?: continue
            val blockNormCenterX = (blockBox.exactCenterX()) / frameWidth
            val blockNormCenterY = (blockBox.exactCenterY()) / frameHeight

            // Proximity Filter: Only consider elements within the target neighborhood
            val distance = hypot(blockNormCenterX - hintCenterX, blockNormCenterY - hintCenterY)
            if (distance > MAX_SEARCH_RADIUS_NORMALIZED && hintCenterX != 0.5f) {
                continue
            }

            val unifiedBlockText = block.text.replace("\n", " ").lowercase()

            // 1. Exact phrase match inside block
            if (unifiedBlockText.contains(cleanQuery)) {
                return blockBox
            }

            // 2. Keyword overlap match
            val keywordMatches = queryKeywords.count { unifiedBlockText.contains(it) }
            if (keywordMatches > 0) {
                val matchRatio = keywordMatches.toFloat() / queryKeywords.size.coerceAtLeast(1)
                val proximityWeight = (1.0f - (distance / MAX_SEARCH_RADIUS_NORMALIZED)).coerceIn(0.1f, 1.0f)
                val score = (matchRatio * 0.7f) + (proximityWeight * 0.3f)

                if (score > bestScore && matchRatio >= 0.5f) {
                    bestScore = score
                    bestRect = blockBox
                }
            }

            // 3. Line-level check inside block for single-line targets
            for (line in block.lines) {
                val lineText = line.text.lowercase()
                val lineBox = line.boundingBox ?: continue
                val lineNormCenterY = lineBox.exactCenterY() / frameHeight
                val lineDist = kotlin.math.abs(lineNormCenterY - hintCenterY)

                if (lineText.contains(cleanQuery) && lineDist <= MAX_SEARCH_RADIUS_NORMALIZED) {
                    return lineBox
                }
            }
        }

        return bestRect
    }
}