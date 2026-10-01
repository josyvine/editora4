package com.vineyard.aivideostudio.voice.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class VoiceProfile(
    val id: String,
    val name: String,
    val description: String = "",
    val isReplicated: Boolean = false,
    val sampleAudioUri: String? = null,
    val consentVerified: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class TtsRequest(
    val text: String,
    val voiceName: String = "Puck",
    val speed: Float = 1.0f,
    val outputFilePath: String
)

@JsonClass(generateAdapter = true)
data class TtsResult(
    val success: Boolean,
    val audioFilePath: String?,
    val durationSeconds: Double = 0.0,
    val errorMessage: String? = null
)
