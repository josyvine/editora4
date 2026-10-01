package com.vineyard.aivideostudio.ai.model

import com.squareup.moshi.JsonClass
import com.vineyard.aivideostudio.core.model.QaVerdict

enum class CueExecutionMode {
    SINGLE,
    MULTIPLE
}

@JsonClass(generateAdapter = true)
data class SceneSegment(
    val start: Double,
    val end: Double,
    val description: String,
    val importance: String = "MEDIUM", // CRITICAL, HIGH, MEDIUM, LOW, REMOVABLE
    val keySubjects: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class DialogueSegment(
    val start: Double,
    val end: Double,
    val speaker: String = "Speaker",
    val text: String
)

@JsonClass(generateAdapter = true)
data class EditingCandidate(
    val start: Double,
    val end: Double,
    val recommendation: String, // TRIM, REFRAME, ZOOM, HIGHLIGHT
    val reason: String
)

@JsonClass(generateAdapter = true)
data class HighlightSegment(
    val start: Double,
    val end: Double,
    val title: String = "",
    val description: String = "",
    val importance: String = "HIGH" // CRITICAL, HIGH, MEDIUM
)

@JsonClass(generateAdapter = true)
data class SourceAnalysis(
    val duration: Double,
    val resolution: String = "1920x1080",
    val orientation: String = "LANDSCAPE",
    val category: String = "ENTERTAINMENT", // SPORTS, NEWS, COMEDY, GAMING, DOCUMENTARY, ENTERTAINMENT
    val summary: String,
    val scenes: List<SceneSegment> = emptyList(),
    val dialogueSegments: List<DialogueSegment> = emptyList(),
    val criticalContent: List<String> = emptyList(),
    val highlights: List<HighlightSegment> = emptyList(),
    val editingCandidates: List<EditingCandidate> = emptyList(),
    val suggestedEditingStrategy: String = ""
)

@JsonClass(generateAdapter = true)
data class TrimSegment(
    val start: Double,
    val end: Double,
    val reason: String = ""
)

@JsonClass(generateAdapter = true)
data class TrimDecision(
    val operation: String = "trim", // "trim", "highlight_compile", or "skip"
    val isNecessary: Boolean = false,
    val targetMode: String = "HIGHLIGHTS", // "SHORT_60S", "RECAP_EXTENDED", "TRIM_REMOVE"
    val segmentsToRemove: List<TrimSegment> = emptyList(),
    val segmentsToKeep: List<HighlightSegment> = emptyList(),
    val explanation: String = ""
)

@JsonClass(generateAdapter = true)
data class CropDecision(
    val operation: String = "crop", // "crop" or "skip"
    val isNecessary: Boolean = false,
    val x: Float = 0.0f,
    val y: Float = 0.0f,
    val width: Float = 1.0f,
    val height: Float = 1.0f,
    val targetAspectRatio: String = "ORIGINAL",
    val explanation: String = ""
)

@JsonClass(generateAdapter = true)
data class ZoomDecision(
    val operation: String = "zoom", // "zoom" or "skip"
    val isNecessary: Boolean = false,
    val start: Double = 0.0,
    val end: Double = 0.0,
    val fromScale: Float = 1.0f,
    val toScale: Float = 1.28f,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val explanation: String = ""
)

@JsonClass(generateAdapter = true)
data class CaptionItem(
    val text: String,
    val start: Double,
    val end: Double,
    val x: Float = 0.5f,
    val y: Float = 0.85f,
    val style: String = "BOLD",
    val colorHex: String = "#FFFFFF"
)

@JsonClass(generateAdapter = true)
data class CaptionDecision(
    val operation: String = "caption",
    val isNecessary: Boolean = false,
    val captions: List<CaptionItem> = emptyList(),
    val explanation: String = ""
)

@JsonClass(generateAdapter = true)
data class CommentaryItem(
    val start: Double,
    val end: Double,
    val text: String
)

@JsonClass(generateAdapter = true)
data class CommentaryDecision(
    val operation: String = "commentary",
    val isNecessary: Boolean = false,
    val commentarySegments: List<CommentaryItem> = emptyList(),
    val tone: String = "genre-adapted dynamic commentary with intense vocal cues",
    val explanation: String = "",
    val cueMode: String = "single",          // "single" or "multiple"
    val cueConcurrency: Int = 1              // Dynamic concurrency limit
)

@JsonClass(generateAdapter = true)
data class AiQaResponse(
    val verdict: String, // PASS, FAIL, NEEDS_CORRECTION
    val confidence: Float = 1.0f,
    val feedback: String,
    val corrections: List<String> = emptyList(),
    val criticalContentPreserved: Boolean = true
) {
    fun toQaVerdict(): QaVerdict = when (verdict.uppercase()) {
        "PASS" -> QaVerdict.PASS
        "FAIL" -> QaVerdict.FAIL
        "NEEDS_CORRECTION" -> QaVerdict.NEEDS_CORRECTION
        else -> QaVerdict.PASS
    }
}