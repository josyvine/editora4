package com.vineyard.aivideostudio.ai.gemini

import com.vineyard.aivideostudio.core.model.PipelineStatus
import com.vineyard.aivideostudio.core.result.AppError
import com.vineyard.aivideostudio.core.result.AppResult
import com.vineyard.aivideostudio.data.local.database.dao.AiRequestDao
import com.vineyard.aivideostudio.data.local.database.entity.AiRequestEntity
import com.vineyard.aivideostudio.data.preferences.GeminiPreferences
import com.vineyard.aivideostudio.data.remote.gemini.ContentDto
import com.vineyard.aivideostudio.data.remote.gemini.FileDataDto
import com.vineyard.aivideostudio.data.remote.gemini.GenerateContentRequest
import com.vineyard.aivideostudio.data.remote.gemini.GenerationConfigDto
import com.vineyard.aivideostudio.data.remote.gemini.GeminiApiService
import com.vineyard.aivideostudio.data.remote.gemini.PartDto
import com.vineyard.aivideostudio.data.remote.network.ApiErrorMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class GeminiClient(
    private val apiService: GeminiApiService,
    private val preferences: GeminiPreferences,
    private val aiRequestDao: AiRequestDao
) {

    suspend fun <T> generateStructured(
        projectId: String,
        stage: PipelineStatus,
        modelId: String,
        prompt: String,
        mediaUri: String? = null,
        mediaMimeType: String = "video/mp4",
        parser: (String) -> T?
    ): AppResult<T> = withContext(Dispatchers.IO) {
        val apiKey = preferences.getApiKey()
        if (apiKey.isBlank()) {
            return@withContext AppResult.Error(AppError.ApiKeyError("Please enter your Gemini API key in Settings"))
        }

        val cleanModel = if (modelId.startsWith("models/")) modelId else "models/$modelId"
        
        // Build multimodal parts: Text instruction + optional Video/Audio file_data
        val parts = mutableListOf<PartDto>()
        parts.add(PartDto(text = prompt))
        
        if (!mediaUri.isNullOrBlank()) {
            parts.add(
                PartDto(
                    fileData = FileDataDto(
                        fileUri = mediaUri,
                        mimeType = mediaMimeType
                    )
                )
            )
        }

        val request = GenerateContentRequest(
            contents = listOf(
                ContentDto(
                    role = "user",
                    parts = parts
                )
            ),
            generationConfig = GenerationConfigDto(
                responseMimeType = "application/json",
                temperature = 0.2f
            )
        )

        val startTime = System.currentTimeMillis()
        val requestId = "req_${UUID.randomUUID().toString().take(8)}"
        var rawResponseText: String? = null
        var isSuccess = false
        var errorMessage: String? = null

        try {
            val response = apiService.generateContent(
                model = cleanModel,
                apiKey = apiKey,
                request = request
            )

            val latency = System.currentTimeMillis() - startTime

            if (response.isSuccessful) {
                val candidate = response.body()?.candidates?.firstOrNull()
                val text = candidate?.content?.parts?.firstOrNull()?.text
                rawResponseText = text

                if (text.isNullOrBlank()) {
                    errorMessage = "Empty response from Gemini model"
                    logRequest(requestId, projectId, stage, cleanModel, prompt, null, latency, false, errorMessage)
                    return@withContext AppResult.Error(AppError.ValidationError(errorMessage))
                }

                val parsed = parser(text)
                if (parsed != null) {
                    isSuccess = true
                    logRequest(requestId, projectId, stage, cleanModel, prompt, text, latency, true, null)
                    return@withContext AppResult.Success(parsed)
                } else {
                    errorMessage = "Failed to parse structured JSON from model: ${text.take(200)}"
                    logRequest(requestId, projectId, stage, cleanModel, prompt, text, latency, false, errorMessage)
                    return@withContext AppResult.Error(AppError.ValidationError(errorMessage))
                }
            } else {
                val errBody = response.errorBody()?.string()
                errorMessage = "API error ${response.code()}: $errBody"
                val appError = ApiErrorMapper.mapHttpError(response.code(), errBody)
                logRequest(requestId, projectId, stage, cleanModel, prompt, null, latency, false, errorMessage)
                return@withContext AppResult.Error(appError)
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            errorMessage = e.message ?: "Unknown network error"
            logRequest(requestId, projectId, stage, cleanModel, prompt, null, latency, false, errorMessage)
            return@withContext AppResult.Error(AppError.NetworkError("Network request failed: $errorMessage", e))
        }
    }

    private suspend fun logRequest(
        id: String,
        projectId: String,
        stage: PipelineStatus,
        model: String,
        prompt: String,
        response: String?,
        latencyMs: Long,
        isSuccess: Boolean,
        error: String?
    ) {
        try {
            aiRequestDao.insertRequest(
                AiRequestEntity(
                    id = id,
                    projectId = projectId,
                    stage = stage,
                    modelId = model,
                    promptSummary = prompt.take(200).replace("\n", " "),
                    rawResponse = response?.take(1000),
                    latencyMs = latencyMs,
                    isSuccess = isSuccess,
                    errorMessage = error,
                    timestamp = System.currentTimeMillis()
                )
            )
        } catch (_: Exception) {}
    }
}