package com.vineyard.aivideostudio.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vineyard.aivideostudio.core.model.PipelineStatus
import com.vineyard.aivideostudio.data.local.database.entity.AiRequestEntity
import com.vineyard.aivideostudio.data.local.database.entity.CaptionEntity
import com.vineyard.aivideostudio.data.local.database.entity.CommentaryEntity
import com.vineyard.aivideostudio.data.local.database.entity.MediaArtifactEntity
import com.vineyard.aivideostudio.data.local.database.entity.ModelConfigurationEntity
import com.vineyard.aivideostudio.data.local.database.entity.PipelineStepEntity
import com.vineyard.aivideostudio.data.local.database.entity.QaResultEntity
import com.vineyard.aivideostudio.data.local.database.entity.TimelineSegmentEntity
import com.vineyard.aivideostudio.data.local.database.entity.TranscriptSegmentEntity
import com.vineyard.aivideostudio.data.local.database.entity.VoiceEntity
import com.vineyard.aivideostudio.data.local.database.entity.PersistentLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PipelineStepDao {
    @Query("SELECT * FROM pipeline_steps WHERE projectId = :projectId ORDER BY startTime ASC")
    fun getStepsForProject(projectId: String): Flow<List<PipelineStepEntity>>

    @Query("SELECT * FROM pipeline_steps WHERE projectId = :projectId AND stage = :stage LIMIT 1")
    suspend fun getStep(projectId: String, stage: PipelineStatus): PipelineStepEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(step: PipelineStepEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSteps(steps: List<PipelineStepEntity>)

    @Query("DELETE FROM pipeline_steps WHERE projectId = :projectId")
    suspend fun deleteStepsForProject(projectId: String)
}

@Dao
interface MediaArtifactDao {
    @Query("SELECT * FROM media_artifacts WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun getArtifactsForProject(projectId: String): Flow<List<MediaArtifactEntity>>

    @Query("SELECT * FROM media_artifacts WHERE projectId = :projectId AND stage = :stage LIMIT 1")
    suspend fun getArtifactByStage(projectId: String, stage: PipelineStatus): MediaArtifactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtifact(artifact: MediaArtifactEntity)

    @Query("DELETE FROM media_artifacts WHERE projectId = :projectId")
    suspend fun deleteArtifactsForProject(projectId: String)
}

@Dao
interface TimelineSegmentDao {
    @Query("SELECT * FROM timeline_segments WHERE projectId = :projectId ORDER BY currentStart ASC")
    fun getSegmentsForProject(projectId: String): Flow<List<TimelineSegmentEntity>>

    @Query("SELECT * FROM timeline_segments WHERE projectId = :projectId ORDER BY currentStart ASC")
    suspend fun getSegmentsList(projectId: String): List<TimelineSegmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<TimelineSegmentEntity>)

    @Query("DELETE FROM timeline_segments WHERE projectId = :projectId")
    suspend fun deleteSegmentsForProject(projectId: String)
}

@Dao
interface TranscriptSegmentDao {
    @Query("SELECT * FROM transcript_segments WHERE projectId = :projectId ORDER BY start ASC")
    fun getTranscriptForProject(projectId: String): Flow<List<TranscriptSegmentEntity>>

    @Query("SELECT * FROM transcript_segments WHERE projectId = :projectId ORDER BY start ASC")
    suspend fun getTranscriptList(projectId: String): List<TranscriptSegmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscript(segments: List<TranscriptSegmentEntity>)

    @Query("DELETE FROM transcript_segments WHERE projectId = :projectId")
    suspend fun deleteTranscriptForProject(projectId: String)
}

@Dao
interface CaptionDao {
    @Query("SELECT * FROM captions WHERE projectId = :projectId ORDER BY start ASC")
    fun getCaptionsForProject(projectId: String): Flow<List<CaptionEntity>>

    @Query("SELECT * FROM captions WHERE projectId = :projectId ORDER BY start ASC")
    suspend fun getCaptionsList(projectId: String): List<CaptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCaptions(captions: List<CaptionEntity>)

    @Query("DELETE FROM captions WHERE projectId = :projectId")
    suspend fun deleteCaptionsForProject(projectId: String)
}

@Dao
interface CommentaryDao {
    @Query("SELECT * FROM commentary_segments WHERE projectId = :projectId ORDER BY start ASC")
    fun getCommentaryForProject(projectId: String): Flow<List<CommentaryEntity>>

    @Query("SELECT * FROM commentary_segments WHERE projectId = :projectId ORDER BY start ASC")
    suspend fun getCommentaryList(projectId: String): List<CommentaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommentary(segments: List<CommentaryEntity>)

    @Query("DELETE FROM commentary_segments WHERE projectId = :projectId")
    suspend fun deleteCommentaryForProject(projectId: String)
}

@Dao
interface AiRequestDao {
    @Query("SELECT * FROM ai_requests ORDER BY timestamp DESC")
    fun getAllRequests(): Flow<List<AiRequestEntity>>

    @Query("SELECT * FROM ai_requests WHERE projectId = :projectId ORDER BY timestamp DESC")
    fun getRequestsForProject(projectId: String): Flow<List<AiRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: AiRequestEntity)
}

@Dao
interface QaResultDao {
    @Query("SELECT * FROM qa_results WHERE projectId = :projectId ORDER BY timestamp DESC")
    fun getQaResultsForProject(projectId: String): Flow<List<QaResultEntity>>

    @Query("SELECT * FROM qa_results WHERE projectId = :projectId AND stage = :stage ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestQaForStage(projectId: String, stage: PipelineStatus): QaResultEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQaResult(qaResult: QaResultEntity)
}

@Dao
interface ModelConfigurationDao {
    @Query("SELECT * FROM model_configurations")
    fun getAllConfigurations(): Flow<List<ModelConfigurationEntity>>

    @Query("SELECT * FROM model_configurations WHERE purpose = :purpose LIMIT 1")
    suspend fun getConfiguration(purpose: String): ModelConfigurationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setConfiguration(config: ModelConfigurationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setAllConfigurations(configs: List<ModelConfigurationEntity>)
}

@Dao
interface VoiceDao {
    @Query("SELECT * FROM voices ORDER BY name ASC")
    fun getAllVoices(): Flow<List<VoiceEntity>>

    @Query("SELECT * FROM voices WHERE id = :id LIMIT 1")
    suspend fun getVoiceById(id: String): VoiceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoice(voice: VoiceEntity)

    @Query("DELETE FROM voices WHERE id = :id")
    suspend fun deleteVoiceById(id: String)
}

@Dao
interface PersistentLogDao {
    @Query("SELECT * FROM persistent_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<PersistentLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: PersistentLogEntity): Long

    @Query("DELETE FROM persistent_logs")
    suspend fun clearAllLogs()

    @Query("SELECT COUNT(*) FROM persistent_logs WHERE severity = 'ERROR' OR severity = 'CRASH'")
    fun getErrorCount(): Flow<Int>
}
