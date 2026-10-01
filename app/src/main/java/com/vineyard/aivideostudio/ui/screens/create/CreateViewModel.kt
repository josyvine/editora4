package com.vineyard.aivideostudio.ui.screens.create

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vineyard.aivideostudio.ai.model.MasterRecipe
import com.vineyard.aivideostudio.core.model.PipelineStatus
import com.vineyard.aivideostudio.core.model.Project
import com.vineyard.aivideostudio.core.model.TimelineMap
import com.vineyard.aivideostudio.core.model.VideoMetadata
import com.vineyard.aivideostudio.core.util.FileUtils
import com.vineyard.aivideostudio.core.util.JsonUtils
import com.vineyard.aivideostudio.core.util.UriUtils
import com.vineyard.aivideostudio.data.storage.ProjectStorageManager
import com.vineyard.aivideostudio.media.video.VideoMetadataReader
import com.vineyard.aivideostudio.media.video.VideoValidator
import com.vineyard.aivideostudio.project.repository.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

data class CreateUiState(
    val projectName: String = "",
    val selectedVideoUri: Uri? = null,
    val selectedVideoFileName: String? = null,
    val videoMetadata: VideoMetadata? = null,
    val targetAspectRatio: String = "ORIGINAL", // ORIGINAL, 9:16, 16:9, 1:1
    val youtubeUrl: String = "",
    val isRecipeMode: Boolean = false, // Toggle: Auto AI vs. Master Recipe Import (Script Mode)
    val masterRecipeJson: String = "",
    val masterRecipeFileName: String? = null,
    val parsedRecipe: MasterRecipe? = null,
    val detectedCueMode: String = "single",      // "single" or "multiple" detected from script
    val detectedCueConcurrency: Int = 1,        // Dynamic parallel count (e.g. 10)
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val createdProjectId: String? = null
) {
    val isYoutubeUrlValid: Boolean
        get() = isValidYoutubeUrl(youtubeUrl)

    val isShortsFormat: Boolean
        get() {
            val isVertical = (videoMetadata?.height ?: 0) > (videoMetadata?.width ?: 0)
            val lowerUrl = youtubeUrl.lowercase()
            val isShortsUrl = lowerUrl.contains("/shorts/") || lowerUrl.contains("/reel/") || lowerUrl.contains("/tiktok/")
            return isVertical || isShortsUrl
        }

    val isReadyToCreate: Boolean
        get() = if (isRecipeMode) {
            selectedVideoUri != null && parsedRecipe != null && !isLoading
        } else {
            selectedVideoUri != null && isYoutubeUrlValid && !isLoading
        }

    companion object {
        fun isValidYoutubeUrl(url: String): Boolean {
            val trimmed = url.trim().lowercase()
            if (trimmed.isEmpty()) return false
            val isHttp = trimmed.startsWith("http://") || trimmed.startsWith("https://")
            val hasValidDomain = trimmed.contains(".") && trimmed.length > 8
            return isHttp && hasValidDomain
        }
    }
}

