package com.vineyard.aivideostudio.ui.screens.processing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vineyard.aivideostudio.core.model.PipelineStep
import com.vineyard.aivideostudio.core.model.Project
import com.vineyard.aivideostudio.core.model.QaResult
import com.vineyard.aivideostudio.processing.controller.ProcessingController
import com.vineyard.aivideostudio.processing.state.ProcessingUiState
import com.vineyard.aivideostudio.project.repository.ProjectRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ProcessingViewModel(
    private val projectId: String,
    private val controller: ProcessingController,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    init {
        controller.loadProject(projectId)
    }

    val uiState: StateFlow<ProcessingUiState> = combine(
        controller.uiState,
        projectRepository.getProjectByIdFlow(projectId),
        projectRepository.getSteps(projectId),
        projectRepository.getQaResults(projectId)
    ) { ctrlState, proj, steps, qas ->
        ctrlState.copy(
            project = proj ?: ctrlState.project,
            steps = steps,
            latestQa = qas.firstOrNull()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProcessingUiState()
    )

    fun startProcessing() {
        controller.startProcessing(projectId)
    }

    fun cancelProcessing() {
        controller.cancelProcessing()
    }
}
