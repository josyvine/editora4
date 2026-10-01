package com.vineyard.aivideostudio.media.video

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.vineyard.aivideostudio.core.model.VideoMetadata
import com.vineyard.aivideostudio.core.util.UriUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoMetadataReader(private val context: Context) {

    suspend fun readMetadata(uri: Uri): VideoMetadata = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            val durationSeconds = durationMs / 1000.0

            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val width = widthStr?.toIntOrNull() ?: 1920

            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val height = heightStr?.toIntOrNull() ?: 1080

            val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            val rotation = rotationStr?.toIntOrNull() ?: 0

            val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            val bitrate = bitrateStr?.toLongOrNull() ?: 0L

            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "video/mp4"

            val fileSize = UriUtils.getFileSize(context, uri)

            VideoMetadata(
                durationSeconds = durationSeconds,
                width = width,
                height = height,
                rotationDegrees = rotation,
                frameRate = 30f,
                bitrate = bitrate,
                videoCodec = mimeType,
                fileSize = fileSize
            )
        } catch (e: Exception) {
            VideoMetadata(
                durationSeconds = 10.0,
                width = 1920,
                height = 1080,
                rotationDegrees = 0,
                frameRate = 30f,
                fileSize = UriUtils.getFileSize(context, uri)
            )
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }
}

object VideoValidator {
    fun validateSourceVideo(metadata: VideoMetadata): String? {
        if (metadata.durationSeconds <= 0.5) {
            return "Video duration is too short (< 0.5 seconds)"
        }
        if (metadata.width <= 0 || metadata.height <= 0) {
            return "Invalid video dimensions: ${metadata.width}x${metadata.height}"
        }
        return null
    }
}
