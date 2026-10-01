package com.vineyard.aivideostudio.project.repository

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
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
    fun getAllProjects(): Flow<List<Project>>
    fun getProjectByIdFlow(id: String): Flow<Project?>
    suspend fun getProjectById(id: String): Project?
    suspend fun saveProject(project: Project)
    suspend fun updateProjectStatus(id: String, status: PipelineStatus, stage: PipelineStatus)
    suspend fun updateCurrentVideoUri(id: String, uri: String)
    suspend fun markCompleted(id: String, finalUri: String)
    suspend fun markFailed(id: String, error: String)
    suspend fun deleteProject(id: String)

    fun getSteps(projectId: String): Flow<List<PipelineStep>>
    suspend fun recordStep(step: PipelineStep)

    fun getArtifacts(projectId: String): Flow<List<MediaArtifact>>
    suspend fun recordArtifact(artifact: MediaArtifact)

    fun getTimelineSegments(projectId: String): Flow<List<TimelineSegment>>
    suspend fun saveTimelineMap(projectId: String, timelineMap: TimelineMap)

    fun getTranscript(projectId: String): Flow<List<TranscriptSegment>>
    suspend fun saveTranscript(projectId: String, segments: List<TranscriptSegment>)

    fun getCaptions(projectId: String): Flow<List<Caption>>
    suspend fun saveCaptions(projectId: String, captions: List<Caption>)

    fun getCommentary(projectId: String): Flow<List<CommentarySegment>>
    suspend fun saveCommentary(projectId: String, commentary: List<CommentarySegment>)

    fun getQaResults(projectId: String): Flow<List<QaResult>>
    suspend fun recordQaResult(qaResult: QaResult)
}
