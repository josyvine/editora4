package com.vineyard.aivideostudio.voice.live

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.vineyard.aivideostudio.ai.model.ModelPurpose
import com.vineyard.aivideostudio.core.common.DispatcherProvider
import com.vineyard.aivideostudio.core.result.AppError
import com.vineyard.aivideostudio.core.result.AppResult
import com.vineyard.aivideostudio.data.preferences.Preferences
import com.vineyard.aivideostudio.data.repository.ModelRepositoryImpl
import com.vineyard.aivideostudio.processing.logger.ProcessingLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages the headless background WebView runtime that connects to the Gemini Multimodal Live API.
 */
class LiveCommentatorManager(
    private val context: Context,
    private val preferences: Preferences,
    private val dispatcherProvider: DispatcherProvider,
    private val logger: ProcessingLogger,
    private var modelRepository: ModelRepositoryImpl? = null
) {

    companion object {
        private const val TAG = "LiveCommentatorManager"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private val isWebViewReady = AtomicBoolean(false)
    private var pageLoadedDeferred: CompletableDeferred<Boolean>? = null

    private var activeFileOutputStream: FileOutputStream? = null
    private var activeOutputFile: File? = null
    private var commentaryDeferred: CompletableDeferred<AppResult<File>>? = null
    private val isSessionActive = AtomicBoolean(false)
    private var currentSessionModelId: String? = null

    fun setModelRepository(repo: ModelRepositoryImpl) {
        this.modelRepository = repo
    }

    /**
     * Initializes the off-screen Headless WebView instance on the main UI thread.
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun initialize(): AppResult<Unit> = withContext(dispatcherProvider.main) {
        if (webView != null && isWebViewReady.get()) {
            return@withContext AppResult.Success(Unit)
        }

        pageLoadedDeferred = CompletableDeferred()

        try {
            val newWebView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.allowFileAccess = true
                settings.allowContentAccess = true

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        if (consoleMessage != null) {
                            Log.d("LiveCommentatorWebView", "[${consoleMessage.messageLevel()}] ${consoleMessage.message()} (${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})")
                        }
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        Log.i(TAG, "Headless Live Commentator engine loaded successfully: $url")
                        isWebViewReady.set(true)
                        pageLoadedDeferred?.complete(true)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        val errorDescription = error?.description?.toString() ?: "Unknown WebView Error"
                        Log.e(TAG, "Failed loading engine: $errorDescription")
                        pageLoadedDeferred?.complete(false)
                    }
                }

                // Bind JavaScript Interface
                addJavascriptInterface(
                    LiveBridgeInterface(
                        apiKeyProvider = { fetchApiKeySync() },
                        modelIdProvider = { fetchModelIdSync() },
                        voiceNameProvider = { fetchVoiceNameSync() },
                        listener = object : LiveCommentaryListener {
                            override fun onConnecting() {
                                Log.i(TAG, "Establishing Gemini Live Bidi WebSocket connection...")
                            }

                            override fun onConnected() {
                                Log.i(TAG, "Gemini Live Bidi session authenticated and connected.")
                            }

                            override fun onAudioChunkReceived(base64PcmData: String) {
                                handleIncomingAudioChunk(base64PcmData)
                            }

                            override fun onCommentaryText(text: String) {
                                Log.d("LiveCommentatorTranscript", text)
                            }

                            override fun onCommentaryFinished() {
                                handleCommentaryCompletion()
                            }

                            override fun onError(errorMessage: String) {
                                handleCommentaryError(errorMessage)
                            }

                            override fun onDiagnostic(message: String, category: String) {
                                Log.d("LiveDiagnostic[$category]", message)
                            }
                        }
                    ),
                    "AndroidInterface"
                )
            }

            webView = newWebView
            newWebView.loadUrl("file:///android_asset/live_commentator_engine.html")

            val loaded = withTimeoutOrNull(15_000L) {
                pageLoadedDeferred?.await()
            } ?: false

            if (loaded) {
                AppResult.Success(Unit)
            } else {
                AppResult.Error(AppError.MediaProcessingError("Timed out waiting for Live Commentator WebView initialization."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed with exception: ${e.message}", e)
            AppResult.Error(AppError.MediaProcessingError(e.message ?: "Failed to initialize Live Commentator WebView", e))
        }
    }

    /**
     * Synthesizes expressive voiceover commentary using the connected Live WebSocket model.
     *
     * @param scriptText The commentary script containing emotional cue tags (e.g. [SCREAMING], [LOUD HYPE]).
     * @param personaPrompt The director persona instructions.
     * @param outputPcmFile Target file where raw 24kHz 16-bit mono Little-Endian PCM audio will be written.
     * @param modelId Optional model override to ensure exact user-selected model from Settings is used.
     */
    suspend fun generateLiveCommentary(
        scriptText: String,
        personaPrompt: String,
        outputPcmFile: File,
        modelId: String? = null
    ): AppResult<File> = withContext(dispatcherProvider.io) {
        currentSessionModelId = modelId

        if (!isWebViewReady.get() || webView == null) {
            val initResult = initialize()
            if (initResult is AppResult.Error) {
                currentSessionModelId = null
                return@withContext AppResult.Error(initResult.error)
            }
        }

        if (isSessionActive.getAndSet(true)) {
            currentSessionModelId = null
            return@withContext AppResult.Error(AppError.MediaProcessingError("A live commentary session is already active."))
        }

        // Prepare target output file
        outputPcmFile.parentFile?.mkdirs()
        if (outputPcmFile.exists()) {
            outputPcmFile.delete()
        }

        try {
            activeFileOutputStream = FileOutputStream(outputPcmFile, false)
            activeOutputFile = outputPcmFile
        } catch (e: Exception) {
            isSessionActive.set(false)
            currentSessionModelId = null
            activeOutputFile = null
            return@withContext AppResult.Error(AppError.StorageError("Cannot create output PCM file: ${e.message}"))
        }

        val deferred = CompletableDeferred<AppResult<File>>()
        commentaryDeferred = deferred

        // Dispatch session start inside WebView on Main thread
        withContext(dispatcherProvider.main) {
            val escapedScript = escapeForJavascript(scriptText)
            val escapedPersona = escapeForJavascript(personaPrompt)
            val jsCall = "window.startCommentarySession('$escapedScript', '$escapedPersona');"
            webView?.evaluateJavascript(jsCall, null)
        }

        // Wait for generation with safety timeout of 120 seconds
        val result = withTimeoutOrNull(120_000L) {
            deferred.await()
        } ?: run {
            stopCurrentSessionSync()
            AppResult.Error(AppError.NetworkError("Gemini Live Commentary stream timed out after 120 seconds."))
        }

        isSessionActive.set(false)
        currentSessionModelId = null
        activeOutputFile = null
        return@withContext result
    }

    /**
     * Appends incoming decoded Base64 PCM audio bytes directly into the open output file.
     */
    private fun handleIncomingAudioChunk(base64PcmData: String) {
        try {
            val rawBytes = Base64.decode(base64PcmData, Base64.DEFAULT)
            synchronized(this) {
                activeFileOutputStream?.write(rawBytes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing PCM audio chunk: ${e.message}", e)
        }
    }

    /**
     * Invoked when the Live model turn is complete and audio streaming is finished.
     */
    private fun handleCommentaryCompletion() {
        val completedFile = activeOutputFile

        synchronized(this) {
            try {
                activeFileOutputStream?.flush()
                activeFileOutputStream?.close()
                activeFileOutputStream = null
            } catch (e: Exception) {
                Log.e(TAG, "Error closing output stream: ${e.message}")
            }
        }

        commentaryDeferred?.let { def ->
            if (def.isActive) {
                def.complete(AppResult.Success(completedFile ?: File("")))
            }
        }
    }

    /**
     * Invoked when an error is returned by the Live WebSocket engine.
     */
    private fun handleCommentaryError(errorMessage: String) {
        Log.e(TAG, "Live Commentary Engine error: $errorMessage")
        synchronized(this) {
            try {
                activeFileOutputStream?.close()
                activeFileOutputStream = null
                activeOutputFile = null
            } catch (_: Exception) {}
        }

        commentaryDeferred?.let { def ->
            if (def.isActive) {
                def.complete(AppResult.Error(AppError.MediaProcessingError("Live commentary engine error: $errorMessage")))
            }
        }
        isSessionActive.set(false)
    }

    private fun stopCurrentSessionSync() {
        mainHandler.post {
            webView?.evaluateJavascript("window.stopCommentarySession();", null)
        }
        synchronized(this) {
            try {
                activeFileOutputStream?.close()
                activeFileOutputStream = null
                activeOutputFile = null
            } catch (_: Exception) {}
        }
        isSessionActive.set(false)
    }

    /**
     * Disposes the WebView and closes any open streams.
     */
    fun release() {
        stopCurrentSessionSync()
        mainHandler.post {
            webView?.stopLoading()
            webView?.clearHistory()
            webView?.destroy()
            webView = null
            isWebViewReady.set(false)
        }
    }

    private fun escapeForJavascript(input: String): String {
        return input
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("\t", "\\t")
    }

    private fun cleanModelId(rawId: String): String {
        val clean = rawId.trim()
        return if (clean.startsWith("models/")) clean else "models/$clean"
    }

    private fun fetchApiKeySync(): String {
        return kotlinx.coroutines.runBlocking(dispatcherProvider.io) {
            try {
                preferences.geminiApiKey.first()
            } catch (_: Exception) {
                ""
            }
        }
    }

    private fun fetchModelIdSync(): String {
        // 1. Session-level model override if provided
        currentSessionModelId?.let { if (it.isNotBlank()) return cleanModelId(it) }

        return kotlinx.coroutines.runBlocking(dispatcherProvider.io) {
            try {
                // 2. Read dynamically configured model from Room database (set via Settings dropdown)
                val repoModel = modelRepository?.getSelectedModelForPurpose(ModelPurpose.LIVE_VOICE)
                if (!repoModel.isNullOrBlank()) {
                    return@runBlocking cleanModelId(repoModel)
                }

                // 3. Check preferences flow
                val configuredModel = preferences.selectedLiveModel.first()
                if (configuredModel.isNotBlank()) cleanModelId(configuredModel) else "models/gemini-2.5-flash-native-audio-preview-12-2025"
            } catch (_: Exception) {
                "models/gemini-2.5-flash-native-audio-preview-12-2025"
            }
        }
    }

    private fun fetchVoiceNameSync(): String {
        return kotlinx.coroutines.runBlocking(dispatcherProvider.io) {
            try {
                val voice = preferences.commentaryVoice.first()
                if (voice.isNotBlank()) voice else "Puck"
            } catch (_: Exception) {
                "Puck"
            }
        }
    }
}