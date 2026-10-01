package com.vineyard.aivideostudio.ui.screens.editor

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vineyard.aivideostudio.core.model.ArtifactType
import com.vineyard.aivideostudio.core.model.Caption
import com.vineyard.aivideostudio.core.model.CommentarySegment
import com.vineyard.aivideostudio.core.model.MediaArtifact
import com.vineyard.aivideostudio.core.model.PipelineStep
import com.vineyard.aivideostudio.core.model.Project
import com.vineyard.aivideostudio.core.model.QaResult
import com.vineyard.aivideostudio.core.model.TimelineSegment
import com.vineyard.aivideostudio.core.model.TranscriptSegment
import com.vineyard.aivideostudio.core.result.AppResult
import com.vineyard.aivideostudio.core.util.StorageUtils
import com.vineyard.aivideostudio.project.repository.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class EditorUiState(
    val project: Project? = null,
    val timelineSegments: List<TimelineSegment> = emptyList(),
    val transcriptSegments: List<TranscriptSegment> = emptyList(),
    val captions: List<Caption> = emptyList(),
    val commentary: List<CommentarySegment> = emptyList(),
    val steps: List<PipelineStep> = emptyList(),
    val artifacts: List<MediaArtifact> = emptyList(),
    val qaResults: List<QaResult> = emptyList(),
    val activeCommentaryAudioUri: String? = null,
    val isOriginalAudioMuted: Boolean = true,
    val totalTimeTrimmedSeconds: Double = 0.0,
    val isCopyrightTransformed: Boolean = true,
    val isExporting: Boolean = false,
    val exportSuccessMessage: String? = null,
    val exportErrorMessage: String? = null,
    val savedPublicUri: String? = null
)

