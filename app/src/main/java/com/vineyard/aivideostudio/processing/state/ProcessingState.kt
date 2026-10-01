package com.vineyard.aivideostudio.processing.state

import com.vineyard.aivideostudio.core.model.PipelineStatus
import com.vineyard.aivideostudio.core.model.PipelineStep
import com.vineyard.aivideostudio.core.model.Project
import com.vineyard.aivideostudio.core.model.QaResult

data class ProcessingUiState(
    val project: Project? = null,
    val currentStage: PipelineStatus = PipelineStatus.IDLE,
    val isRunning: Boolean = false,
    val isCancelled: Boolean = false,
    val steps: List<PipelineStep> = emptyList(),
    val latestQa: QaResult? = null,
    val aiMessage: String = "Ready to start AI video production",
    val currentOperationDetail: String = "",
    val retryCount: Int = 0,
    val progressFraction: Float = 0f,
    val errorMessage: String? = null
)
