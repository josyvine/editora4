package com.vineyard.aivideostudio.core.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Project(
    val id: String,
    val name: String,
    val sourceUri: String,
    val sourcePath: String,
    val currentVideoUri: String,
    val sourceYoutubeUrl: String? = null,
    val masterRecipeJson: String? = null, // Ingested Master Recipe from AI Studio Web
    val finalVideoUri: String? = null,
    val thumbnailUri: String? = null,
    val metadata: VideoMetadata = VideoMetadata(),
    val currentStage: PipelineStatus = PipelineStatus.IDLE,
    val status: PipelineStatus = PipelineStatus.IDLE,
    val sourceAnalysisJson: String? = null,
    val timelineMapJson: String? = null,
    val targetAspectRatio: String = "ORIGINAL", // ORIGINAL, 9:16, 16:9, 1:1
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastError: String? = null
)