package com.vineyard.aivideostudio.processing.controller

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.vineyard.aivideostudio.core.model.PipelineStatus
import com.vineyard.aivideostudio.core.model.Project
import com.vineyard.aivideostudio.domain.pipeline.VideoProcessingPipeline
import com.vineyard.aivideostudio.processing.logger.ProcessingLogger
import com.vineyard.aivideostudio.processing.state.ProcessingUiState
import com.vineyard.aivideostudio.processing.worker.VideoProcessingForegroundService
import com.vineyard.aivideostudio.project.repository.ProjectRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProcessingController(
    private val context: Context,
    private val pipeline: VideoProcessingPipeline,
    private val projectRepository: ProjectRepository,
    private val logger: ProcessingLogger
) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var activeJob: Job? = null

    private val _uiState = MutableStateFlow(ProcessingUiState())
    val uiState: StateFlow<ProcessingUiState> = _uiState.asStateFlow()

    fun loadProject(projectId: String) {
        scope.launch {
            val project = projectRepository.getProjectById(projectId)
            val isScriptMode = !project?.masterRecipeJson.isNullOrBlank()

            val initialMsg = when {
                isScriptMode -> "Ready to execute Master Recipe Script (Direct Mode)."
                !project?.sourceYoutubeUrl.isNullOrBlank() ->
                    "Ready to edit. Gemini will first analyze the YouTube source video (${project.sourceYoutubeUrl}) and compare with your downloaded local video."
                else -> "Ready to start AI video production"
            }

            _uiState.value = _uiState.value.copy(
                project = project,
                currentStage = project?.currentStage ?: PipelineStatus.IDLE,
                isRunning = false,
                aiMessage = initialMsg
            )
        }
    }

    fun startProcessing(projectId: String) {
        if (_uiState.value.isRunning) return

        val project = _uiState.value.project
        val isScriptMode = !project?.masterRecipeJson.isNullOrBlank()
        val startMessage = if (isScriptMode) {
            "Starting Master Recipe Script execution..."
        } else {
            "Starting sequential AI video editing pipeline..."
        }

        _uiState.value = _uiState.value.copy(
            isRunning = true,
            isCancelled = false,
            errorMessage = null,
            aiMessage = startMessage
        )

        // Start Foreground Service for background resilience
        try {
            val intent = Intent(context, VideoProcessingForegroundService::class.java).apply {
                putExtra("projectId", projectId)
            }
            ContextCompat.startForegroundService(context, intent)
        } catch (_: Exception) {}

        activeJob = scope.launch {
            val result = pipeline.executePipeline(projectId) { stage, message ->
                scope.launch(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        currentStage = stage,
                        aiMessage = message,
                        currentOperationDetail = "Current stage: ${stage.name}"
                    )
                }
            }

            when (result) {
                is com.vineyard.aivideostudio.core.result.AppResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        project = result.data,
                        currentStage = PipelineStatus.COMPLETED,
                        isRunning = false,
                        aiMessage = "Video production and QA complete!"
                    )
                }
                is com.vineyard.aivideostudio.core.result.AppResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isRunning = false,
                        errorMessage = result.error.message,
                        aiMessage = "Pipeline paused: ${result.error.message}"
                    )
                }
            }

            try {
                context.stopService(Intent(context, VideoProcessingForegroundService::class.java))
            } catch (_: Exception) {}
        }
    }

    fun cancelProcessing() {
        activeJob?.cancel()
        activeJob = null
        _uiState.value = _uiState.value.copy(
            isRunning = false,
            isCancelled = true,
            aiMessage = "Processing cancelled by user"
        )
        try {
            context.stopService(Intent(context, VideoProcessingForegroundService::class.java))
        } catch (_: Exception) {}
    }
}