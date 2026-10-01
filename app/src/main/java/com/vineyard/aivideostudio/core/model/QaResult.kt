package com.vineyard.aivideostudio.core.model

import com.squareup.moshi.JsonClass

enum class QaVerdict {
    PASS,
    FAIL,
    NEEDS_CORRECTION,
    SKIPPED
}

@JsonClass(generateAdapter = true)
data class QaResult(
    val id: String,
    val projectId: String,
    val stage: PipelineStatus,
    val verdict: QaVerdict,
    val feedback: String,
    val corrections: List<String> = emptyList(),
    val confidence: Float = 1.0f,
    val timestamp: Long = System.currentTimeMillis()
)
