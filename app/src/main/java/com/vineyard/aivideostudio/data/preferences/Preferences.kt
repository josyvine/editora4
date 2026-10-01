package com.vineyard.aivideostudio.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import com.vineyard.aivideostudio.core.common.AppConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Unified preferences contract accessible across the entire studio architecture.
 */
interface Preferences {
    val geminiApiKey: StateFlow<String>
    val selectedLiveModel: StateFlow<String>
    val commentaryVoice: StateFlow<String>
    val purgeOriginalAudio: StateFlow<Boolean>

    fun setGeminiApiKey(key: String)
    fun setSelectedLiveModel(modelId: String)
    fun setCommentaryVoice(voiceName: String)
    fun setPurgeOriginalAudio(purge: Boolean)
}

class GeminiPreferences(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)

    private val _apiKeyFlow = MutableStateFlow(getApiKey())
    val apiKeyFlow: StateFlow<String> = _apiKeyFlow.asStateFlow()

    fun getApiKey(): String {
        val userSavedKey = prefs.getString("custom_api_key", null)
        if (!userSavedKey.isNullOrBlank()) return userSavedKey.trim()
        return try {
            BuildConfig.GEMINI_API_KEY.trim()
        } catch (_: Exception) {
            ""
        }
    }

    fun setApiKey(key: String) {
        val cleanKey = key.trim()
        prefs.edit().putString("custom_api_key", cleanKey).apply()
        _apiKeyFlow.value = cleanKey
    }

    fun clearApiKey() {
        prefs.edit().remove("custom_api_key").apply()
        _apiKeyFlow.value = try { BuildConfig.GEMINI_API_KEY.trim() } catch (_: Exception) { "" }
    }

    fun getLastModelSync(): Long = prefs.getLong("last_model_sync", 0L)
    fun setLastModelSync(timestamp: Long) = prefs.edit().putLong("last_model_sync", timestamp).apply()
}

class ProcessingPreferences(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("processing_prefs", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_PURGE_ORIGINAL_AUDIO = true
        const val DEFAULT_COMMENTARY_VOICE = "Puck"
        const val DEFAULT_LIVE_MODEL = "gemini-3.8-live"
    }

    private val _purgeOriginalAudioFlow = MutableStateFlow(purgeOriginalAudio)
    val purgeOriginalAudioFlow: StateFlow<Boolean> = _purgeOriginalAudioFlow.asStateFlow()

    private val _commentaryVoiceFlow = MutableStateFlow(commentaryVoice)
    val commentaryVoiceFlow: StateFlow<String> = _commentaryVoiceFlow.asStateFlow()

    private val _selectedLiveModelFlow = MutableStateFlow(selectedLiveModel)
    val selectedLiveModelFlow: StateFlow<String> = _selectedLiveModelFlow.asStateFlow()

    var maxQaRetries: Int
        get() = prefs.getInt("max_qa_retries", AppConstants.DEFAULT_MAX_QA_RETRIES)
        set(value) = prefs.edit().putInt("max_qa_retries", value).apply()

    var keepIntermediateVideos: Boolean
        get() = prefs.getBoolean("keep_intermediate_videos", AppConstants.DEFAULT_KEEP_INTERMEDIATE_VIDEOS)
        set(value) = prefs.edit().putBoolean("keep_intermediate_videos", value).apply()

    var autoFinalQa: Boolean
        get() = prefs.getBoolean("auto_final_qa", AppConstants.DEFAULT_AUTO_FINAL_QA)
        set(value) = prefs.edit().putBoolean("auto_final_qa", value).apply()

    var purgeOriginalAudio: Boolean
        get() = prefs.getBoolean("purge_original_audio", DEFAULT_PURGE_ORIGINAL_AUDIO)
        set(value) {
            prefs.edit().putBoolean("purge_original_audio", value).apply()
            _purgeOriginalAudioFlow.value = value
        }

    var commentaryVoice: String
        get() = prefs.getString("commentary_voice", DEFAULT_COMMENTARY_VOICE) ?: DEFAULT_COMMENTARY_VOICE
        set(value) {
            val cleanVal = value.trim()
            prefs.edit().putString("commentary_voice", cleanVal).apply()
            _commentaryVoiceFlow.value = cleanVal
        }

    var selectedLiveModel: String
        get() = prefs.getString("selected_live_model", DEFAULT_LIVE_MODEL) ?: DEFAULT_LIVE_MODEL
        set(value) {
            val cleanVal = value.trim()
            prefs.edit().putString("selected_live_model", cleanVal).apply()
            _selectedLiveModelFlow.value = cleanVal
        }

    var customOutputDirectoryUri: String?
        get() = prefs.getString("custom_output_dir", null)
        set(value) = prefs.edit().putString("custom_output_dir", value).apply()
}

/**
 * Composite application preferences container implementing the unified Preferences interface.
 */
class AppPreferences(
    val gemini: GeminiPreferences,
    val processing: ProcessingPreferences
) : Preferences {

    override val geminiApiKey: StateFlow<String> = gemini.apiKeyFlow
    override val selectedLiveModel: StateFlow<String> = processing.selectedLiveModelFlow
    override val commentaryVoice: StateFlow<String> = processing.commentaryVoiceFlow
    override val purgeOriginalAudio: StateFlow<Boolean> = processing.purgeOriginalAudioFlow

    override fun setGeminiApiKey(key: String) {
        gemini.setApiKey(key)
    }

    override fun setSelectedLiveModel(modelId: String) {
        processing.selectedLiveModel = modelId
    }

    override fun setCommentaryVoice(voiceName: String) {
        processing.commentaryVoice = voiceName
    }

    override fun setPurgeOriginalAudio(purge: Boolean) {
        processing.purgeOriginalAudio = purge
    }
}