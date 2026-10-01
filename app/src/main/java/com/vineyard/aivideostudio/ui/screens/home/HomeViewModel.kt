package com.vineyard.aivideostudio.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vineyard.aivideostudio.core.model.PipelineStatus
import com.vineyard.aivideostudio.core.model.Project
import com.vineyard.aivideostudio.data.preferences.GeminiPreferences
import com.vineyard.aivideostudio.data.repository.ModelRepositoryImpl
import com.vineyard.aivideostudio.project.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val projects: List<Project> = emptyList(),
    val recentProject: Project? = null,
    val hasApiKey: Boolean = false,
    val modelCount: Int = 0,
    val processingCount: Int = 0,
    val completedCount: Int = 0
)

class HomeViewModel(
    private val projectRepository: ProjectRepository,
    private val modelRepository: ModelRepositoryImpl,
    private val preferences: GeminiPreferences
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        projectRepository.getAllProjects(),
        modelRepository.cachedModels,
        preferences.apiKeyFlow
    ) { projects, models, apiKey ->
        HomeUiState(
            projects = projects,
            recentProject = projects.firstOrNull(),
            hasApiKey = apiKey.isNotBlank(),
            modelCount = models.size,
            processingCount = projects.count { it.status.isProcessing },
            completedCount = projects.count { it.status == PipelineStatus.COMPLETED }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )
}
