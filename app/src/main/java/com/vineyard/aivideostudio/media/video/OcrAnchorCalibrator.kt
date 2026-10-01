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
import com.vineyard.aivideostudio.core.model.effects.NormalizedBounds
import com.vineyard.aivideostudio.core.model.effects.TrackingIndicatorSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object OcrAnchorCalibrator {

    private const val TAG = "OcrAnchorCalibrator"

    /**
     * Inspects video frames at indicator trigger times, finds real on-screen text,
     * and snaps misplaced script coordinates directly to the target UI elements.
     */
    suspend fun calibrateIndicators(
        context: Context,
        videoUri: Uri,
        indicators: List<TrackingIndicatorSpec>,
        videoWidth: Int,
        videoHeight: Int
    ): List<TrackingIndicatorSpec> = withContext(Dispatchers.IO) {
        if (indicators.isEmpty()) return@withContext indicators

        val retriever = MediaMetadataRetriever()
        val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        try {
            retriever.setDataSource(context, videoUri)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot set data source for video frame extraction: ${e.message}")
            return@withContext indicators
        }

        val calibratedIndicators = mutableListOf<TrackingIndicatorSpec>()

        try {
            for (indicator in indicators) {
                val searchTarget = indicator.targetText?.trim()
                val hasSearchTarget = !searchTarget.isNullOrBlank()

                // If no specific target text is declared, check if label can serve as search anchor
                val anchorQuery = if (hasSearchTarget) {
                    searchTarget
                } else if (!indicator.label.isNullOrBlank() && indicator.label.length > 3) {
                    indicator.label.trim()
                } else {
                    null
                }

                if (anchorQuery == null) {
                    calibratedIndicators.add(indicator)
                    continue
                }

                // 1. Extract ONE single frame snapshot at the indicator's trigger millisecond
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

                // 2. Scan frame using on-device ML Kit OCR
                val recognizedText = processOcr(textRecognizer, frameBitmap)
                val matchedRect = findMatchingTextBounds(recognizedText, anchorQuery)

                if (matchedRect != null) {
                    val frameW = frameBitmap.width.toFloat().coerceAtLeast(1f)
                    val frameH = frameBitmap.height.toFloat().coerceAtLeast(1f)

                    // 3. Snap & calculate padded normalized coordinates
                    // Pad horizontally by 12% to ensure adjacent toggles/switches are enclosed
                    val padX = (matchedRect.width() * 0.15f).coerceAtLeast(24f)
                    val padY = (matchedRect.height() * 0.20f).coerceAtLeast(16f)

                    // If it's a toggle row, expand right edge towards screen edge to include the toggle switch
                    val expandForToggle = if (indicator.label?.contains("GROUNDING", ignoreCase = true) == true ||
                        anchorQuery.contains("Grounding", ignoreCase = true) ||
                        anchorQuery.contains("context", ignoreCase = true)
                    ) {
                        frameW * 0.25f
                    } else {
                        padX
                    }

                    val snappedLeft = ((matchedRect.left - padX) / frameW).coerceIn(0.0f, 1.0f)
                    val snappedTop = ((matchedRect.top - padY) / frameH).coerceIn(0.0f, 1.0f)
                    val snappedRight = ((matchedRect.right + expandForToggle) / frameW).coerceIn(snappedLeft + 0.05f, 1.0f)
                    val snappedBottom = ((matchedRect.bottom + padY) / frameH).coerceIn(snappedTop + 0.02f, 1.0f)

                    Log.i(
                        TAG,
                        "Auto-Fix Snapped '${indicator.id}' [Anchor: '$anchorQuery'] " +
                                "from Y=${indicator.staticBounds?.top ?: 0f} to Real Text Y=$snappedTop"
                    )

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
                    // Safe fallback: Retain original script bounds if text was occluded
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

    private fun findMatchingTextBounds(ocrText: Text?, query: String): Rect? {
        if (ocrText == null || query.isBlank()) return null
        val cleanQuery = query.lowercase().replace("_", " ").trim()
        val queryKeywords = cleanQuery.split(" ").filter { it.length > 2 }

        var bestRect: Rect? = null
        var maxKeywordMatches = 0

        // Search text lines for exact phrase or high keyword overlap
        for (block in ocrText.textBlocks) {
            for (line in block.lines) {
                val lineText = line.text.lowercase()

                // Exact phrase match
                if (lineText.contains(cleanQuery)) {
                    return line.boundingBox
                }

                // Keyword overlap match (handles slight OCR misspelling or multi-line breaks)
                val matches = queryKeywords.count { lineText.contains(it) }
                if (matches > maxKeywordMatches && matches >= (queryKeywords.size / 2).coerceAtLeast(1)) {
                    maxKeywordMatches = matches
                    bestRect = line.boundingBox
                }
            }
        }

        return bestRect
    }
}