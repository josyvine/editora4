package com.vineyard.aivideostudio.core.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class VideoMetadata(
    val durationSeconds: Double = 0.0,
    val width: Int = 0,
    val height: Int = 0,
    val rotationDegrees: Int = 0,
    val frameRate: Float = 30f,
    val bitrate: Long = 0L,
    val videoCodec: String = "video/avc",
    val audioCodec: String? = null,
    val audioChannels: Int = 2,
    val audioSampleRate: Int = 44100,
    val fileSize: Long = 0L
) {
    val aspectRatio: Float
        get() = if (height > 0) width.toFloat() / height.toFloat() else 1.0f

    val isPortrait: Boolean
        get() = (rotationDegrees == 90 || rotationDegrees == 270) && width > height || (rotationDegrees == 0 || rotationDegrees == 180) && height > width
}