class EditorViewModel(
    private val projectId: String,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _isOriginalAudioMuted = MutableStateFlow(true)
    private val _isExporting = MutableStateFlow(false)
    private val _exportSuccessMessage = MutableStateFlow<String?>(null)
    private val _exportErrorMessage = MutableStateFlow<String?>(null)
    private val _savedPublicUri = MutableStateFlow<String?>(null)

    val uiState: StateFlow<EditorUiState> = combine(
        projectRepository.getProjectByIdFlow(projectId),
        projectRepository.getTimelineSegments(projectId),
        projectRepository.getTranscript(projectId),
        projectRepository.getCaptions(projectId),
        projectRepository.getCommentary(projectId),
        projectRepository.getSteps(projectId),
        projectRepository.getArtifacts(projectId),
        projectRepository.getQaResults(projectId),
        _isOriginalAudioMuted,
        _isExporting,
        _exportSuccessMessage,
        _exportErrorMessage
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val project = args[0] as? Project
        @Suppress("UNCHECKED_CAST")
        val timeline = (args[1] as? List<TimelineSegment>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val transcript = (args[2] as? List<TranscriptSegment>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val captions = (args[3] as? List<Caption>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val commentary = (args[4] as? List<CommentarySegment>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val steps = (args[5] as? List<PipelineStep>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val artifacts = (args[6] as? List<MediaArtifact>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val qaResults = (args[7] as? List<QaResult>) ?: emptyList()
        val isMuted = (args[8] as? Boolean) ?: true
        val isExporting = (args[9] as? Boolean) ?: false
        val exportSuccess = args[10] as? String
        val exportError = args[11] as? String

        // Extract synchronized commentary track
        val activeAudioUri = commentary.firstOrNull { !it.audioArtifactUri.isNullOrBlank() }?.audioArtifactUri
            ?: artifacts.firstOrNull { it.stage.name.contains("TTS") || it.stage.name.contains("COMMENTARY") }?.fileUri

        // Calculate total trimmed duration to show transformative editing impact
        val totalTrimmed = timeline.sumOf { seg: TimelineSegment ->
            (seg.originalEnd - seg.originalStart) - (seg.currentEnd - seg.currentStart)
        }.coerceAtLeast(0.0)

        EditorUiState(
            project = project,
            timelineSegments = timeline,
            transcriptSegments = transcript,
            captions = captions,
            commentary = commentary,
            steps = steps,
            artifacts = artifacts,
            qaResults = qaResults,
            activeCommentaryAudioUri = activeAudioUri,
            isOriginalAudioMuted = isMuted,
            totalTimeTrimmedSeconds = totalTrimmed,
            isCopyrightTransformed = true,
            isExporting = isExporting,
            exportSuccessMessage = exportSuccess,
            exportErrorMessage = exportError,
            savedPublicUri = _savedPublicUri.value
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EditorUiState()
    )

    fun toggleOriginalAudioMute() {
        _isOriginalAudioMuted.value = !_isOriginalAudioMuted.value
    }

    /**
     * Resolves the video file to be saved.
     * STRICT RULE: Strictly returns the finalized MP4 containing the burned TTS audio.
     * Never falls back to un-muxed raw source files.
     */
    private fun resolveCurrentVideoFile(state: EditorUiState): File? {
        // 1. Prioritize strictly project.finalVideoUri
        val finalUriStr = state.project?.finalVideoUri?.takeIf { it.isNotBlank() }
        if (finalUriStr != null) {
            val file = parseUriToFile(finalUriStr)
            if (file != null && file.exists() && file.length() > 0L) {
                return file
            }
        }

        // 2. Check media artifacts explicitly for FINAL_VIDEO
        val finalArtifact = state.artifacts.firstOrNull {
            it.type == ArtifactType.FINAL_VIDEO &&
            it.filePath.isNotBlank() &&
            File(it.filePath).exists() &&
            File(it.filePath).length() > 0L
        } ?: state.artifacts.firstOrNull {
            (it.stage.name.contains("EXPORT") || it.stage.name.contains("COMPLET")) &&
            it.filePath.isNotBlank() &&
            File(it.filePath).exists() &&
            File(it.filePath).length() > 0L
        }

        if (finalArtifact != null) {
            return File(finalArtifact.filePath)
        }

        // 3. If finalVideoUri is absent, reject export of raw video to prevent original audio leakage
        return null
    }

    private fun parseUriToFile(uriStr: String): File? {
        return try {
            if (uriStr.startsWith("file://")) {
                File(Uri.parse(uriStr).path ?: "")
            } else if (uriStr.startsWith("/")) {
                File(uriStr)
            } else {
                File(Uri.parse(uriStr).path ?: uriStr)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Saves the final transformed video directly to the device's public Gallery (Movies/Editora).
     */
    fun exportToGallery(context: Context) {
        val state = uiState.value
        val file = resolveCurrentVideoFile(state)
        if (file == null || !file.exists()) {
            _exportErrorMessage.value = "Final video with AI voiceover is not ready. Please wait for pipeline export to complete."
            return
        }

        viewModelScope.launch {
            _isExporting.value = true
            _exportErrorMessage.value = null
            _exportSuccessMessage.value = null

            val projectName = state.project?.name?.replace(Regex("[^a-zA-Z0-9._-]"), "_") ?: "Editora_Production"
            val result = withContext(Dispatchers.IO) {
                StorageUtils.saveVideoToGallery(
                    context = context.applicationContext,
                    sourceFile = file,
                    displayName = "$projectName.mp4"
                )
            }

            when (result) {
                is AppResult.Success -> {
                    _exportSuccessMessage.value = "Saved to Gallery (Movies/Editora)"
                    _savedPublicUri.value = result.data.toString()
                }
                is AppResult.Error -> {
                    _exportErrorMessage.value = "Failed saving to gallery: ${result.error.message}"
                }
            }
            _isExporting.value = false
        }
    }

    /**
     * Saves the final video to a custom folder selected via Storage Access Framework.
     */
    fun exportToCustomTree(context: Context, treeUri: Uri) {
        val state = uiState.value
        val file = resolveCurrentVideoFile(state)
        if (file == null || !file.exists()) {
            _exportErrorMessage.value = "Final video with AI voiceover is not ready. Please wait for pipeline export to complete."
            return
        }

        viewModelScope.launch {
            _isExporting.value = true
            _exportErrorMessage.value = null
            _exportSuccessMessage.value = null

            val projectName = state.project?.name?.replace(Regex("[^a-zA-Z0-9._-]"), "_") ?: "Editora_Production"
            val result = withContext(Dispatchers.IO) {
                StorageUtils.saveVideoToTreeUri(
                    context = context.applicationContext,
                    sourceFile = file,
                    treeUri = treeUri,
                    displayName = "$projectName.mp4"
                )
            }

            when (result) {
                is AppResult.Success -> {
                    _exportSuccessMessage.value = "Saved to selected folder"
                    _savedPublicUri.value = result.data.toString()
                }
                is AppResult.Error -> {
                    _exportErrorMessage.value = "Failed saving to folder: ${result.error.message}"
                }
            }
            _isExporting.value = false
        }
    }

    /**
     * Saves the final video to a file destination chosen via system file picker.
     */
    fun exportToDocumentUri(context: Context, targetUri: Uri) {
        val state = uiState.value
        val file = resolveCurrentVideoFile(state)
        if (file == null || !file.exists()) {
            _exportErrorMessage.value = "Final video with AI voiceover is not ready. Please wait for pipeline export to complete."
            return
        }

        viewModelScope.launch {
            _isExporting.value = true
            _exportErrorMessage.value = null
            _exportSuccessMessage.value = null

            val result = withContext(Dispatchers.IO) {
                StorageUtils.copyVideoToUri(
                    context = context.applicationContext,
                    sourceFile = file,
                    targetUri = targetUri
                )
            }

            when (result) {
                is AppResult.Success -> {
                    _exportSuccessMessage.value = "Video file saved successfully"
                    _savedPublicUri.value = result.data.toString()
                }
                is AppResult.Error -> {
                    _exportErrorMessage.value = "Failed writing video: ${result.error.message}"
                }
            }
            _isExporting.value = false
        }
    }

    fun clearExportStatus() {
        _exportSuccessMessage.value = null
        _exportErrorMessage.value = null
    }
}