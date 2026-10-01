package com.vineyard.aivideostudio.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vineyard.aivideostudio.core.model.ArtifactType
import com.vineyard.aivideostudio.core.model.PipelineStatus
import com.vineyard.aivideostudio.core.model.QaVerdict
import com.vineyard.aivideostudio.core.model.StepStatus

@Entity(
    tableName = "pipeline_steps",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"]), Index(value = ["projectId", "stage"], unique = true)]
)
data class PipelineStepEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val stage: PipelineStatus,
    val status: StepStatus,
    val retryCount: Int,
    val maxRetries: Int,
    val inputArtifactUri: String?,
    val outputArtifactUri: String?,
    val message: String?,
    val errorMessage: String?,
    val qaVerdict: QaVerdict?,
    val startTime: Long?,
    val endTime: Long?
)

@Entity(
    tableName = "media_artifacts",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class MediaArtifactEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val stage: PipelineStatus,
    val type: ArtifactType,
    val fileUri: String,
    val filePath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationSeconds: Double,
    val createdAt: Long
)

@Entity(
    tableName = "timeline_segments",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class TimelineSegmentEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val originalStart: Double,
    val originalEnd: Double,
    val currentStart: Double,
    val currentEnd: Double,
    val isRetained: Boolean,
    val stageApplied: PipelineStatus
)

@Entity(
    tableName = "transcript_segments",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class TranscriptSegmentEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val start: Double,
    val end: Double,
    val text: String,
    val speaker: String?
)

@Entity(
    tableName = "captions",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class CaptionEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val text: String,
    val start: Double,
    val end: Double,
    val x: Float,
    val y: Float,
    val fontSizeSp: Float,
    val fontColorHex: String,
    val backgroundColorHex: String?,
    val style: String
)

@Entity(
    tableName = "commentary_segments",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class CommentaryEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val start: Double,
    val end: Double,
    val text: String,
    val audioArtifactUri: String?,
    val voiceId: String?
)

@Entity(
    tableName = "ai_requests",
    indices = [Index(value = ["projectId"])]
)
data class AiRequestEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val stage: PipelineStatus,
    val modelId: String,
    val promptSummary: String,
    val rawResponse: String?,
    val latencyMs: Long,
    val isSuccess: Boolean,
    val errorMessage: String?,
    val timestamp: Long
)

@Entity(
    tableName = "qa_results",
    indices = [Index(value = ["projectId"])]
)
data class QaResultEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val stage: PipelineStatus,
    val verdict: QaVerdict,
    val feedback: String,
    val correctionsJson: String,
    val confidence: Float,
    val timestamp: Long
)

@Entity(tableName = "model_configurations")
data class ModelConfigurationEntity(
    @PrimaryKey val purpose: String, // e.g., VIDEO_ANALYSIS, AUDIO_TRANSCRIPTION, etc.
    val modelId: String,
    val displayName: String,
    val description: String?,
    val inputTokenLimit: Int,
    val outputTokenLimit: Int,
    val supportedCapabilitiesJson: String,
    val updatedAt: Long
)

@Entity(tableName = "voices")
data class VoiceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val isReplicated: Boolean,
    val sampleAudioUri: String?,
    val consentVerified: Boolean,
    val createdAt: Long
)

@Entity(tableName = "persistent_logs")
data class PersistentLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: String,
    val stage: String,
    val severity: String,
    val message: String,
    val technicalDetails: String?,
    val timestamp: Long
)
