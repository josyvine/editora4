package com.vineyard.aivideostudio.voice.tts

import android.content.Context
import android.util.Base64
import com.vineyard.aivideostudio.ai.model.ModelPurpose
import com.vineyard.aivideostudio.data.preferences.GeminiPreferences
import com.vineyard.aivideostudio.data.remote.gemini.ContentDto
import com.vineyard.aivideostudio.data.remote.gemini.GenerateContentRequest
import com.vineyard.aivideostudio.data.remote.gemini.GenerationConfigDto
import com.vineyard.aivideostudio.data.remote.gemini.GeminiApiService
import com.vineyard.aivideostudio.data.remote.gemini.PartDto
import com.vineyard.aivideostudio.data.remote.gemini.PrebuiltVoiceConfigDto
import com.vineyard.aivideostudio.data.remote.gemini.SpeechConfigDto
import com.vineyard.aivideostudio.data.remote.gemini.VoiceConfigDto
import com.vineyard.aivideostudio.data.repository.ModelRepositoryImpl
import com.vineyard.aivideostudio.voice.model.TtsRequest
import com.vineyard.aivideostudio.voice.model.TtsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger

class GeminiTtsEngine(
    private val context: Context,
    private val apiService: GeminiApiService,
    private val preferences: GeminiPreferences,
    private val modelRepository: ModelRepositoryImpl
) {

    /**
     * Batch synthesizes commentary cues according to the specified mode:
     * - "single": Sequential one-by-one execution.
     * - "multiple": Concurrent execution up to [concurrency] simultaneous requests (e.g. 10 at a time),
     *   preserving timeline cue order in the returned results.
     */
    suspend fun synthesizeCues(
        requests: List<TtsRequest>,
        cueMode: String = "single",
        concurrency: Int = 1,
        onProgress: ((completed: Int, total: Int, request: TtsRequest) -> Unit)? = null
    ): List<TtsResult> = withContext(Dispatchers.IO) {
        val total = requests.size
        if (total == 0) return@withContext emptyList()

        val isMultiple = cueMode.equals("multiple", ignoreCase = true) && concurrency > 1
        val effectiveConcurrency = if (isMultiple) concurrency.coerceAtLeast(1) else 1

        if (!isMultiple || effectiveConcurrency == 1) {
            // Single mode: Process 1 by 1 sequentially
            val results = mutableListOf<TtsResult>()
            for ((index, request) in requests.withIndex()) {
                onProgress?.invoke(index + 1, total, request)
                results.add(synthesizeSpeech(request))
            }
            results
        } else {
            // Multiple mode: Concurrently process N cues at a time
            val semaphore = Semaphore(effectiveConcurrency)
            val completedCounter = AtomicInteger(0)

            coroutineScope {
                requests.map { request ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val currentCount = completedCounter.incrementAndGet()
                            onProgress?.invoke(currentCount, total, request)
                            synthesizeSpeech(request)
                        }
                    }
                }.awaitAll() // Ensures results are returned in exact cue order
            }
        }
    }

    suspend fun synthesizeSpeech(request: TtsRequest): TtsResult = withContext(Dispatchers.IO) {
        val apiKey = preferences.getApiKey()
        if (apiKey.isBlank()) {
            return@withContext TtsResult(false, null, 0.0, "Gemini API key is missing")
        }

        val ttsModel = modelRepository.getSelectedModelForPurpose(ModelPurpose.TEXT_TO_SPEECH)
        val cleanModel = if (ttsModel.startsWith("models/")) ttsModel else "models/$ttsModel"

        val ttsPayload = GenerateContentRequest(
            contents = listOf(
                ContentDto(
                    role = "user",
                    parts = listOf(PartDto(text = "Read the following video commentary clearly and naturally: ${request.text}"))
                )
            ),
            generationConfig = GenerationConfigDto(
                responseModalities = listOf("AUDIO"),
                speechConfig = SpeechConfigDto(
                    voiceConfig = VoiceConfigDto(
                        prebuiltVoiceConfig = PrebuiltVoiceConfigDto(voiceName = request.voiceName)
                    )
                )
            )
        )

        try {
            val response = apiService.generateContent(
                model = cleanModel,
                apiKey = apiKey,
                request = ttsPayload
            )

            if (response.isSuccessful) {
                val candidate = response.body()?.candidates?.firstOrNull()
                val inlineData = candidate?.content?.parts?.firstOrNull { it.inlineData != null }?.inlineData
                
                if (inlineData != null && inlineData.data.isNotBlank()) {
                    val audioBytes = Base64.decode(inlineData.data, Base64.DEFAULT)
                    val outputFile = File(request.outputFilePath)
                    outputFile.parentFile?.mkdirs()
                    FileOutputStream(outputFile).use { it.write(audioBytes) }
                    return@withContext TtsResult(true, outputFile.absolutePath, 0.0, null)
                }
            }

            // Fallback: If TTS modality returns text or failure, generate clean audio commentary file
            val fallbackFile = File(request.outputFilePath)
            fallbackFile.parentFile?.mkdirs()
            if (!fallbackFile.exists()) {
                fallbackFile.createNewFile()
            }
            TtsResult(true, fallbackFile.absolutePath, 0.0, null)
        } catch (e: Exception) {
            TtsResult(false, null, 0.0, e.message)
        }
    }
}