class CreateViewModel(
    private val context: Context,
    private val projectRepository: ProjectRepository,
    private val storageManager: ProjectStorageManager,
    private val metadataReader: VideoMetadataReader
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateUiState())
    val uiState: StateFlow<CreateUiState> = _uiState.asStateFlow()

    private val MAX_JSON_FILE_BYTES = 5 * 1024 * 1024L // 5 MB hard limit for JSON files

    fun onProjectNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(projectName = name, errorMessage = null)
    }

    fun onAspectRatioChanged(ratio: String) {
        _uiState.value = _uiState.value.copy(targetAspectRatio = ratio)
    }

    fun onYoutubeUrlChanged(url: String) {
        _uiState.value = _uiState.value.copy(youtubeUrl = url, errorMessage = null)
    }

    fun onModeToggled(isRecipeMode: Boolean) {
        _uiState.value = _uiState.value.copy(isRecipeMode = isRecipeMode, errorMessage = null)
    }

    /**
     * Safely reads JSON files on Dispatchers.IO with strict size checking to prevent OutOfMemoryError crashes.
     */
    fun onJsonFileSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                val (text, parsed, fileName) = withContext(Dispatchers.IO) {
                    val contentResolver = context.contentResolver

                    // 1. Check file size before reading to prevent OutOfMemoryError
                    var fileSize = -1L
                    try {
                        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                                if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                                    fileSize = cursor.getLong(sizeIndex)
                                }
                            }
                        }
                    } catch (_: Exception) {}

                    if (fileSize > MAX_JSON_FILE_BYTES) {
                        throw IllegalArgumentException(
                            "Selected file is too large (${fileSize / (1024 * 1024)} MB). Please select a .json recipe file, not a video."
                        )
                    }

                    // 2. Stream-read with bounded buffer
                    val inputStream = contentResolver.openInputStream(uri)
                        ?: throw IllegalArgumentException("Cannot open selected file")

                    val stringBuilder = StringBuilder()
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        val buffer = CharArray(8192)
                        var totalCharsRead = 0
                        var charsRead: Int
                        while (reader.read(buffer).also { charsRead = it } != -1) {
                            totalCharsRead += charsRead
                            if (totalCharsRead > MAX_JSON_FILE_BYTES) {
                                throw IllegalArgumentException("Recipe file exceeds 5MB size limit.")
                            }
                            stringBuilder.append(buffer, 0, charsRead)
                        }
                    }

                    val jsonText = stringBuilder.toString().trim()
                    if (!jsonText.startsWith("{") && !jsonText.startsWith("[")) {
                        throw IllegalArgumentException("Selected file does not contain valid JSON data.")
                    }

                    val parsedRecipe = JsonUtils.fromJson<MasterRecipe>(jsonText)
                        ?: throw IllegalArgumentException("Invalid Editora Master Recipe JSON format")

                    val resolvedFileName = UriUtils.getFileName(context, uri)
                    Triple(jsonText, parsedRecipe, resolvedFileName)
                }

                val defaultProjectName = parsed.projectInfo?.title ?: _uiState.value.projectName
                val defaultRatio = parsed.projectInfo?.targetAspectRatio ?: _uiState.value.targetAspectRatio
                val cueMode = parsed.cueMode ?: parsed.commentary.cueMode ?: "single"
                val cueConcurrency = parsed.cueConcurrency ?: parsed.commentary.cueConcurrency ?: 1

                _uiState.value = _uiState.value.copy(
                    isRecipeMode = true,
                    masterRecipeJson = text,
                    masterRecipeFileName = fileName,
                    parsedRecipe = parsed,
                    detectedCueMode = cueMode,
                    detectedCueConcurrency = cueConcurrency.coerceAtLeast(1),
                    projectName = if (_uiState.value.projectName.isBlank()) defaultProjectName else _uiState.value.projectName,
                    targetAspectRatio = defaultRatio,
                    isLoading = false,
                    errorMessage = null
                )
            } catch (oom: OutOfMemoryError) {
                System.gc()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Out of memory: The selected file is too large to load as JSON."
                )
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load Recipe JSON: ${t.message}"
                )
            }
        }
    }

    fun onJsonPasted(jsonText: String) {
        if (jsonText.isBlank()) return
        try {
            if (jsonText.length > MAX_JSON_FILE_BYTES) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Pasted text is too large (maximum 5MB allowed)."
                )
                return
            }

            val parsed = JsonUtils.fromJson<MasterRecipe>(jsonText.trim())
                ?: throw IllegalArgumentException("Invalid Editora Master Recipe JSON structure")

            val defaultProjectName = parsed.projectInfo?.title ?: _uiState.value.projectName
            val defaultRatio = parsed.projectInfo?.targetAspectRatio ?: _uiState.value.targetAspectRatio
            val cueMode = parsed.cueMode ?: parsed.commentary.cueMode ?: "single"
            val cueConcurrency = parsed.cueConcurrency ?: parsed.commentary.cueConcurrency ?: 1

            _uiState.value = _uiState.value.copy(
                isRecipeMode = true,
                masterRecipeJson = jsonText.trim(),
                masterRecipeFileName = "Pasted_Recipe.json",
                parsedRecipe = parsed,
                detectedCueMode = cueMode,
                detectedCueConcurrency = cueConcurrency.coerceAtLeast(1),
                projectName = if (_uiState.value.projectName.isBlank()) defaultProjectName else _uiState.value.projectName,
                targetAspectRatio = defaultRatio,
                errorMessage = null
            )
        } catch (t: Throwable) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Failed to parse pasted Recipe: ${t.message}"
            )
        }
    }

    fun onVideoSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val metadata = metadataReader.readMetadata(uri)
            val validationError = VideoValidator.validateSourceVideo(metadata)
            if (validationError != null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = validationError
                )
            } else {
                val fullFileName = UriUtils.getFileName(context, uri)
                val baseName = fullFileName.substringBeforeLast(".")
                val defaultName = if (_uiState.value.projectName.isBlank()) baseName else _uiState.value.projectName

                _uiState.value = _uiState.value.copy(
                    selectedVideoUri = uri,
                    selectedVideoFileName = fullFileName,
                    videoMetadata = metadata,
                    projectName = defaultName,
                    targetAspectRatio = "ORIGINAL",
                    isLoading = false,
                    errorMessage = null
                )
            }
        }
    }

    fun createProject() {
        val state = _uiState.value

        if (state.selectedVideoUri == null) {
            _uiState.value = state.copy(
                errorMessage = "Please select the source video file from your device."
            )
            return
        }

        if (!state.isRecipeMode && (state.youtubeUrl.isBlank() || !state.isYoutubeUrlValid)) {
            _uiState.value = state.copy(
                errorMessage = "Please provide a valid source context URL or switch to Master Recipe Mode."
            )
            return
        }

        if (state.isRecipeMode && state.parsedRecipe == null) {
            _uiState.value = state.copy(
                errorMessage = "Please upload or paste a valid Master Recipe JSON script."
            )
            return
        }

        val name = if (state.projectName.isBlank()) {
            state.parsedRecipe?.projectInfo?.title ?: "Studio Video ${System.currentTimeMillis() % 1000}"
        } else {
            state.projectName.trim()
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            try {
                val projectId = "proj_${UUID.randomUUID().toString().take(8)}"
                storageManager.setupProjectStructure(projectId)

                val metadata = state.videoMetadata ?: VideoMetadata()

                val destFile = storageManager.getSourceFile(projectId)
                FileUtils.copyUriToFile(context, state.selectedVideoUri, destFile)
                val sourcePath = destFile.absolutePath
                val sourceUriString = Uri.fromFile(destFile).toString()

                val initialTimelineMap = TimelineMap.identity(projectId, metadata.durationSeconds)

                val project = Project(
                    id = projectId,
                    name = name,
                    sourceUri = sourceUriString,
                    sourcePath = sourcePath,
                    currentVideoUri = sourceUriString,
                    sourceYoutubeUrl = if (!state.isRecipeMode) state.youtubeUrl.trim() else null,
                    masterRecipeJson = if (state.isRecipeMode) state.masterRecipeJson else null,
                    metadata = metadata,
                    currentStage = PipelineStatus.IDLE,
                    status = PipelineStatus.IDLE,
                    targetAspectRatio = state.targetAspectRatio,
                    timelineMapJson = JsonUtils.toJson(initialTimelineMap)
                )

                projectRepository.saveProject(project)
                projectRepository.saveTimelineMap(projectId, initialTimelineMap)

                _uiState.value = state.copy(
                    isLoading = false,
                    createdProjectId = projectId
                )
            } catch (e: Exception) {
                _uiState.value = state.copy(
                    isLoading = false,
                    errorMessage = "Failed to initialize studio project: ${e.message}"
                )
            }
        }
    }

    fun resetCreatedState() {
        _uiState.value = _uiState.value.copy(createdProjectId = null)
    }
}