package com.vineyard.aivideostudio.ai.classifier

import com.vineyard.aivideostudio.ai.model.ModelCapabilities
import com.vineyard.aivideostudio.ai.model.ModelInfo
import com.vineyard.aivideostudio.ai.model.ModelPurpose
import com.vineyard.aivideostudio.data.remote.gemini.ModelDto

object ModelCapabilityClassifier {

    /**
     * Classifies a Gemini ModelDto into a domain ModelInfo with detected capabilities and purposes.
     */
    fun classify(dto: ModelDto): ModelInfo {
        val rawId = dto.name
        val cleanId = rawId.removePrefix("models/")
        val lowerId = cleanId.lowercase()
        val lowerDesc = (dto.description ?: "").lowercase()
        val lowerMethods = dto.supportedGenerationMethods.map { it.lowercase() }

        val hasGenerateContent = lowerMethods.contains("generatecontent") || lowerMethods.contains("streamgeneratecontent")

        // 1. Detect Live Bidirectional WebSocket Models (Unlimited RPM/RPD)
        val isLive = lowerId.contains("native-audio") ||
                lowerId.contains("live") ||
                lowerId.contains("dialog") ||
                lowerDesc.contains("realtime") ||
                lowerDesc.contains("bidirectional") ||
                lowerDesc.contains("live api")

        // 2. Detect Legacy REST TTS Models (Subject to 10 RPD quota limits)
        val isTts = (lowerId.contains("-tts") || lowerId.endsWith("tts")) && !isLive

        // 3. Detect Multimodal Vision and Director Capabilities
        val isFlashOrPro = lowerId.contains("flash") || lowerId.contains("pro") || lowerId.contains("gemini")
        val isEmbeddingOrOther = lowerId.contains("embedding") || lowerId.contains("robotics") || lowerId.contains("image")

        val supportsVideo = isFlashOrPro && !isTts && !isEmbeddingOrOther && !isLive
        val supportsAudio = isFlashOrPro || isTts || isLive || lowerDesc.contains("audio") || lowerId.contains("transcribe")
        val supportsText = hasGenerateContent && !isEmbeddingOrOther
        val supportsStructuredJson = hasGenerateContent && !isLive && !isTts

        val capabilities = ModelCapabilities(
            supportsVideo = supportsVideo,
            supportsAudio = supportsAudio,
            supportsText = supportsText,
            supportsTts = isTts,
            supportsLive = isLive,
            supportsStructuredJson = supportsStructuredJson
        )

        // 4. Assign Qualified Production Purposes
        val purposes = mutableListOf<ModelPurpose>()

        // Video Director & Semantic Analysis
        if (capabilities.supportsVideo && capabilities.supportsStructuredJson) {
            purposes.add(ModelPurpose.VIDEO_ANALYSIS)
            purposes.add(ModelPurpose.EDITING_DIRECTOR)
        }

        // Dialogue Transcription
        if (capabilities.supportsAudio || lowerId.contains("transcribe")) {
            purposes.add(ModelPurpose.AUDIO_TRANSCRIPTION)
        }

        // Scriptwriting & Narration Composition
        if (capabilities.supportsText && capabilities.supportsStructuredJson) {
            purposes.add(ModelPurpose.COMMENTARY)
            if (!purposes.contains(ModelPurpose.EDITING_DIRECTOR)) {
                purposes.add(ModelPurpose.EDITING_DIRECTOR)
            }
        }

        // Dedicated Unlimited Live Commentary Voice Models
        if (capabilities.supportsLive) {
            purposes.add(ModelPurpose.LIVE_VOICE)
        }

        // Fallback REST TTS
        if (capabilities.supportsTts) {
            purposes.add(ModelPurpose.TEXT_TO_SPEECH)
        }

        return ModelInfo(
            id = rawId,
            baseId = cleanId,
            displayName = formatDisplayName(cleanId, dto.displayName),
            description = dto.description ?: "Gemini generative model",
            version = dto.version ?: "1.0",
            inputTokenLimit = dto.inputTokenLimit ?: 32768,
            outputTokenLimit = dto.outputTokenLimit ?: 8192,
            capabilities = capabilities,
            supportedPurposes = purposes
        )
    }

    /**
     * Prioritizes the optimal model for a given purpose based on rate limits and capability.
     */
    fun rankModelsForPurpose(models: List<ModelInfo>, purpose: ModelPurpose): List<ModelInfo> {
        return models.filter { it.supportedPurposes.contains(purpose) }
            .sortedByDescending { model -> calculateScore(model, purpose) }
    }

    private fun calculateScore(model: ModelInfo, purpose: ModelPurpose): Int {
        val id = model.baseId.lowercase()
        return when (purpose) {
            ModelPurpose.LIVE_VOICE -> when {
                id.contains("3.8-live") -> 100
                id.contains("3.1-flash-live") -> 95
                id.contains("3-flash-live") -> 90
                id.contains("2.5-flash-native-audio") -> 85
                id.contains("2.0-flash-exp") -> 80
                else -> 50
            }
            ModelPurpose.VIDEO_ANALYSIS, ModelPurpose.EDITING_DIRECTOR -> when {
                id.contains("3.1-flash-lite") -> 100 // High 500 RPD quota
                id.contains("3.5-flash-lite") -> 98
                id.contains("3.5-flash") -> 90
                id.contains("3-flash") -> 85
                id.contains("2.5-flash-lite") -> 80
                id.contains("2.5-flash") -> 75
                id.contains("pro") -> 60
                else -> 40
            }
            ModelPurpose.COMMENTARY -> when {
                id.contains("3.1-flash-lite") -> 100
                id.contains("3.5-flash-lite") -> 95
                id.contains("flash") -> 85
                else -> 50
            }
            ModelPurpose.AUDIO_TRANSCRIPTION -> when {
                id.contains("transcribe") -> 100
                id.contains("3.1-flash-lite") -> 90
                id.contains("flash") -> 80
                else -> 50
            }
            ModelPurpose.TEXT_TO_SPEECH -> when {
                id.contains("3.8-flash-lite-tts") -> 90
                id.contains("3.1-flash-tts") -> 80
                id.contains("2.5-flash-preview-tts") -> 50
                else -> 30
            }
        }
    }

    private fun formatDisplayName(cleanId: String, rawDisplayName: String?): String {
        if (!rawDisplayName.isNullOrBlank() && rawDisplayName != cleanId) {
            return rawDisplayName
        }
        return cleanId
            .replace("-", " ")
            .split(" ")
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }
}