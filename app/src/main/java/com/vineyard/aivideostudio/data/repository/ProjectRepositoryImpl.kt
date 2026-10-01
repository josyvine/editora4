package com.vineyard.aivideostudio.data.repository

import com.vineyard.aivideostudio.core.model.Caption
import com.vineyard.aivideostudio.core.model.CommentarySegment
import com.vineyard.aivideostudio.core.model.MediaArtifact
import com.vineyard.aivideostudio.core.model.PipelineStatus
import com.vineyard.aivideostudio.core.model.PipelineStep
import com.vineyard.aivideostudio.core.model.Project
import com.vineyard.aivideostudio.core.model.QaResult
import com.vineyard.aivideostudio.core.model.TimelineMap
import com.vineyard.aivideostudio.core.model.TimelineSegment
import com.vineyard.aivideostudio.core.model.TranscriptSegment
import com.vineyard.aivideostudio.core.model.VideoMetadata
import com.vineyard.aivideostudio.core.util.JsonUtils
import com.vineyard.aivideostudio.data.local.database.AppDatabase
import com.vineyard.aivideostudio.data.local.database.entity.CaptionEntity
import com.vineyard.aivideostudio.data.local.database.entity.CommentaryEntity
import com.vineyard.aivideostudio.data.local.database.entity.MediaArtifactEntity
import com.vineyard.aivideostudio.data.local.database.entity.PipelineStepEntity
import com.vineyard.aivideostudio.data.local.database.entity.ProjectEntity
import com.vineyard.aivideostudio.data.local.database.entity.QaResultEntity
import com.vineyard.aivideostudio.data.local.database.entity.TimelineSegmentEntity
import com.vineyard.aivideostudio.data.local.database.entity.TranscriptSegmentEntity
import com.vineyard.aivideostudio.project.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProjectRepositoryImpl(
    private val database: AppDatabase
) : ProjectRepository {

    private val projectDao = database.projectDao()
    private val stepDao = database.pipelineStepDao()
    private val artifactDao = database.mediaArtifactDao()
    private val timelineDao = database.timelineSegmentDao()
    private val transcriptDao = database.transcriptSegmentDao()
    private val captionDao = database.captionDao()
    private val commentaryDao = database.commentaryDao()
    private val qaDao = database.qaResultDao()

    override fun getAllProjects(): Flow<List<Project>> {
        return projectDao.getAllProjects().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getProjectByIdFlow(id: String): Flow<Project?> {
        return projectDao.getProjectByIdFlow(id).map { it?.toDomain() }
    }

    override suspend fun getProjectById(id: String): Project? {
        return projectDao.getProjectById(id)?.toDomain()
    }

    override suspend fun saveProject(project: Project) {
        projectDao.insertProject(project.toEntity())
    }

    override suspend fun updateProjectStatus(id: String, status: PipelineStatus, stage: PipelineStatus) {
        projectDao.updateProjectStatus(id, status, stage)
    }

    override suspend fun updateCurrentVideoUri(id: String, uri: String) {
        projectDao.updateCurrentVideoUri(id, uri)
    }

    override suspend fun markCompleted(id: String, finalUri: String) {
        projectDao.markProjectCompleted(id, finalUri)
    }

    override suspend fun markFailed(id: String, error: String) {
        projectDao.markProjectFailed(id, error)
    }

    override suspend fun deleteProject(id: String) {
        projectDao.deleteProjectById(id)
    }

    override fun getSteps(projectId: String): Flow<List<PipelineStep>> {
        return stepDao.getStepsForProject(projectId).map { list ->
            list.map {
                PipelineStep(
                    id = it.id,
                    projectId = it.projectId,
                    stage = it.stage,
                    status = it.status,
                    retryCount = it.retryCount,
                    maxRetries = it.maxRetries,
                    inputArtifactUri = it.inputArtifactUri,
                    outputArtifactUri = it.outputArtifactUri,
                    message = it.message,
                    errorMessage = it.errorMessage,
                    qaVerdict = it.qaVerdict,
                    startTime = it.startTime,
                    endTime = it.endTime
                )
            }
        }
    }

    override suspend fun recordStep(step: PipelineStep) {
        stepDao.insertOrUpdate(
            PipelineStepEntity(
                id = step.id,
                projectId = step.projectId,
                stage = step.stage,
                status = step.status,
                retryCount = step.retryCount,
                maxRetries = step.maxRetries,
                inputArtifactUri = step.inputArtifactUri,
                outputArtifactUri = step.outputArtifactUri,
                message = step.message,
                errorMessage = step.errorMessage,
                qaVerdict = step.qaVerdict,
                startTime = step.startTime,
                endTime = step.endTime
            )
        )
    }

    override fun getArtifacts(projectId: String): Flow<List<MediaArtifact>> {
        return artifactDao.getArtifactsForProject(projectId).map { list ->
            list.map {
                MediaArtifact(
                    id = it.id,
                    projectId = it.projectId,
                    stage = it.stage,
                    type = it.type,
                    fileUri = it.fileUri,
                    filePath = it.filePath,
                    mimeType = it.mimeType,
                    sizeBytes = it.sizeBytes,
                    durationSeconds = it.durationSeconds,
                    createdAt = it.createdAt
                )
            }
        }
    }

    override suspend fun recordArtifact(artifact: MediaArtifact) {
        artifactDao.insertArtifact(
            MediaArtifactEntity(
                id = artifact.id,
                projectId = artifact.projectId,
                stage = artifact.stage,
                type = artifact.type,
                fileUri = artifact.fileUri,
                filePath = artifact.filePath,
                mimeType = artifact.mimeType,
                sizeBytes = artifact.sizeBytes,
                durationSeconds = artifact.durationSeconds,
                createdAt = artifact.createdAt
            )
        )
    }

    override fun getTimelineSegments(projectId: String): Flow<List<TimelineSegment>> {
        return timelineDao.getSegmentsForProject(projectId).map { list ->
            list.map {
                TimelineSegment(
                    id = it.id,
                    projectId = it.projectId,
                    originalStart = it.originalStart,
                    originalEnd = it.originalEnd,
                    currentStart = it.currentStart,
                    currentEnd = it.currentEnd,
                    isRetained = it.isRetained,
                    stageApplied = it.stageApplied
                )
            }
        }
    }

    override suspend fun saveTimelineMap(projectId: String, timelineMap: TimelineMap) {
        val entities = timelineMap.segments.map {
            TimelineSegmentEntity(
                id = it.id,
                projectId = it.projectId,
                originalStart = it.originalStart,
                originalEnd = it.originalEnd,
                currentStart = it.currentStart,
                currentEnd = it.currentEnd,
                isRetained = it.isRetained,
                stageApplied = it.stageApplied
            )
        }
        timelineDao.deleteSegmentsForProject(projectId)
        timelineDao.insertSegments(entities)
    }

    override fun getTranscript(projectId: String): Flow<List<TranscriptSegment>> {
        return transcriptDao.getTranscriptForProject(projectId).map { list ->
            list.map {
                TranscriptSegment(it.id, it.projectId, it.start, it.end, it.text, it.speaker)
            }
        }
    }

    override suspend fun saveTranscript(projectId: String, segments: List<TranscriptSegment>) {
        transcriptDao.deleteTranscriptForProject(projectId)
        transcriptDao.insertTranscript(
            segments.map { TranscriptSegmentEntity(it.id, it.projectId, it.start, it.end, it.text, it.speaker) }
        )
    }

    override fun getCaptions(projectId: String): Flow<List<Caption>> {
        return captionDao.getCaptionsForProject(projectId).map { list ->
            list.map {
                Caption(it.id, it.projectId, it.text, it.start, it.end, it.x, it.y, it.fontSizeSp, it.fontColorHex, it.backgroundColorHex, it.style)
            }
        }
    }

    override suspend fun saveCaptions(projectId: String, captions: List<Caption>) {
        captionDao.deleteCaptionsForProject(projectId)
        captionDao.insertCaptions(
            captions.map { CaptionEntity(it.id, it.projectId, it.text, it.start, it.end, it.x, it.y, it.fontSizeSp, it.fontColorHex, it.backgroundColorHex, it.style) }
        )
    }

    override fun getCommentary(projectId: String): Flow<List<CommentarySegment>> {
        return commentaryDao.getCommentaryForProject(projectId).map { list ->
            list.map {
                CommentarySegment(it.id, it.projectId, it.start, it.end, it.text, it.audioArtifactUri, it.voiceId)
            }
        }
    }

    override suspend fun saveCommentary(projectId: String, commentary: List<CommentarySegment>) {
        commentaryDao.deleteCommentaryForProject(projectId)
        commentaryDao.insertCommentary(
            commentary.map { CommentaryEntity(it.id, it.projectId, it.start, it.end, it.text, it.audioArtifactUri, it.voiceId) }
        )
    }

    override fun getQaResults(projectId: String): Flow<List<QaResult>> {
        return qaDao.getQaResultsForProject(projectId).map { list ->
            list.map {
                val corrections: List<String> = try {
                    JsonUtils.fromJson(it.correctionsJson) ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }
                QaResult(
                    id = it.id,
                    projectId = it.projectId,
                    stage = it.stage,
                    verdict = it.verdict,
                    feedback = it.feedback,
                    corrections = corrections,
                    confidence = it.confidence,
                    timestamp = it.timestamp
                )
            }
        }
    }

    override suspend fun recordQaResult(qaResult: QaResult) {
        qaDao.insertQaResult(
            QaResultEntity(
                id = qaResult.id,
                projectId = qaResult.projectId,
                stage = qaResult.stage,
                verdict = qaResult.verdict,
                feedback = qaResult.feedback,
                correctionsJson = JsonUtils.toJson(qaResult.corrections),
                confidence = qaResult.confidence,
                timestamp = qaResult.timestamp
            )
        )
    }

    private fun ProjectEntity.toDomain(): Project {
        return Project(
            id = id,
            name = name,
            sourceUri = sourceUri,
            sourcePath = sourcePath,
            currentVideoUri = currentVideoUri,
            sourceYoutubeUrl = sourceYoutubeUrl,
            masterRecipeJson = masterRecipeJson,
            finalVideoUri = finalVideoUri,
            thumbnailUri = thumbnailUri,
            metadata = VideoMetadata(
                durationSeconds = durationSeconds,
                width = width,
                height = height,
                rotationDegrees = rotationDegrees,
                frameRate = frameRate,
                bitrate = bitrate,
                videoCodec = videoCodec,
                audioCodec = audioCodec,
                fileSize = fileSize
            ),
            currentStage = currentStage,
            status = status,
            sourceAnalysisJson = sourceAnalysisJson,
            timelineMapJson = timelineMapJson,
            targetAspectRatio = targetAspectRatio,
            createdAt = createdAt,
            updatedAt = updatedAt,
            lastError = lastError
        )
    }

    private fun Project.toEntity(): ProjectEntity {
        return ProjectEntity(
            id = id,
            name = name,
            sourceUri = sourceUri,
            sourcePath = sourcePath,
            currentVideoUri = currentVideoUri,
            sourceYoutubeUrl = sourceYoutubeUrl,
            masterRecipeJson = masterRecipeJson,
            finalVideoUri = finalVideoUri,
            thumbnailUri = thumbnailUri,
            durationSeconds = metadata.durationSeconds,
            width = metadata.width,
            height = metadata.height,
            rotationDegrees = metadata.rotationDegrees,
            frameRate = metadata.frameRate,
            bitrate = metadata.bitrate,
            videoCodec = metadata.videoCodec,
            audioCodec = metadata.audioCodec,
            fileSize = metadata.fileSize,
            currentStage = currentStage,
            status = status,
            sourceAnalysisJson = sourceAnalysisJson,
            timelineMapJson = timelineMapJson,
            targetAspectRatio = targetAspectRatio,
            createdAt = createdAt,
            updatedAt = updatedAt,
            lastError = lastError
        )
    }
}