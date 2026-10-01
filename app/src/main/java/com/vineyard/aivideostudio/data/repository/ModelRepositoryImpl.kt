package com.vineyard.aivideostudio.data.repository

import com.vineyard.aivideostudio.ai.classifier.ModelCapabilityClassifier
import com.vineyard.aivideostudio.ai.model.ModelInfo
import com.vineyard.aivideostudio.ai.model.ModelPurpose
import com.vineyard.aivideostudio.core.common.AppConstants
import com.vineyard.aivideostudio.core.result.AppError
import com.vineyard.aivideostudio.core.result.AppResult
import com.vineyard.aivideostudio.core.util.JsonUtils
import com.vineyard.aivideostudio.data.local.database.dao.ModelConfigurationDao
import com.vineyard.aivideostudio.data.local.database.entity.ModelConfigurationEntity
import com.vineyard.aivideostudio.data.preferences.GeminiPreferences
import com.vineyard.aivideostudio.data.remote.gemini.GeminiApiService
import com.vineyard.aivideostudio.data.remote.network.ApiErrorMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class ModelRepositoryImpl(
    private val apiService: GeminiApiService,
    private val preferences: GeminiPreferences,
    private val modelDao: ModelConfigurationDao
) {
    private val _cachedModels = MutableStateFlow<List<ModelInfo>>(emptyList())
    val cachedModels: StateFlow<List<ModelInfo>> = _cachedModels.asStateFlow()

    suspend fun fetchAvailableModels(): AppResult<List<ModelInfo>> = withContext(Dispatchers.IO) {
        val apiKey = preferences.getApiKey()
        if (apiKey.isBlank()) {
            return@withContext AppResult.Error(AppError.ApiKeyError("Please enter your Gemini API key in Settings"))
        }

        try {
            val allFetchedModels = mutableListOf<ModelInfo>()
            var pageToken: String? = null

            do {
                val response = apiService.listModels(apiKey = apiKey, pageSize = 50, pageToken = pageToken)
                if (!response.isSuccessful) {
                    val err = response.errorBody()?.string()
                    return@withContext AppResult.Error(ApiErrorMapper.mapHttpError(response.code(), err))
                }

                val body = response.body()
                val models = body?.models ?: emptyList()
                val classified = models.map { ModelCapabilityClassifier.classify(it) }
                allFetchedModels.addAll(classified)
                pageToken = body?.nextPageToken
            } while (!pageToken.isNullOrBlank())

            _cachedModels.value = allFetchedModels
            preferences.setLastModelSync(System.currentTimeMillis())

            // Initialize default purpose assignments if none exist
            ensureDefaultAssignments(allFetchedModels)

            AppResult.Success(allFetchedModels)
        } catch (e: Exception) {
            AppResult.Error(AppError.NetworkError("Failed to fetch models: ${e.message}", e))
        }
    }

    suspend fun getSelectedModelForPurpose(purpose: ModelPurpose): String = withContext(Dispatchers.IO) {
        val config = modelDao.getConfiguration(purpose.name)
        if (config != null && config.modelId.isNotBlank()) {
            return@withContext config.modelId
        }

        // Fallbacks
        return@withContext when (purpose) {
            ModelPurpose.VIDEO_ANALYSIS -> AppConstants.DEFAULT_ANALYSIS_MODEL
            ModelPurpose.AUDIO_TRANSCRIPTION -> AppConstants.DEFAULT_TRANSCRIPTION_MODEL
            ModelPurpose.EDITING_DIRECTOR -> AppConstants.DEFAULT_DIRECTOR_MODEL
            ModelPurpose.COMMENTARY -> AppConstants.DEFAULT_COMMENTARY_MODEL
            ModelPurpose.TEXT_TO_SPEECH -> AppConstants.DEFAULT_TTS_MODEL
            ModelPurpose.LIVE_VOICE -> AppConstants.DEFAULT_LIVE_MODEL
        }
    }

    suspend fun setSelectedModelForPurpose(purpose: ModelPurpose, modelInfo: ModelInfo) = withContext(Dispatchers.IO) {
        modelDao.setConfiguration(
            ModelConfigurationEntity(
                purpose = purpose.name,
                modelId = modelInfo.id,
                displayName = modelInfo.displayName,
                description = modelInfo.description,
                inputTokenLimit = modelInfo.inputTokenLimit,
                outputTokenLimit = modelInfo.outputTokenLimit,
                supportedCapabilitiesJson = JsonUtils.toJson(modelInfo.capabilities),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun setSelectedModelForPurpose(purpose: ModelPurpose, modelId: String) = withContext(Dispatchers.IO) {
        val cleanId = if (modelId.startsWith("models/")) modelId else "models/$modelId"
        val cached = _cachedModels.value.firstOrNull { it.id == cleanId || it.id == modelId }

        if (cached != null) {
            setSelectedModelForPurpose(purpose, cached)
        } else {
            modelDao.setConfiguration(
                ModelConfigurationEntity(
                    purpose = purpose.name,
                    modelId = cleanId,
                    displayName = modelId.substringAfter("models/"),
                    description = "User selected model",
                    inputTokenLimit = 0,
                    outputTokenLimit = 0,
                    supportedCapabilitiesJson = "{}",
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun getAllConfigurations(): Flow<List<ModelConfigurationEntity>> {
        return modelDao.getAllConfigurations()
    }

    private suspend fun ensureDefaultAssignments(available: List<ModelInfo>) {
        for (purpose in ModelPurpose.values()) {
            val existing = modelDao.getConfiguration(purpose.name)
            if (existing == null) {
                val candidate = available.firstOrNull { it.supportedPurposes.contains(purpose) }
                    ?: available.firstOrNull { it.capabilities.supportsText }
                if (candidate != null) {
                    setSelectedModelForPurpose(purpose, candidate)
                }
            }
        }
    }
}