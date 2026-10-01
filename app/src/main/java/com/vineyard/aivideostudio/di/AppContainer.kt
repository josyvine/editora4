package com.vineyard.aivideostudio.di

import android.content.Context
import com.vineyard.aivideostudio.ai.gemini.GeminiClient
import com.vineyard.aivideostudio.core.common.DefaultDispatcherProvider
import com.vineyard.aivideostudio.core.common.DispatcherProvider
import com.vineyard.aivideostudio.core.model.PipelineStatus
import com.vineyard.aivideostudio.data.local.database.AppDatabase
import com.vineyard.aivideostudio.data.preferences.AppPreferences
import com.vineyard.aivideostudio.data.preferences.GeminiPreferences
import com.vineyard.aivideostudio.data.preferences.ProcessingPreferences
import com.vineyard.aivideostudio.data.remote.network.NetworkLoggingInterceptor
import com.vineyard.aivideostudio.data.remote.network.NetworkModule
import com.vineyard.aivideostudio.data.repository.ModelRepositoryImpl
import com.vineyard.aivideostudio.data.repository.ProjectRepositoryImpl
import com.vineyard.aivideostudio.data.repository.VoiceRepositoryImpl
import com.vineyard.aivideostudio.data.storage.ProjectStorageManager
import com.vineyard.aivideostudio.data.storage.StorageManager
import com.vineyard.aivideostudio.domain.pipeline.VideoProcessingPipeline
import com.vineyard.aivideostudio.media.audio.AudioExtractor
import com.vineyard.aivideostudio.media.audio.PcmToM4aConverter
import com.vineyard.aivideostudio.media.transformer.Media3TransformerEngine
import com.vineyard.aivideostudio.media.video.ObjectAnchorCalibrator
import com.vineyard.aivideostudio.media.video.VideoMetadataReader
import com.vineyard.aivideostudio.processing.controller.ProcessingController
import com.vineyard.aivideostudio.processing.logger.LogSeverity
import com.vineyard.aivideostudio.processing.logger.ProcessingLogger
import com.vineyard.aivideostudio.project.repository.ProjectRepository
import com.vineyard.aivideostudio.voice.live.LiveCommentatorManager
import com.vineyard.aivideostudio.voice.tts.GeminiTtsEngine

class AppContainer(private val context: Context) {

    // Coroutine Dispatcher Provider
    val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider()

    // Preferences & Persistence
    val geminiPreferences = GeminiPreferences(context)
    val processingPreferences = ProcessingPreferences(context)
    val appPreferences = AppPreferences(geminiPreferences, processingPreferences)

    val database = AppDatabase.getInstance(context)

    // Storage Managers
    val storageManager = StorageManager(context)
    val projectStorageManager = ProjectStorageManager(storageManager)

    // Logging & Audit
    val logger = ProcessingLogger(database.persistentLogDao())

    val networkLogger = NetworkLoggingInterceptor { message, isError, details ->
        logger.log(
            projectId = "NETWORK",
            stage = PipelineStatus.IDLE,
            message = message,
            severity = if (isError) LogSeverity.ERROR else LogSeverity.INFO,
            details = details
        )
    }

    // Network & Gemini REST Client
    val okHttpClient = NetworkModule.createOkHttpClient(
        apiKeyProvider = { geminiPreferences.getApiKey() },
        networkLogger = networkLogger
    )
    val geminiApiService = NetworkModule.createGeminiApiService(okHttpClient)

    // Repositories
    val projectRepository: ProjectRepository = ProjectRepositoryImpl(database)
    val modelRepository = ModelRepositoryImpl(geminiApiService, geminiPreferences, database.modelConfigurationDao())
    val voiceRepository = VoiceRepositoryImpl(database.voiceDao())

    val aiRequestDao = database.aiRequestDao()
    val geminiClient = GeminiClient(geminiApiService, geminiPreferences, aiRequestDao)

    // Media & Transformation Engines
    val videoMetadataReader = VideoMetadataReader(context)
    val transformerEngine = Media3TransformerEngine(context)
    val audioExtractor = AudioExtractor(context)
    val ttsEngine = GeminiTtsEngine(context, geminiApiService, geminiPreferences, modelRepository)

    // On-Device Script-Mode Object & Face Calibrator
    val objectAnchorCalibrator = ObjectAnchorCalibrator(context)

    // Hardware AAC Encoder for Live PCM Audio Streams
    val pcmToM4aConverter = PcmToM4aConverter(
        dispatcherProvider = dispatcherProvider,
        logger = logger
    )

    // Headless Live Commentator Runtime (Gemini Bidi WebSocket Engine)
    val liveCommentatorManager = LiveCommentatorManager(
        context = context,
        preferences = appPreferences,
        dispatcherProvider = dispatcherProvider,
        logger = logger
    )

    // Central Autonomous Video Processing Pipeline
    val pipeline = VideoProcessingPipeline(
        context = context,
        projectRepository = projectRepository,
        modelRepository = modelRepository,
        geminiClient = geminiClient,
        transformerEngine = transformerEngine,
        audioExtractor = audioExtractor,
        liveCommentatorManager = liveCommentatorManager,
        pcmToM4aConverter = pcmToM4aConverter,
        ttsEngine = ttsEngine,
        storageManager = projectStorageManager,
        preferences = processingPreferences,
        objectAnchorCalibrator = objectAnchorCalibrator,
        logger = logger
    )

    // Background Execution Controller
    val processingController = ProcessingController(
        context = context,
        pipeline = pipeline,
        projectRepository = projectRepository,
        logger = logger
    )
}