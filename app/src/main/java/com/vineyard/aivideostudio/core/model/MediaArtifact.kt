package com.vineyard.aivideostudio.core.model

import com.squareup.moshi.JsonClass

enum class ArtifactType {
    SOURCE_VIDEO,
    INTERMEDIATE_VIDEO,
    FINAL_VIDEO,
    EXTRACTED_AUDIO,
    COMMENTARY_AUDIO,
    MIXED_AUDIO,
    TRANSCRIPT,
    THUMBNAIL
}

@JsonClass(generateAdapter = true)
data class MediaArtifact(
    val id: String,
    val projectId: String,
    val stage: PipelineStatus,
    val type: ArtifactType,
    val fileUri: String,
    val filePath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationSeconds: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)
