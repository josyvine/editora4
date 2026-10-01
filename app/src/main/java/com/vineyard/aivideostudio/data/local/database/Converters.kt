package com.vineyard.aivideostudio.data.local.database

import androidx.room.TypeConverter
import com.vineyard.aivideostudio.core.model.ArtifactType
import com.vineyard.aivideostudio.core.model.PipelineStatus
import com.vineyard.aivideostudio.core.model.QaVerdict
import com.vineyard.aivideostudio.core.model.StepStatus

class Converters {
    @TypeConverter
    fun fromPipelineStatus(status: PipelineStatus): String = status.name

    @TypeConverter
    fun toPipelineStatus(name: String): PipelineStatus = try {
        PipelineStatus.valueOf(name)
    } catch (_: Exception) {
        PipelineStatus.IDLE
    }

    @TypeConverter
    fun fromStepStatus(status: StepStatus): String = status.name

    @TypeConverter
    fun toStepStatus(name: String): StepStatus = try {
        StepStatus.valueOf(name)
    } catch (_: Exception) {
        StepStatus.PENDING
    }

    @TypeConverter
    fun fromArtifactType(type: ArtifactType): String = type.name

    @TypeConverter
    fun toArtifactType(name: String): ArtifactType = try {
        ArtifactType.valueOf(name)
    } catch (_: Exception) {
        ArtifactType.SOURCE_VIDEO
    }

    @TypeConverter
    fun fromQaVerdict(verdict: QaVerdict?): String? = verdict?.name

    @TypeConverter
    fun toQaVerdict(name: String?): QaVerdict? = name?.let {
        try {
            QaVerdict.valueOf(it)
        } catch (_: Exception) {
            null
        }
    }
}
