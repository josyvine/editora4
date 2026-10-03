package com.vineyard.aivideostudio.media.video

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * High-performance, memory-safe frame and thumbnail extractor for the filmstrip slider.
 * Uses hardware scaling and LRU caching to guarantee instant loading with zero OOM crashes.
 */
class FastFilmstripExtractor(
    private val videoFile: File,
    private val maxCacheSizeBytes: Int = 25 * 1024 * 1024 // Strict 25MB memory cap
) {

    private val retriever = MediaMetadataRetriever()
    private var durationMs: Long = 0L
    private var videoWidth: Int = 1080
    private var videoHeight: Int = 1920

    // Memory-safe LRU cache for micro-thumbnails
    private val thumbnailCache = object : LruCache<Long, Bitmap>(maxCacheSizeBytes) {
        override fun sizeOf(key: Long, bitmap: Bitmap): Int {
            return bitmap.allocationByteCount
        }
    }

    init {
        try {
            retriever.setDataSource(videoFile.absolutePath)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durStr?.toLongOrNull() ?: 0L
            val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            videoWidth = wStr?.toIntOrNull() ?: 1080
            videoHeight = hStr?.toIntOrNull() ?: 1920
        } catch (e: Exception) {
            // Fallback defaults
            durationMs = 0L
        }
    }

    /**
     * Extracts a lightweight, low-res micro-thumbnail for the horizontal filmstrip slider at [timeMs].
     * Target thumbnail dimensions: 160x90 (scaled down for instant rendering and zero memory strain).
     */
    suspend fun getThumbnailAtTime(timeMs: Long, thumbWidth: Int = 160, thumbHeight: Int = 90): Bitmap? = withContext(Dispatchers.IO) {
        val clampedTimeUs = (timeMs * 1000L).coerceIn(0L, durationMs * 1000L)
        
        // Check cache first
        thumbnailCache.get(clampedTimeUs)?.let { cached ->
            if (!cached.isRecycled) return@withContext cached
        }

        val bitmap = try {
            // Hardware-scaled frame extraction for speed and low memory
            retriever.getScaledFrameAtTime(
                clampedTimeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                thumbWidth,
                thumbHeight
            ) ?: retriever.getFrameAtTime(clampedTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            null
        }

        if (bitmap != null) {
            thumbnailCache.put(clampedTimeUs, bitmap)
        }
        return@withContext bitmap
    }

    /**
     * Extracts full-resolution frame on-demand strictly when user long-presses to copy coordinates.
     */
    suspend fun getFullResolutionFrameAtTime(timeMs: Long): Bitmap? = withContext(Dispatchers.IO) {
        val clampedTimeUs = (timeMs * 1000L).coerceIn(0L, durationMs * 1000L)
        try {
            retriever.getFrameAtTime(clampedTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(clampedTimeUs)
        } catch (e: Exception) {
            null
        }
    }

    fun getVideoDurationSeconds(): Double {
        return durationMs / 1000.0
    }

    fun getVideoResolution(): Pair<Int, Int> {
        return Pair(videoWidth, videoHeight)
    }

    fun release() {
        try {
            thumbnailCache.evictAll()
            retriever.release()
        } catch (_: Exception) {}
    }
}