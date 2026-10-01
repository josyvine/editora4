package com.vineyard.aivideostudio.core.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CommentarySegment(
    val id: String,
    val projectId: String,
    val start: Double,
    val end: Double,
    val text: String,
    val audioArtifactUri: String? = null,
    val voiceId: String? = null
)
