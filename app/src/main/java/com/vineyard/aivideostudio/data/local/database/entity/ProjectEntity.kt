package com.vineyard.aivideostudio.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.vineyard.aivideostudio.core.model.PipelineStatus

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sourceUri: String,
    val sourcePath: String,
    val currentVideoUri: String,
    val sourceYoutubeUrl: String? = null,
    val masterRecipeJson: String? = null, // Ingested Master Recipe JSON
    val finalVideoUri: String?,
    val thumbnailUri: String?,
    val durationSeconds: Double,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val frameRate: Float,
    val bitrate: Long,
    val videoCodec: String,
    val audioCodec: String?,
    val fileSize: Long,
    val currentStage: PipelineStatus,
    val status: PipelineStatus,
    val sourceAnalysisJson: String?,
    val timelineMapJson: String?,
    val targetAspectRatio: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastError: String?
)