package com.vineyard.aivideostudio.ai.model

import com.squareup.moshi.JsonClass

enum class ModelPurpose(val displayName: String, val description: String) {
    VIDEO_ANALYSIS("Video Analysis", "Visual reasoning, scene understanding, composition detection"),
    AUDIO_TRANSCRIPTION("Audio / Transcription", "Speech-to-text, dialogue segmentation and audio summary"),
    EDITING_DIRECTOR("Editing Director", "Sequential cut, trim, crop, zoom and pacing decisions"),
    COMMENTARY("Commentary Generation", "Narrative creation, voiceover script writing, contextual commentary"),
    TEXT_TO_SPEECH("Text-to-Speech", "Synthesizing voiceover commentary audio"),
    LIVE_VOICE("Live Voice", "Real-time conversational voice interaction for directing")
}

@JsonClass(generateAdapter = true)
data class ModelCapabilities(
    val supportsVideo: Boolean = false,
    val supportsAudio: Boolean = false,
    val supportsText: Boolean = true,
    val supportsTts: Boolean = false,
    val supportsLive: Boolean = false,
    val supportsStructuredJson: Boolean = true
)

@JsonClass(generateAdapter = true)
data class ModelInfo(
    val id: String, // e.g. "models/gemini-3.5-flash"
    val baseId: String, // e.g. "gemini-3.5-flash"
    val displayName: String,
    val description: String,
    val version: String,
    val inputTokenLimit: Int,
    val outputTokenLimit: Int,
    val capabilities: ModelCapabilities,
    val supportedPurposes: List<ModelPurpose>
)
