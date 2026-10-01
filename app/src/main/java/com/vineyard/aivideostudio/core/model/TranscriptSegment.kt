package com.vineyard.aivideostudio.core.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TranscriptSegment(
    val id: String,
    val projectId: String,
    val start: Double,
    val end: Double,
    val text: String,
    val speaker: String? = null
)
