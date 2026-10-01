package com.vineyard.aivideostudio

import com.vineyard.aivideostudio.ai.classifier.ModelCapabilityClassifier
import com.vineyard.aivideostudio.ai.model.ModelPurpose
import com.vineyard.aivideostudio.data.remote.gemini.ModelDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCapabilityClassifierTest {

    @Test
    fun testFlashModelClassification() {
        val dto = ModelDto(
            name = "models/gemini-3.5-flash",
            displayName = "Gemini Flash",
            description = "Fast multimodal model for video and text understanding",
            supportedGenerationMethods = listOf("generateContent", "countTokens")
        )

        val info = ModelCapabilityClassifier.classify(dto)
        assertTrue(info.capabilities.supportsVideo)
        assertTrue(info.capabilities.supportsText)
        assertTrue(info.supportedPurposes.contains(ModelPurpose.VIDEO_ANALYSIS))
        assertTrue(info.supportedPurposes.contains(ModelPurpose.EDITING_DIRECTOR))
    }

    @Test
    fun testTtsModelClassification() {
        val dto = ModelDto(
            name = "models/gemini-2.5-flash-preview-tts",
            displayName = "Gemini TTS",
            description = "Text-to-speech audio model",
            supportedGenerationMethods = listOf("generateContent")
        )

        val info = ModelCapabilityClassifier.classify(dto)
        assertTrue(info.capabilities.supportsTts)
        assertTrue(info.supportedPurposes.contains(ModelPurpose.TEXT_TO_SPEECH))
    }

    @Test
    fun testLiveVoiceModelClassification() {
        val dto = ModelDto(
            name = "models/gemini-2.5-flash-native-audio-preview-12-2025",
            displayName = "Gemini Live Audio",
            description = "Low-latency bidirectional native audio interaction",
            supportedGenerationMethods = listOf("generateContent")
        )

        val info = ModelCapabilityClassifier.classify(dto)
        assertTrue(info.capabilities.supportsLive)
        assertTrue(info.supportedPurposes.contains(ModelPurpose.LIVE_VOICE))
    }
}
