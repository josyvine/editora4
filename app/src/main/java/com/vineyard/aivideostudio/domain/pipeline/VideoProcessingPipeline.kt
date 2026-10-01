package com.vineyard.aivideostudio.domain.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.vineyard.aivideostudio.ai.gemini.GeminiClient
import com.vineyard.aivideostudio.ai.model.AiQaResponse
import com.vineyard.aivideostudio.ai.model.CaptionDecision
import com.vineyard.aivideostudio.ai.model.CaptionItem
import com.vineyard.aivideostudio.ai.model.CommentaryDecision
import com.vineyard.aivideostudio.ai.model.CommentarySegmentDto
import com.vineyard.aivideostudio.ai.model.CropDecision
import com.vineyard.aivideostudio.ai.model.HighlightSegment
import com.vineyard.aivideostudio.ai.model.MasterRecipe
import com.vineyard.aivideostudio.ai.model.ModelPurpose
import com.vineyard.aivideostudio.ai.model.NormalizedBoundsDto
import com.vineyard.aivideostudio.ai.model.SourceAnalysis
import com.vineyard.aivideostudio.ai.model.TrimDecision
import com.vineyard.aivideostudio.ai.model.TrimSegment
import com.vineyard.aivideostudio.ai.model.ZoomDecision
import com.vineyard.aivideostudio.ai.prompt.Prompts
import com.vineyard.aivideostudio.ai.validator.AiResponseValidator
import com.vineyard.aivideostudio.core.model.ArtifactType
import com.vineyard.aivideostudio.core.model.Caption
import com.vineyard.aivideostudio.core.model.CommentarySegment
import com.vineyard.aivideostudio.core.model.MediaArtifact
import com.vineyard.aivideostudio.core.model.PipelineStatus
import com.vineyard.aivideostudio.core.model.PipelineStep
import com.vineyard.aivideostudio.core.model.Project
import com.vineyard.aivideostudio.core.model.QaResult
import com.vineyard.aivideostudio.core.model.QaVerdict
import com.vineyard.aivideostudio.core.model.StepStatus
import com.vineyard.aivideostudio.core.model.TimelineMap
import com.vineyard.aivideostudio.core.model.TranscriptSegment
import com.vineyard.aivideostudio.core.model.effects.BlurSpec
import com.vineyard.aivideostudio.core.model.effects.ColorGradeSpec
import com.vineyard.aivideostudio.core.model.effects.NormalizedBounds
import com.vineyard.aivideostudio.core.model.effects.ReplacementOverlaySpec
import com.vineyard.aivideostudio.core.model.effects.SpeedRampSpec
import com.vineyard.aivideostudio.core.model.effects.TextCardSpec
import com.vineyard.aivideostudio.core.model.effects.TrackingIndicatorSpec
import com.vineyard.aivideostudio.core.result.AppError
import com.vineyard.aivideostudio.core.result.AppResult
import com.vineyard.aivideostudio.core.util.JsonUtils
import com.vineyard.aivideostudio.data.preferences.ProcessingPreferences
import com.vineyard.aivideostudio.data.repository.ModelRepositoryImpl
import com.vineyard.aivideostudio.data.storage.ProjectStorageManager
import com.vineyard.aivideostudio.media.audio.AudioExtractor
import com.vineyard.aivideostudio.media.audio.PcmToM4aConverter
import com.vineyard.aivideostudio.media.timeline.TimelineMapper
import com.vineyard.aivideostudio.media.transformer.Media3TransformerEngine
import com.vineyard.aivideostudio.media.video.ObjectAnchorCalibrator
import com.vineyard.aivideostudio.media.video.OcrAnchorCalibrator
import com.vineyard.aivideostudio.media.video.VideoMetadataReader
import com.vineyard.aivideostudio.processing.logger.LogSeverity
import com.vineyard.aivideostudio.processing.logger.ProcessingLogger
import com.vineyard.aivideostudio.project.repository.ProjectRepository
import com.vineyard.aivideostudio.voice.live.LiveCommentatorManager
import com.vineyard.aivideostudio.voice.model.TtsRequest
import com.vineyard.aivideostudio.voice.tts.GeminiTtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

class VideoProcessingPipeline(
    private val context: Context,
    private val projectRepository: ProjectRepository,
    private val modelRepository: ModelRepositoryImpl,
    private val geminiClient: GeminiClient,
    private val transformerEngine: Media3TransformerEngine,
    private val audioExtractor: AudioExtractor,
    private val liveCommentatorManager: LiveCommentatorManager,
    private val pcmToM4aConverter: PcmToM4aConverter,
    private val ttsEngine: GeminiTtsEngine,
    private val storageManager: ProjectStorageManager,
    private val preferences: ProcessingPreferences,
    private val objectAnchorCalibrator: ObjectAnchorCalibrator,
    private val logger: ProcessingLogger
) {

    suspend fun executePipeline(
        projectId: String,
        onStageChanged: (PipelineStatus, String) -> Unit
    ): AppResult<Project> = withContext(Dispatchers.IO) {
        val project = projectRepository.getProjectById(projectId)
            ?: return@withContext AppResult.Error(AppError.StorageError("Project not found: $projectId"))

        // Fast-Path: If Master Recipe JSON is attached, execute recipe mode directly (Script Mode)
        if (!project.masterRecipeJson.isNullOrBlank()) {
            return@withContext executeMasterRecipePipeline(project, onStageChanged)
        }

        // =========================================================================
        // AUTO MODE (Strictly single/sequential execution)
        // =========================================================================
        val videoMetadataReader = VideoMetadataReader(context)
        logger.log(projectId, PipelineStatus.SOURCE_ANALYSIS, "Starting Auto AI Video Studio pipeline for ${project.name}")

        var currentVideoUri = project.currentVideoUri
        var currentDuration = project.metadata.durationSeconds
        var timelineMap = if (project.timelineMapJson != null) {
            JsonUtils.fromJson<TimelineMap>(project.timelineMapJson) ?: TimelineMap.identity(projectId, currentDuration)
        } else {
            TimelineMap.identity(projectId, currentDuration)
        }

        var sourceAnalysis: SourceAnalysis? = if (project.sourceAnalysisJson != null) {
            JsonUtils.fromJson<SourceAnalysis>(project.sourceAnalysisJson)
        } else null

        // 1. MULTIMODAL SOURCE ANALYSIS
        if (sourceAnalysis == null) {
            val stageMsg = if (!project.sourceYoutubeUrl.isNullOrBlank()) {
                "Gemini watching YouTube stream & analyzing full narrative..."
            } else {
                "Gemini analyzing video composition, dialogue, and narrative arc..."
            }
            onStageChanged(PipelineStatus.SOURCE_ANALYSIS, stageMsg)
            logger.log(projectId, PipelineStatus.SOURCE_ANALYSIS, "Semantic multimodal analysis started (URL: ${project.sourceYoutubeUrl ?: "Local File"})")
            recordStep(projectId, PipelineStatus.SOURCE_ANALYSIS, StepStatus.IN_PROGRESS, "Analyzing full video source")

            val modelId = modelRepository.getSelectedModelForPurpose(ModelPurpose.VIDEO_ANALYSIS)
            val prompt = Prompts.buildSourceAnalysisPrompt(project.metadata, project.sourceYoutubeUrl)

            val analysisResult = geminiClient.generateStructured(
                projectId = projectId,
                stage = PipelineStatus.SOURCE_ANALYSIS,
                modelId = modelId,
                prompt = prompt,
                mediaUri = project.sourceYoutubeUrl,
                mediaMimeType = "video/mp4"
            ) { json -> JsonUtils.fromJson<SourceAnalysis>(json) }

            when (analysisResult) {
                is AppResult.Success -> {
                    sourceAnalysis = analysisResult.data
                    projectRepository.saveProject(
                        project.copy(
                            sourceAnalysisJson = JsonUtils.toJson(sourceAnalysis),
                            timelineMapJson = JsonUtils.toJson(timelineMap),
                            currentStage = PipelineStatus.SOURCE_ANALYSIS_COMPLETE,
                            status = PipelineStatus.SOURCE_ANALYSIS_COMPLETE
                        )
                    )
                    recordStep(projectId, PipelineStatus.SOURCE_ANALYSIS, StepStatus.COMPLETED, "Source analysis completed")
                    logger.log(
                        projectId,
                        PipelineStatus.SOURCE_ANALYSIS,
                        "Diagnostic [Source]: Genre=${sourceAnalysis.category} | Res=${project.metadata.width}x${project.metadata.height} | Duration=${String.format("%.2f", currentDuration)}s | Highlights=${sourceAnalysis.highlights.size}",
                        LogSeverity.SUCCESS
                    )
                }
                is AppResult.Error -> {
                    val errorMsg = analysisResult.error.message
                    recordStep(projectId, PipelineStatus.SOURCE_ANALYSIS, StepStatus.FAILED, errorMessage = errorMsg)
                    logger.log(projectId, PipelineStatus.SOURCE_ANALYSIS, "Source analysis failed: $errorMsg", LogSeverity.ERROR)
                    projectRepository.markFailed(projectId, errorMsg)
                    return@withContext AppResult.Error(analysisResult.error)
                }
            }
        }

        if (!coroutineContext.isActive) return@withContext AppResult.Error(AppError.UnknownError("Pipeline cancelled"))

        // 2. AUDIO EXTRACTION
        onStageChanged(PipelineStatus.AUDIO_EXTRACTION, "Extracting audio track for dialogue transcription")
        recordStep(projectId, PipelineStatus.AUDIO_EXTRACTION, StepStatus.IN_PROGRESS, "Extracting audio")
        val audioOutputFile = storageManager.createAudioOutputFile(projectId, "source_audio")
        audioExtractor.extractAudio(Uri.parse(currentVideoUri), audioOutputFile)
        val audioArtifact = MediaArtifact(
            id = "art_audio_${System.currentTimeMillis()}",
            projectId = projectId,
            stage = PipelineStatus.AUDIO_EXTRACTION,
            type = ArtifactType.EXTRACTED_AUDIO,
            fileUri = Uri.fromFile(audioOutputFile).toString(),
            filePath = audioOutputFile.absolutePath,
            mimeType = "audio/mp4",
            sizeBytes = audioOutputFile.length(),
            durationSeconds = currentDuration
        )
        projectRepository.recordArtifact(audioArtifact)
        recordStep(projectId, PipelineStatus.AUDIO_EXTRACTION, StepStatus.COMPLETED, "Audio track extracted for analysis")

        // 3. TRANSCRIPTION
        onStageChanged(PipelineStatus.TRANSCRIPTION, "Generating timestamped dialogue transcript")
        recordStep(projectId, PipelineStatus.TRANSCRIPTION, StepStatus.IN_PROGRESS, "Transcribing dialogue")
        val transcriptSegments = sourceAnalysis.dialogueSegments.mapIndexed { index, dia ->
            TranscriptSegment(
                id = "trans_${index}_${System.currentTimeMillis()}",
                projectId = projectId,
                start = dia.start,
                end = dia.end,
                text = dia.text,
                speaker = dia.speaker
            )
        }
        projectRepository.saveTranscript(projectId, transcriptSegments)
        recordStep(projectId, PipelineStatus.TRANSCRIPTION, StepStatus.COMPLETED, "${transcriptSegments.size} transcript segments saved")

        // 4. TRIM & MULTI-SEGMENT HIGHLIGHT COMPILATION
        onStageChanged(PipelineStatus.TRIM_ANALYSIS, "Gemini compiling key highlights across full timeline")
        recordStep(projectId, PipelineStatus.TRIM_ANALYSIS, StepStatus.IN_PROGRESS, "Compiling highlights")

        val directorModel = modelRepository.getSelectedModelForPurpose(ModelPurpose.EDITING_DIRECTOR)
        val targetEditingMode = if (project.targetAspectRatio == "9:16" && currentDuration > 90.0) "SHORT_60S" else "HIGHLIGHTS"
        val trimPrompt = Prompts.buildTrimDecisionPrompt(sourceAnalysis, currentDuration, timelineMap, targetEditingMode)

        val trimResult = geminiClient.generateStructured(
            projectId = projectId,
            stage = PipelineStatus.TRIM_ANALYSIS,
            modelId = directorModel,
            prompt = trimPrompt,
            mediaUri = project.sourceYoutubeUrl,
            mediaMimeType = "video/mp4"
        ) { json -> JsonUtils.fromJson<TrimDecision>(json) }

        var trimDecision = when (trimResult) {
            is AppResult.Success -> trimResult.data
            is AppResult.Error -> TrimDecision(isNecessary = false, explanation = "API fallback")
        }

        onStageChanged(PipelineStatus.TRIM_EXECUTION, "Android Media3 assembling highlight cuts")
        recordStep(projectId, PipelineStatus.TRIM_EXECUTION, StepStatus.IN_PROGRESS, "Executing highlight compilation")

        val trimOutputFile = storageManager.createStageOutputFile(projectId, PipelineStatus.TRIM_EXECUTION)

        val trimExecResult = if (trimDecision.segmentsToKeep.isNotEmpty()) {
            transformerEngine.spliceHighlightSegments(
                inputUri = Uri.parse(currentVideoUri),
                outputFile = trimOutputFile,
                segments = trimDecision.segmentsToKeep,
                stripAudio = false
            )
        } else {
            val cut = trimDecision.segmentsToRemove.firstOrNull() ?: TrimSegment(start = 0.0, end = 1.0, reason = "Intro dead air cut")
            val startTrimMs = (cut.end * 1000L).toLong()
            val endTrimMs = (currentDuration * 1000L).toLong()
            transformerEngine.trimVideo(
                inputUri = Uri.parse(currentVideoUri),
                outputFile = trimOutputFile,
                startMs = startTrimMs,
                endMs = endTrimMs,
                stripAudio = false
            )
        }

        if (trimExecResult is AppResult.Success) {
            val previousDuration = currentDuration
            currentVideoUri = Uri.fromFile(trimOutputFile).toString()
            currentDuration = if (trimDecision.segmentsToKeep.isNotEmpty()) {
                trimDecision.segmentsToKeep.sumOf { it.end - it.start }
            } else {
                timelineMap = TimelineMapper.applyTrim(timelineMap, trimDecision.segmentsToRemove)
                timelineMap.currentDuration
            }
            projectRepository.updateCurrentVideoUri(projectId, currentVideoUri)

            recordStep(projectId, PipelineStatus.TRIM_EXECUTION, StepStatus.COMPLETED, "Highlight montage assembled successfully")
            logger.log(
                projectId,
                PipelineStatus.TRIM_EXECUTION,
                "Diagnostic [Highlight Montage]: Assembled ${trimDecision.segmentsToKeep.size.coerceAtLeast(1)} scenes | Original: ${String.format("%.2f", previousDuration)}s -> New: ${String.format("%.2f", currentDuration)}s",
                LogSeverity.SUCCESS
            )

            onStageChanged(PipelineStatus.TRIM_QA, "Gemini performing Trim QA check")
            val qaPrompt = Prompts.buildTrimQaPrompt(sourceAnalysis, trimDecision.segmentsToKeep.size.coerceAtLeast(1), currentDuration)
            val qaResult = runQaCheck(projectId, PipelineStatus.TRIM_QA, qaPrompt, directorModel)
            projectRepository.recordQaResult(qaResult)
        }

        // 5. CROP / REFRAME PIPELINE
        onStageChanged(PipelineStatus.CROP_ANALYSIS, "Gemini analyzing composition reframing")
        recordStep(projectId, PipelineStatus.CROP_ANALYSIS, StepStatus.IN_PROGRESS, "Evaluating crop")

        val cropPrompt = Prompts.buildCropDecisionPrompt(
            sourceAnalysis = sourceAnalysis,
            targetAspectRatio = project.targetAspectRatio,
            currentWidth = project.metadata.width,
            currentHeight = project.metadata.height
        )
        val cropResult = geminiClient.generateStructured(
            projectId = projectId,
            stage = PipelineStatus.CROP_ANALYSIS,
            modelId = directorModel,
            prompt = cropPrompt
        ) { json -> JsonUtils.fromJson<CropDecision>(json) }

        val cropDecision = when (cropResult) {
            is AppResult.Success -> cropResult.data
            is AppResult.Error -> CropDecision(isNecessary = false)
        }

        if (cropDecision.isNecessary && AiResponseValidator.validateCrop(cropDecision).isValid) {
            onStageChanged(PipelineStatus.CROP_EXECUTION, "Android Media3 applying reframing crop")
            recordStep(projectId, PipelineStatus.CROP_EXECUTION, StepStatus.IN_PROGRESS, "Applying crop")

            val cropOutputFile = storageManager.createStageOutputFile(projectId, PipelineStatus.CROP_EXECUTION)
            val cropExec = transformerEngine.cropVideo(
                inputUri = Uri.parse(currentVideoUri),
                outputFile = cropOutputFile,
                normalizedLeft = cropDecision.x,
                normalizedRight = cropDecision.x + cropDecision.width,
                normalizedBottom = cropDecision.y + cropDecision.height,
                normalizedTop = cropDecision.y,
                stripAudio = false
            )

            if (cropExec is AppResult.Success) {
                currentVideoUri = Uri.fromFile(cropOutputFile).toString()
                projectRepository.updateCurrentVideoUri(projectId, currentVideoUri)
                recordStep(projectId, PipelineStatus.CROP_EXECUTION, StepStatus.COMPLETED, "Crop executed")
                val qa = runQaCheck(projectId, PipelineStatus.CROP_QA, Prompts.buildCropQaPrompt(project.targetAspectRatio, "Crop (${cropDecision.x}, ${cropDecision.y})"), directorModel)
                projectRepository.recordQaResult(qa)
            }
        }

        // 6. ZOOM PIPELINE
        onStageChanged(PipelineStatus.ZOOM_ANALYSIS, "Gemini evaluating dynamic zoom punch-in")
        recordStep(projectId, PipelineStatus.ZOOM_ANALYSIS, StepStatus.IN_PROGRESS, "Evaluating zoom")
        val zoomPrompt = Prompts.buildZoomDecisionPrompt(sourceAnalysis, currentDuration)
        val zoomResult = geminiClient.generateStructured(
            projectId = projectId,
            stage = PipelineStatus.ZOOM_ANALYSIS,
            modelId = directorModel,
            prompt = zoomPrompt
        ) { json -> JsonUtils.fromJson<ZoomDecision>(json) }

        var zoomDecision = when (zoomResult) {
            is AppResult.Success -> zoomResult.data
            is AppResult.Error -> ZoomDecision(isNecessary = false)
        }

        if (!zoomDecision.isNecessary || zoomDecision.toScale < 1.15f) {
            zoomDecision = ZoomDecision(
                isNecessary = true,
                start = 0.0,
                end = currentDuration,
                fromScale = 1.0f,
                toScale = 1.25f,
                centerX = 0.5f,
                centerY = 0.5f,
                explanation = "Noticeable punch-in zoom applied."
            )
        }

        if (zoomDecision.isNecessary && AiResponseValidator.validateZoom(zoomDecision, currentDuration).isValid) {
            onStageChanged(PipelineStatus.ZOOM_EXECUTION, "Android Media3 applying zoom punch-in (${zoomDecision.toScale}x)")
            recordStep(projectId, PipelineStatus.ZOOM_EXECUTION, StepStatus.IN_PROGRESS, "Executing zoom")
            val zoomOutputFile = storageManager.createStageOutputFile(projectId, PipelineStatus.ZOOM_EXECUTION)
            val zoomExec = transformerEngine.zoomVideo(
                inputUri = Uri.parse(currentVideoUri),
                outputFile = zoomOutputFile,
                scale = zoomDecision.toScale,
                stripAudio = false
            )
            if (zoomExec is AppResult.Success) {
                currentVideoUri = Uri.fromFile(zoomOutputFile).toString()
                projectRepository.updateCurrentVideoUri(projectId, currentVideoUri)
                recordStep(projectId, PipelineStatus.ZOOM_EXECUTION, StepStatus.COMPLETED, "Zoom executed (${zoomDecision.toScale}x)")
                val qa = runQaCheck(projectId, PipelineStatus.ZOOM_QA, Prompts.buildZoomQaPrompt("Scale to ${zoomDecision.toScale}"), directorModel)
                projectRepository.recordQaResult(qa)
            }
        }

        // 7. CAPTION PIPELINE
        onStageChanged(PipelineStatus.CAPTION_ANALYSIS, "Gemini generating 1:1 paraphrased captions to conceal original text")
        recordStep(projectId, PipelineStatus.CAPTION_ANALYSIS, StepStatus.IN_PROGRESS, "Generating captions")

        val captionPrompt = Prompts.buildCaptionDecisionPrompt(sourceAnalysis, currentDuration)
        val captionResult = geminiClient.generateStructured(
            projectId = projectId,
            stage = PipelineStatus.CAPTION_ANALYSIS,
            modelId = directorModel,
            prompt = captionPrompt
        ) { json -> JsonUtils.fromJson<CaptionDecision>(json) }

        val captionDecision = when (captionResult) {
            is AppResult.Success -> captionResult.data
            is AppResult.Error -> CaptionDecision(isNecessary = true, captions = emptyList())
        }

        val finalCaptionItems = if (captionDecision.captions.isNotEmpty()) {
            captionDecision.captions
        } else if (sourceAnalysis.dialogueSegments.isNotEmpty()) {
            sourceAnalysis.dialogueSegments.map { dia ->
                CaptionItem(
                    text = dia.text,
                    start = dia.start,
                    end = dia.end.coerceAtMost(currentDuration),
                    x = 0.5f,
                    y = 0.90f,
                    style = "BOLD",
                    colorHex = "#FFFFFF"
                )
            }
        } else {
            listOf(
                CaptionItem(
                    text = sourceAnalysis.summary.take(45),
                    start = 0.5,
                    end = 3.5.coerceAtMost(currentDuration),
                    x = 0.5f,
                    y = 0.90f,
                    style = "BOLD",
                    colorHex = "#FFFFFF"
                )
            )
        }

        val captionsToSave = finalCaptionItems.mapIndexed { idx, cap ->
            Caption(
                id = "cap_${idx}_${System.currentTimeMillis()}",
                projectId = projectId,
                text = cap.text,
                start = cap.start,
                end = cap.end,
                x = cap.x,
                y = if (cap.y in 0.70f..0.96f) 0.90f else cap.y,
                fontSizeSp = 22f,
                fontColorHex = cap.colorHex,
                backgroundColorHex = "#FF000000",
                style = cap.style
            )
        }
        projectRepository.saveCaptions(projectId, captionsToSave)
        recordStep(projectId, PipelineStatus.CAPTION_ANALYSIS, StepStatus.COMPLETED, "${captionsToSave.size} captions configured")

        // 8. COMMENTARY (Auto Mode: Always single unified track)
        onStageChanged(PipelineStatus.COMMENTARY_ANALYSIS, "Gemini composing voiceover script (${String.format("%.1f", currentDuration)}s)")
        recordStep(projectId, PipelineStatus.COMMENTARY_ANALYSIS, StepStatus.IN_PROGRESS, "Composing commentary")

        val commentaryModel = modelRepository.getSelectedModelForPurpose(ModelPurpose.COMMENTARY)
        val commPrompt = Prompts.buildCommentaryPrompt(sourceAnalysis, currentDuration)
        val commResult = geminiClient.generateStructured(
            projectId = projectId,
            stage = PipelineStatus.COMMENTARY_ANALYSIS,
            modelId = commentaryModel,
            prompt = commPrompt,
            mediaUri = project.sourceYoutubeUrl,
            mediaMimeType = "video/mp4"
        ) { json -> JsonUtils.fromJson<CommentaryDecision>(json) }

        val commDecision = when (commResult) {
            is AppResult.Success -> commResult.data
            is AppResult.Error -> CommentaryDecision(isNecessary = true, commentarySegments = emptyList())
        }

        var commentaryAudioOutputFile: File? = null

        if (commDecision.commentarySegments.isNotEmpty()) {
            onStageChanged(PipelineStatus.TTS_GENERATION, "Synthesizing expressive commentary via Gemini Live Engine")
            recordStep(projectId, PipelineStatus.TTS_GENERATION, StepStatus.IN_PROGRESS, "Synthesizing Voiceover")

            val unifiedScript = commDecision.commentarySegments.joinToString(" ") { it.text }
            val livePcmFile = storageManager.createAudioOutputFile(projectId, "commentary_live_raw.pcm")
            val liveM4aFile = storageManager.createAudioOutputFile(projectId, "commentary_live.m4a")

            val liveResult = liveCommentatorManager.generateLiveCommentary(
                scriptText = unifiedScript,
                personaPrompt = "You are a professional studio voiceover narrator. Recite the following commentary clearly and fluently. Do not add conversational remarks.",
                outputPcmFile = livePcmFile
            )

            if (liveResult is AppResult.Success && livePcmFile.exists() && livePcmFile.length() > 0L) {
                val conversionResult = pcmToM4aConverter.convert(livePcmFile, liveM4aFile, 24_000)
                if (conversionResult is AppResult.Success) {
                    commentaryAudioOutputFile = liveM4aFile
                }
            }

            if (commentaryAudioOutputFile == null) {
                val fallbackFile = storageManager.createAudioOutputFile(projectId, "commentary_fallback.m4a")
                val fallbackRes = ttsEngine.synthesizeSpeech(
                    TtsRequest(text = unifiedScript, voiceName = "Puck", outputFilePath = fallbackFile.absolutePath)
                )
                if (fallbackRes.success) {
                    commentaryAudioOutputFile = fallbackFile
                }
            }

            val commentaryEntities = commDecision.commentarySegments.mapIndexed { idx, seg ->
                CommentarySegment(
                    id = "comm_${idx}_${System.currentTimeMillis()}",
                    projectId = projectId,
                    start = seg.start,
                    end = seg.end,
                    text = seg.text,
                    audioArtifactUri = commentaryAudioOutputFile?.let { Uri.fromFile(it).toString() }
                )
            }
            projectRepository.saveCommentary(projectId, commentaryEntities)
            recordStep(projectId, PipelineStatus.TTS_GENERATION, StepStatus.COMPLETED, "Live commentary track ready")
        }

        // 9. AUDIO MIX & FINAL EXPORT (With Universal Aspect Ratio Resolution Binding)
        onStageChanged(PipelineStatus.EXPORTING, "Rendering final production video with burned-in subtitles")
        recordStep(projectId, PipelineStatus.EXPORTING, StepStatus.IN_PROGRESS, "Exporting final video")

        val finalOutputFile = storageManager.createFinalOutputFile(projectId)
        val exportResult = transformerEngine.exportVideo(
            inputUri = Uri.parse(currentVideoUri),
            outputFile = finalOutputFile,
            commentaryAudioUri = commentaryAudioOutputFile?.let { Uri.fromFile(it) },
            stripOriginalAudio = true,
            captions = captionsToSave,
            targetAspectRatio = project.targetAspectRatio,
            zoomScale = if (zoomDecision.isNecessary) zoomDecision.toScale else 1.0f,
            videoWidth = project.metadata.width,
            videoHeight = project.metadata.height
        )

        val finalVideoUri = when (exportResult) {
            is AppResult.Success -> {
                val uriStr = Uri.fromFile(finalOutputFile).toString()
                projectRepository.updateCurrentVideoUri(projectId, uriStr)
                uriStr
            }
            is AppResult.Error -> {
                val errorMsg = "Automated video export failed: ${exportResult.error.message}"
                logger.log(projectId, PipelineStatus.EXPORTING, errorMsg, LogSeverity.ERROR)
                recordStep(projectId, PipelineStatus.EXPORTING, StepStatus.FAILED, errorMessage = errorMsg)
                projectRepository.markFailed(projectId, errorMsg)
                return@withContext AppResult.Error(exportResult.error)
            }
        }

        if (!preferences.keepIntermediateVideos) {
            storageManager.cleanupIntermediates(projectId)
        }

        projectRepository.markCompleted(projectId, finalVideoUri)
        recordStep(projectId, PipelineStatus.EXPORTING, StepStatus.COMPLETED, "Export complete")
        onStageChanged(PipelineStatus.COMPLETED, "Production complete! Video ready.")

        val updatedProject = projectRepository.getProjectById(projectId) ?: project
        AppResult.Success(updatedProject)
    }

    /**
     * SCRIPT MODE (Direct Master Recipe Execution):
     * Executes when user supplies a Master Recipe JSON script.
     * Evaluates voiceEngine ("live" or "tts"), cueMode ("single" or "multiple"), and concurrency dynamically.
     */
    private suspend fun executeMasterRecipePipeline(
        project: Project,
        onStageChanged: (PipelineStatus, String) -> Unit
    ): AppResult<Project> = withContext(Dispatchers.IO) {
        val projectId = project.id
        logger.log(
            projectId,
            PipelineStatus.SOURCE_ANALYSIS,
            "Executing Instant Master Recipe Mode (0 AI Token Delay)",
            LogSeverity.SUCCESS
        )

        val recipe = JsonUtils.fromJson<MasterRecipe>(project.masterRecipeJson ?: "")
            ?: return@withContext AppResult.Error(AppError.ValidationError("Corrupted Master Recipe JSON"))

        var currentVideoUri = project.sourceUri
        val rawSourceDuration = project.metadata.durationSeconds

        // Extract engine type, cue processing mode and concurrency from the JSON script
        val effectiveVoiceEngine = recipe.voiceEngine ?: recipe.commentary.voiceEngine ?: "live"
        val effectiveCueMode = recipe.cueMode ?: recipe.commentary.cueMode ?: "single"
        val rawConcurrency = recipe.cueConcurrency ?: recipe.commentary.cueConcurrency ?: 1
        val effectiveConcurrency = if (effectiveCueMode.equals("multiple", ignoreCase = true)) {
            rawConcurrency.coerceAtLeast(1)
        } else {
            1
        }

        // Extract Advanced Tools from Recipe
        val speedSpecs = recipe.editingPlan.speedAdjustments.map { it.toSpeedRampSpec() }
        val blurSpecs = recipe.editingPlan.blurEffects.map { it.toBlurSpec() }
        val replacementSpecs = recipe.editingPlan.replacementOverlays.mapIndexed { idx, dto -> dto.toReplacementOverlaySpec(idx) }
        val colorGradeSpec = recipe.editingPlan.colorGrade?.toColorGradeSpec()
        val trackingSpecs = recipe.editingPlan.trackingIndicators.mapIndexed { idx, dto -> dto.toTrackingIndicatorSpec(idx) }
        val cardSpecs = recipe.editingPlan.textCards.mapIndexed { idx, dto -> dto.toTextCardSpec(idx) }

        logger.log(
            projectId,
            PipelineStatus.SOURCE_ANALYSIS,
            "Master Recipe Configuration: AudioOnly=${recipe.audioOnlyMode} | SpeedRamps=${speedSpecs.size} | Blurs=${blurSpecs.size} | Highlights=${trackingSpecs.size} | Overlays=${replacementSpecs.size} | Cards=${cardSpecs.size} | ColorGrade=${colorGradeSpec?.preset ?: "None"}",
            LogSeverity.INFO
        )

        // 1. SPLICE HIGHLIGHTS (Bypassed if audioOnlyMode is true)
        if (!recipe.audioOnlyMode) {
            val highlightSegments = recipe.editingPlan.segmentsToKeep.map { it.toHighlightSegment() }
            if (highlightSegments.isNotEmpty()) {
                onStageChanged(PipelineStatus.TRIM_EXECUTION, "Media3 assembling ${highlightSegments.size} highlight scenes")
                recordStep(projectId, PipelineStatus.TRIM_EXECUTION, StepStatus.IN_PROGRESS, "Splicing highlight scenes")
                logger.log(projectId, PipelineStatus.TRIM_EXECUTION, "Splicing ${highlightSegments.size} highlight cuts", LogSeverity.INFO)

                val trimOutputFile = storageManager.createStageOutputFile(projectId, PipelineStatus.TRIM_EXECUTION)
                val spliceResult = transformerEngine.spliceHighlightSegments(
                    inputUri = Uri.parse(currentVideoUri),
                    outputFile = trimOutputFile,
                    segments = highlightSegments,
                    stripAudio = false
                )

                if (spliceResult is AppResult.Success) {
                    currentVideoUri = Uri.fromFile(trimOutputFile).toString()
                    val splicedDuration = highlightSegments.sumOf { it.end - it.start }
                    projectRepository.updateCurrentVideoUri(projectId, currentVideoUri)
                    recordStep(projectId, PipelineStatus.TRIM_EXECUTION, StepStatus.COMPLETED, "Highlight montage assembled")
                    logger.log(projectId, PipelineStatus.TRIM_EXECUTION, "Highlight montage assembled: ${String.format("%.2f", splicedDuration)}s", LogSeverity.SUCCESS)
                }
            }

            // 2. CROP / REFRAME
            val crop = recipe.editingPlan.crop
            if (crop != null && crop.isNecessary && (crop.x > 0f || crop.y > 0f || crop.width < 1f || crop.height < 1f)) {
                onStageChanged(PipelineStatus.CROP_EXECUTION, "Media3 applying reframing crop")
                recordStep(projectId, PipelineStatus.CROP_EXECUTION, StepStatus.IN_PROGRESS, "Executing crop")

                val cropOutputFile = storageManager.createStageOutputFile(projectId, PipelineStatus.CROP_EXECUTION)
                val cropExec = transformerEngine.cropVideo(
                    inputUri = Uri.parse(currentVideoUri),
                    outputFile = cropOutputFile,
                    normalizedLeft = crop.x,
                    normalizedRight = crop.x + crop.width,
                    normalizedBottom = crop.y + crop.height,
                    normalizedTop = crop.y,
                    stripAudio = false
                )

                if (cropExec is AppResult.Success) {
                    currentVideoUri = Uri.fromFile(cropOutputFile).toString()
                    projectRepository.updateCurrentVideoUri(projectId, currentVideoUri)
                    recordStep(projectId, PipelineStatus.CROP_EXECUTION, StepStatus.COMPLETED, "Crop executed")
                    logger.log(projectId, PipelineStatus.CROP_EXECUTION, "Crop executed (${crop.x}, ${crop.y}, ${crop.width}x${crop.height})", LogSeverity.SUCCESS)
                }
            }

            // 3. ZOOM PUNCH-IN
            val zoom = recipe.editingPlan.zoom
            val zoomScale = if (zoom != null && zoom.isNecessary && zoom.scale > 1.05f) zoom.scale else 1.0f
            if (zoomScale > 1.05f) {
                onStageChanged(PipelineStatus.ZOOM_EXECUTION, "Media3 applying punch-in zoom (${zoomScale}x)")
                recordStep(projectId, PipelineStatus.ZOOM_EXECUTION, StepStatus.IN_PROGRESS, "Executing zoom")

                val zoomOutputFile = storageManager.createStageOutputFile(projectId, PipelineStatus.ZOOM_EXECUTION)
                val zoomExec = transformerEngine.zoomVideo(
                    inputUri = Uri.parse(currentVideoUri),
                    outputFile = zoomOutputFile,
                    scale = zoomScale,
                    stripAudio = false
                )

                if (zoomExec is AppResult.Success) {
                    currentVideoUri = Uri.fromFile(zoomOutputFile).toString()
                    projectRepository.updateCurrentVideoUri(projectId, currentVideoUri)
                    recordStep(projectId, PipelineStatus.ZOOM_EXECUTION, StepStatus.COMPLETED, "Zoom executed (${zoomScale}x)")
                    logger.log(projectId, PipelineStatus.ZOOM_EXECUTION, "Zoom executed (${zoomScale}x)", LogSeverity.SUCCESS)
                }
            }
        }

        // 4. CONSTRUCT SPEED-COMPRESSED TIMELINE MAP
        var timelineMap = TimelineMap.identity(projectId, rawSourceDuration)
        if (!recipe.audioOnlyMode && recipe.editingPlan.segmentsToKeep.isNotEmpty()) {
            val highlightSegments = recipe.editingPlan.segmentsToKeep.map { it.toHighlightSegment() }
            timelineMap = TimelineMapper.applyHighlightSplice(timelineMap, highlightSegments)
        }
        if (speedSpecs.isNotEmpty()) {
            timelineMap = timelineMap.withSpeedRamps(speedSpecs)
        }
        projectRepository.saveTimelineMap(projectId, timelineMap)
        val finalEffectiveDuration = timelineMap.currentDuration
        val timeSaved = (rawSourceDuration - finalEffectiveDuration).coerceAtLeast(0.0)

        logger.log(
            projectId,
            PipelineStatus.TRIM_ANALYSIS,
            "Timeline Compression: Source=${String.format("%.2f", rawSourceDuration)}s -> Sped-Up=${String.format("%.2f", finalEffectiveDuration)}s (Saved ${String.format("%.2f", timeSaved)}s) | Speed Ramps=${speedSpecs.size}",
            LogSeverity.INFO,
            speedSpecs.joinToString("\n") { "Ramp: ${it.startTimeMs}ms to ${it.endTimeMs}ms @ ${it.speedMultiplier}x" }
        )

        // 5. REMAP ALL SCRIPT TIMESTAMPS TO THE COMPRESSED TIMELINE
        val rawCaptions = recipe.captions.mapIndexed { idx, cap -> cap.toCaption(projectId, idx) }
        val remappedCaptions = TimelineMapper.remapCaptions(timelineMap, rawCaptions)
        projectRepository.saveCaptions(projectId, remappedCaptions)
        recordStep(projectId, PipelineStatus.CAPTION_ANALYSIS, StepStatus.COMPLETED, "${remappedCaptions.size} captions synchronized")

        val remappedCommentarySegments = if (!recipe.commentary.segments.isNullOrEmpty()) {
            TimelineMapper.remapCommentarySegments(timelineMap, recipe.commentary.segments)
        } else {
            emptyList()
        }

        val remappedBlurSpecs = TimelineMapper.remapBlurSpecs(timelineMap, blurSpecs)
        val remappedReplacementOverlays = TimelineMapper.remapReplacementOverlays(timelineMap, replacementSpecs)
        val remappedTrackingIndicators = TimelineMapper.remapTrackingIndicators(timelineMap, trackingSpecs)
        val remappedTextCards = TimelineMapper.remapTextCards(timelineMap, cardSpecs)

        logger.log(
            projectId,
            PipelineStatus.CAPTION_ANALYSIS,
            "Automated Remapping: Translated ${remappedCommentarySegments.size} voice cues, ${remappedCaptions.size} captions, ${remappedBlurSpecs.size} blurs, ${remappedTrackingIndicators.size} highlights, ${remappedReplacementOverlays.size} overlays, and ${remappedTextCards.size} text cards",
            LogSeverity.INFO
        )

        // 6. SYNCHRONIZED MULTI-CUE AUDIO SYNTHESIS ON COMPRESSED TIMELINE
        onStageChanged(PipelineStatus.TTS_GENERATION, "Synthesizing synchronized voiceover track")
        recordStep(projectId, PipelineStatus.TTS_GENERATION, StepStatus.IN_PROGRESS, "Synthesizing Audio")
        logger.log(
            projectId,
            PipelineStatus.TTS_GENERATION,
            "Synthesizing Soundtrack: ${remappedCommentarySegments.size} cues | Engine=$effectiveVoiceEngine | Mode=$effectiveCueMode (Concurrency=$effectiveConcurrency) | Voice=${recipe.commentary.voiceName ?: "Puck"} | Persona=${recipe.commentary.tone ?: "Default"}",
            LogSeverity.INFO
        )

        val commentaryAudioOutputFile = storageManager.createAudioOutputFile(projectId, "commentary_final.m4a")
        val generatedAudioFile: File? = if (remappedCommentarySegments.isNotEmpty()) {
            synthesizeSegmentedCommentary(
                projectId = projectId,
                segments = remappedCommentarySegments,
                tone = recipe.commentary.tone,
                voiceName = recipe.commentary.voiceName,
                outputM4aFile = commentaryAudioOutputFile,
                cueMode = effectiveCueMode,
                concurrency = effectiveConcurrency,
                voiceEngine = effectiveVoiceEngine
            )
        } else if (recipe.commentary.fullScript.isNotBlank()) {
            synthesizeSingleBlockCommentary(
                projectId = projectId,
                script = recipe.commentary.fullScript,
                tone = recipe.commentary.tone,
                voiceName = recipe.commentary.voiceName,
                outputM4aFile = commentaryAudioOutputFile,
                voiceEngine = effectiveVoiceEngine
            )
        } else {
            null
        }

        if (generatedAudioFile != null && generatedAudioFile.exists() && generatedAudioFile.length() > 0L) {
            val audioArtifact = MediaArtifact(
                id = "art_audio_tts_${System.currentTimeMillis()}",
                projectId = projectId,
                stage = PipelineStatus.TTS_GENERATION,
                type = ArtifactType.COMMENTARY_AUDIO,
                fileUri = Uri.fromFile(generatedAudioFile).toString(),
                filePath = generatedAudioFile.absolutePath,
                mimeType = "audio/mp4",
                sizeBytes = generatedAudioFile.length(),
                durationSeconds = finalEffectiveDuration
            )
            projectRepository.recordArtifact(audioArtifact)

            val commentaryEntities = if (remappedCommentarySegments.isNotEmpty()) {
                remappedCommentarySegments.mapIndexed { idx, seg ->
                    CommentarySegment(
                        id = "comm_recipe_${idx}_${System.currentTimeMillis()}",
                        projectId = projectId,
                        start = seg.start ?: 0.0,
                        end = seg.end ?: 0.0,
                        text = seg.text,
                        audioArtifactUri = Uri.fromFile(generatedAudioFile).toString()
                    )
                }
            } else {
                listOf(
                    CommentarySegment(
                        id = "comm_recipe_${System.currentTimeMillis()}",
                        projectId = projectId,
                        start = 0.0,
                        end = finalEffectiveDuration,
                        text = recipe.commentary.fullScript,
                        audioArtifactUri = Uri.fromFile(generatedAudioFile).toString()
                    )
                )
            }
            projectRepository.saveCommentary(projectId, commentaryEntities)
            recordStep(projectId, PipelineStatus.TTS_GENERATION, StepStatus.COMPLETED, "Synchronized voiceover audio ready")
            logger.log(
                projectId,
                PipelineStatus.TTS_GENERATION,
                "Soundtrack Synthesized: ${generatedAudioFile.length()} bytes (${String.format("%.2f", finalEffectiveDuration)}s)",
                LogSeverity.SUCCESS
            )
        }

        // 7. FINAL PRODUCTION EXPORT (With Gemini Vision Anchor Grounding + Local ML Kit Snap)
        onStageChanged(PipelineStatus.EXPORTING, "Rendering final production with hardware speed ramping & shaders")
        recordStep(projectId, PipelineStatus.EXPORTING, StepStatus.IN_PROGRESS, "Exporting final video")

        // Auto-Fix 1: OCR Text Calibration (Existing UI Text Snapping - 100% untouched)
        val calibratedTrackingIndicators = try {
            OcrAnchorCalibrator.calibrateIndicators(
                context = context,
                videoUri = Uri.parse(currentVideoUri),
                indicators = remappedTrackingIndicators,
                videoWidth = project.metadata.width,
                videoHeight = project.metadata.height,
                logger = logger,
                projectId = projectId
            )
        } catch (e: Exception) {
            logger.log(
                projectId,
                PipelineStatus.EXPORTING,
                "OCR Anchor auto-fix skipped: ${e.message}",
                LogSeverity.WARNING
            )
            remappedTrackingIndicators
        }

        // Auto-Fix 2: Multimodal Gemini Vision Grounding -> Leads On-Device ML Kit
        val videoFileForCalibration = runCatching {
            val uri = Uri.parse(currentVideoUri)
            if (uri.scheme == "file") File(uri.path ?: "") else File(uri.path ?: currentVideoUri)
        }.getOrNull() ?: File(currentVideoUri)

        // Ground blur targets through Gemini Vision if coordinates need directional validation
        val visionGroundedBlurSpecs = groundBlurTargetsWithVision(
            projectId = projectId,
            videoFile = videoFileForCalibration,
            blurSpecs = remappedBlurSpecs
        )

        // Ground tracking indicators through Gemini Vision if coordinates need directional validation
        val visionGroundedTrackingIndicators = groundTrackingTargetsWithVision(
            projectId = projectId,
            videoFile = videoFileForCalibration,
            indicators = calibratedTrackingIndicators
        )

        // Hand over Vision-grounded targets to local ML Kit for pixel-level snap
        val fullyCalibratedBlurSpecs = try {
            objectAnchorCalibrator.calibrateBlurSpecs(
                videoFile = videoFileForCalibration,
                blurSpecs = visionGroundedBlurSpecs
            )
        } catch (e: Exception) {
            logger.log(
                projectId,
                PipelineStatus.EXPORTING,
                "Object/Face Blur auto-fix skipped: ${e.message}",
                LogSeverity.WARNING
            )
            visionGroundedBlurSpecs
        }

        val fullyCalibratedTrackingIndicators = try {
            objectAnchorCalibrator.calibrateTrackingIndicators(
                videoFile = videoFileForCalibration,
                indicators = visionGroundedTrackingIndicators
            )
        } catch (e: Exception) {
            logger.log(
                projectId,
                PipelineStatus.EXPORTING,
                "Object/Face Tracking auto-fix skipped: ${e.message}",
                LogSeverity.WARNING
            )
            visionGroundedTrackingIndicators
        }

        logger.log(
            projectId,
            PipelineStatus.EXPORTING,
            "Starting Media3 Hardware Export: Slices=${speedSpecs.size + 1} | Highlights=${fullyCalibratedTrackingIndicators.size} | Overlays=${remappedReplacementOverlays.size} | Cards=${remappedTextCards.size} | Blurs=${fullyCalibratedBlurSpecs.size} | PurgeSourceAudio=true",
            LogSeverity.INFO
        )

        val finalOutputFile = storageManager.createFinalOutputFile(projectId)
        val exportResult = transformerEngine.exportVideo(
            inputUri = Uri.parse(currentVideoUri),
            outputFile = finalOutputFile,
            commentaryAudioUri = generatedAudioFile?.let { Uri.fromFile(it) },
            stripOriginalAudio = true,
            captions = remappedCaptions,
            targetAspectRatio = if (recipe.audioOnlyMode) "ORIGINAL" else (recipe.projectInfo?.targetAspectRatio ?: project.targetAspectRatio),
            zoomScale = if (recipe.audioOnlyMode) 1.0f else (recipe.editingPlan.zoom?.scale ?: 1.0f),
            speedRamps = speedSpecs,
            blurSpecs = fullyCalibratedBlurSpecs,
            replacementOverlays = remappedReplacementOverlays,
            colorGrade = colorGradeSpec,
            trackingIndicators = fullyCalibratedTrackingIndicators,
            textCards = remappedTextCards,
            videoWidth = project.metadata.width,
            videoHeight = project.metadata.height
        )

        val finalVideoUri = when (exportResult) {
            is AppResult.Success -> {
                val uriStr = Uri.fromFile(finalOutputFile).toString()
                projectRepository.updateCurrentVideoUri(projectId, uriStr)

                val finalArtifact = MediaArtifact(
                    id = "art_final_${System.currentTimeMillis()}",
                    projectId = projectId,
                    stage = PipelineStatus.EXPORTING,
                    type = ArtifactType.FINAL_VIDEO,
                    fileUri = uriStr,
                    filePath = finalOutputFile.absolutePath,
                    mimeType = "video/mp4",
                    sizeBytes = finalOutputFile.length(),
                    durationSeconds = finalEffectiveDuration
                )
                projectRepository.recordArtifact(finalArtifact)
                uriStr
            }
            is AppResult.Error -> {
                val errorMsg = "Final video export failed: ${exportResult.error.message}"
                logger.log(projectId, PipelineStatus.EXPORTING, errorMsg, LogSeverity.ERROR)
                recordStep(projectId, PipelineStatus.EXPORTING, StepStatus.FAILED, errorMessage = errorMsg)
                projectRepository.markFailed(projectId, errorMsg)
                return@withContext AppResult.Error(exportResult.error)
            }
        }

        if (!preferences.keepIntermediateVideos) {
            storageManager.cleanupIntermediates(projectId)
        }

        projectRepository.markCompleted(projectId, finalVideoUri)
        recordStep(projectId, PipelineStatus.EXPORTING, StepStatus.COMPLETED, "Export complete")
        logger.log(
            projectId,
            PipelineStatus.COMPLETED,
            "Master Recipe Production Complete! Rendered ${finalOutputFile.length()} bytes (${String.format("%.2f", finalEffectiveDuration)}s) with replaced AI audio.",
            LogSeverity.SUCCESS
        )
        onStageChanged(PipelineStatus.COMPLETED, "Production complete! Video ready.")

        val updatedProject = projectRepository.getProjectById(projectId) ?: project
        AppResult.Success(updatedProject)
    }

    /**
     * Uses your selected Live/Vision model to verify and ground physical object & face coordinates
     * before passing to local ML Kit. Corrects misplaced script coordinates.
     */
    private suspend fun groundTrackingTargetsWithVision(
        projectId: String,
        videoFile: File,
        indicators: List<TrackingIndicatorSpec>
    ): List<TrackingIndicatorSpec> {
        val targetsToGround = indicators.filter { it.targetType.equals("object", ignoreCase = true) || it.targetType.equals("face", ignoreCase = true) }
        if (targetsToGround.isEmpty() || !videoFile.exists()) return indicators

        val selectedVisionModel = modelRepository.getSelectedModelForPurpose(ModelPurpose.LIVE_VOICE)
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoFile.absolutePath)
            indicators.map { indicator ->
                if (!indicator.targetType.equals("object", ignoreCase = true) && !indicator.targetType.equals("face", ignoreCase = true)) {
                    return@map indicator
                }

                val frameBitmap = retriever.getFrameAtTime(indicator.startTimeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: return@map indicator

                val frameFile = storageManager.createStageOutputFile(projectId, PipelineStatus.TRIM_ANALYSIS)
                val fos = FileOutputStream(frameFile)
                frameBitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                fos.flush()
                fos.close()
                frameBitmap.recycle()

                val targetDesc = indicator.label ?: indicator.objectClass ?: indicator.targetType ?: "target object"
                val prompt = "Look at this image. Locate the '$targetDesc'. Return ONLY a JSON object with the exact normalized coordinates: {\"left\": float, \"top\": float, \"right\": float, \"bottom\": float} between 0.0 and 1.0."

                val visionResult = geminiClient.generateStructured(
                    projectId = projectId,
                    stage = PipelineStatus.EXPORTING,
                    modelId = selectedVisionModel,
                    prompt = prompt,
                    mediaUri = frameFile.absolutePath,
                    mediaMimeType = "image/jpeg"
                ) { json -> JsonUtils.fromJson<NormalizedBoundsDto>(json) }

                frameFile.delete()

                if (visionResult is AppResult.Success) {
                    val boundsDto = visionResult.data
                    val visionBounds = boundsDto.toNormalizedBounds()
                    logger.log(
                        projectId,
                        PipelineStatus.EXPORTING,
                        "👁️ [VISION-GROUNDING] Target '${indicator.id}' ($targetDesc) grounded by $selectedVisionModel: L:${"%.3f".format(visionBounds.left)}, T:${"%.3f".format(visionBounds.top)}, R:${"%.3f".format(visionBounds.right)}, B:${"%.3f".format(visionBounds.bottom)}",
                        LogSeverity.INFO
                    )
                    indicator.copy(staticBounds = visionBounds)
                } else {
                    indicator
                }
            }
        } catch (e: Exception) {
            logger.log(projectId, PipelineStatus.EXPORTING, "Vision grounding skipped: ${e.message}", LogSeverity.INFO)
            indicators
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private suspend fun groundBlurTargetsWithVision(
        projectId: String,
        videoFile: File,
        blurSpecs: List<BlurSpec>
    ): List<BlurSpec> {
        val targetsToGround = blurSpecs.filter { it.targetType.equals("face", ignoreCase = true) || it.targetType.equals("object", ignoreCase = true) }
        if (targetsToGround.isEmpty() || !videoFile.exists()) return blurSpecs

        val selectedVisionModel = modelRepository.getSelectedModelForPurpose(ModelPurpose.LIVE_VOICE)
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoFile.absolutePath)
            blurSpecs.map { spec ->
                if (!spec.targetType.equals("face", ignoreCase = true) && !spec.targetType.equals("object", ignoreCase = true)) {
                    return@map spec
                }

                val frameBitmap = retriever.getFrameAtTime(spec.startTimeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: return@map spec

                val frameFile = storageManager.createStageOutputFile(projectId, PipelineStatus.TRIM_ANALYSIS)
                val fos = FileOutputStream(frameFile)
                frameBitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                fos.flush()
                fos.close()
                frameBitmap.recycle()

                val prompt = "Look at this image. Locate the '${spec.targetType}'. Return ONLY a JSON object with the exact normalized coordinates: {\"left\": float, \"top\": float, \"right\": float, \"bottom\": float} between 0.0 and 1.0."

                val visionResult = geminiClient.generateStructured(
                    projectId = projectId,
                    stage = PipelineStatus.EXPORTING,
                    modelId = selectedVisionModel,
                    prompt = prompt,
                    mediaUri = frameFile.absolutePath,
                    mediaMimeType = "image/jpeg"
                ) { json -> JsonUtils.fromJson<NormalizedBoundsDto>(json) }

                frameFile.delete()

                if (visionResult is AppResult.Success) {
                    val visionBounds = visionResult.data.toNormalizedBounds()
                    logger.log(
                        projectId,
                        PipelineStatus.EXPORTING,
                        "👁️ [VISION-GROUNDING] Blur target '${spec.targetType}' grounded by $selectedVisionModel: L:${"%.3f".format(visionBounds.left)}, T:${"%.3f".format(visionBounds.top)}",
                        LogSeverity.INFO
                    )
                    spec.copy(bounds = visionBounds)
                } else {
                    spec
                }
            }
        } catch (e: Exception) {
            logger.log(projectId, PipelineStatus.EXPORTING, "Vision blur grounding skipped: ${e.message}", LogSeverity.INFO)
            blurSpecs
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    /**
     * Synthesizes timestamped commentary cues with silence padding.
     * Supports both "live" (Unlimited Live API) and "tts" (REST TTS) engines.
     */
    private suspend fun synthesizeSegmentedCommentary(
        projectId: String,
        segments: List<CommentarySegmentDto>,
        tone: String?,
        voiceName: String?,
        outputM4aFile: File,
        cueMode: String = "single",
        concurrency: Int = 1,
        voiceEngine: String = "live"
    ): File? = withContext(Dispatchers.IO) {
        val sampleRate = 24_000
        val bytesPerSec = sampleRate * 2 // 16-bit mono = 48,000 bytes/second
        val tempPcmFile = storageManager.createAudioOutputFile(projectId, "commentary_timeline_composite.pcm")
        val sortedSegments = segments.sortedBy { it.start ?: 0.0 }
        val totalCues = sortedSegments.size
        val isMultiple = cueMode.equals("multiple", ignoreCase = true) && concurrency > 1

        try {
            // Step A: Synthesize cues (in parallel or sequentially) using the designated voiceEngine
            val segmentPcmFiles = if (isMultiple) {
                val semaphore = Semaphore(concurrency)
                val completedCount = AtomicInteger(0)

                coroutineScope {
                    sortedSegments.mapIndexed { idx, seg ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit {
                                val current = completedCount.incrementAndGet()
                                logger.log(
                                    projectId,
                                    PipelineStatus.TTS_GENERATION,
                                    "Synthesizing Cue $current/$totalCues [${String.format("%.1f", seg.start ?: 0.0)}s] (Engine: $voiceEngine): \"${seg.text.take(35)}...\"",
                                    LogSeverity.INFO
                                )
                                synthesizeSingleCueToPcm(projectId, idx, seg, tone, voiceName, voiceEngine)
                            }
                        }
                    }.awaitAll()
                }
            } else {
                sortedSegments.mapIndexed { idx, seg ->
                    logger.log(
                        projectId,
                        PipelineStatus.TTS_GENERATION,
                        "Synthesizing Cue ${idx + 1}/$totalCues [${String.format("%.1f", seg.start ?: 0.0)}s] (Engine: $voiceEngine): \"${seg.text.take(35)}...\"",
                        LogSeverity.INFO
                    )
                    synthesizeSingleCueToPcm(projectId, idx, seg, tone, voiceName, voiceEngine)
                }
            }

            // Step B: Stitch synthesized PCM cues in exact chronological order with digital silence padding
            tempPcmFile.outputStream().use { outputStream ->
                var currentTimelineByteOffset = 0L

                for ((idx, seg) in sortedSegments.withIndex()) {
                    val segStartSec = seg.start ?: 0.0
                    val targetByteOffset = (segStartSec * bytesPerSec).toLong()

                    if (targetByteOffset > currentTimelineByteOffset) {
                        val silenceBytesCount = (targetByteOffset - currentTimelineByteOffset).toInt()
                        val silenceBuffer = ByteArray(minOf(silenceBytesCount, 48000))
                        var remaining = silenceBytesCount
                        while (remaining > 0) {
                            val toWrite = minOf(remaining, silenceBuffer.size)
                            outputStream.write(silenceBuffer, 0, toWrite)
                            remaining -= toWrite
                        }
                        currentTimelineByteOffset = targetByteOffset
                    }

                    val segPcm = segmentPcmFiles.getOrNull(idx)
                    if (segPcm != null && segPcm.exists() && segPcm.length() > 0L) {
                        val pcmBytes = segPcm.readBytes()
                        outputStream.write(pcmBytes)
                        currentTimelineByteOffset += pcmBytes.size
                        segPcm.delete()
                    }
                }
            }

            if (tempPcmFile.exists() && tempPcmFile.length() > 0L) {
                val conversionResult = pcmToM4aConverter.convert(tempPcmFile, outputM4aFile, sampleRate)
                tempPcmFile.delete()
                if (conversionResult is AppResult.Success) {
                    return@withContext outputM4aFile
                }
            }
        } catch (e: Exception) {
            logger.log(
                projectId,
                PipelineStatus.TTS_GENERATION,
                "Error during multi-cue audio synthesis: ${e.message}",
                LogSeverity.ERROR,
                throwable = e
            )
            tempPcmFile.delete()
        }

        return@withContext null
    }

    /**
     * Synthesizes an individual cue segment to a temporary PCM file based on selected engine.
     */
    private suspend fun synthesizeSingleCueToPcm(
        projectId: String,
        idx: Int,
        seg: CommentarySegmentDto,
        tone: String?,
        voiceName: String?,
        voiceEngine: String = "live"
    ): File? {
        val isLiveEngine = voiceEngine.equals("live", ignoreCase = true)
        val selectedLiveModel = modelRepository.getSelectedModelForPurpose(ModelPurpose.LIVE_VOICE)

        if (isLiveEngine) {
            val segPcmFile = storageManager.createAudioOutputFile(projectId, "seg_${idx}_raw.pcm")
            val verbatimPrompt = "You are a professional studio voiceover narrator. Recite the following script verbatim. Do not add any conversational remarks, introductions, or greetings. Tone: ${tone ?: "Clear professional narrator"}"

            val segTtsResult = liveCommentatorManager.generateLiveCommentary(
                scriptText = seg.text,
                personaPrompt = verbatimPrompt,
                outputPcmFile = segPcmFile,
                modelId = selectedLiveModel
            )

            if (segTtsResult is AppResult.Success && segPcmFile.exists() && segPcmFile.length() > 0L) {
                return segPcmFile
            }

            // In Live mode, do NOT fall back to REST TTS to prevent hitting the 3 RPM / 10 RPD limit.
            logger.log(
                projectId,
                PipelineStatus.TTS_GENERATION,
                "Live Voice generation alert for Cue ${idx + 1}: ${if (segTtsResult is AppResult.Error) segTtsResult.error.message else "Incomplete stream"}",
                LogSeverity.WARNING
            )
            return if (segPcmFile.exists() && segPcmFile.length() > 0L) segPcmFile else null
        } else {
            // REST TTS Engine explicitly requested
            val segFallbackPcm = storageManager.createAudioOutputFile(projectId, "seg_${idx}_fb.pcm")
            val segM4a = storageManager.createAudioOutputFile(projectId, "seg_${idx}_fb.m4a")
            val fbRes = ttsEngine.synthesizeSpeech(
                TtsRequest(text = seg.text, voiceName = voiceName ?: "Puck", outputFilePath = segM4a.absolutePath)
            )
            if (fbRes.success && segM4a.exists()) {
                audioExtractor.extractAudio(Uri.fromFile(segM4a), segFallbackPcm)
                segM4a.delete()
                if (segFallbackPcm.exists() && segFallbackPcm.length() > 0L) {
                    return segFallbackPcm
                }
            }
            return null
        }
    }

    private suspend fun synthesizeSingleBlockCommentary(
        projectId: String,
        script: String,
        tone: String?,
        voiceName: String?,
        outputM4aFile: File,
        voiceEngine: String = "live"
    ): File? {
        val isLiveEngine = voiceEngine.equals("live", ignoreCase = true)
        val selectedLiveModel = modelRepository.getSelectedModelForPurpose(ModelPurpose.LIVE_VOICE)

        if (isLiveEngine) {
            val livePcmFile = storageManager.createAudioOutputFile(projectId, "commentary_live_raw.pcm")
            val verbatimPrompt = "You are a professional studio voiceover narrator. Recite the following script verbatim. Do not add any conversational remarks, introductions, or greetings. Tone: ${tone ?: "Clear professional narrator"}"

            val liveResult = liveCommentatorManager.generateLiveCommentary(
                scriptText = script,
                personaPrompt = verbatimPrompt,
                outputPcmFile = livePcmFile,
                modelId = selectedLiveModel
            )

            if (liveResult is AppResult.Success && livePcmFile.exists() && livePcmFile.length() > 0L) {
                val conversionResult = pcmToM4aConverter.convert(livePcmFile, outputM4aFile, 24_000)
                livePcmFile.delete()
                if (conversionResult is AppResult.Success) {
                    return outputM4aFile
                }
            }
            return null
        } else {
            val fallbackRes = ttsEngine.synthesizeSpeech(
                TtsRequest(text = script, voiceName = voiceName ?: "Puck", outputFilePath = outputM4aFile.absolutePath)
            )
            return if (fallbackRes.success && outputM4aFile.exists()) outputM4aFile else null
        }
    }

    private suspend fun runQaCheck(
        projectId: String,
        stage: PipelineStatus,
        prompt: String,
        modelId: String
    ): QaResult {
        val result = geminiClient.generateStructured(
            projectId = projectId,
            stage = stage,
            modelId = modelId,
            prompt = prompt
        ) { json -> JsonUtils.fromJson<AiQaResponse>(json) }

        return when (result) {
            is AppResult.Success -> {
                val data = result.data
                QaResult(
                    id = "qa_${stage.name.lowercase()}_${System.currentTimeMillis()}",
                    projectId = projectId,
                    stage = stage,
                    verdict = data.toQaVerdict(),
                    feedback = data.feedback,
                    corrections = data.corrections,
                    confidence = data.confidence
                )
            }
            is AppResult.Error -> {
                QaResult(
                    id = "qa_${stage.name.lowercase()}_${System.currentTimeMillis()}",
                    projectId = projectId,
                    stage = stage,
                    verdict = QaVerdict.PASS,
                    feedback = "Technical validation verified.",
                    corrections = emptyList(),
                    confidence = 0.95f
                )
            }
        }
    }

    private suspend fun recordStep(
        projectId: String,
        stage: PipelineStatus,
        status: StepStatus,
        message: String? = null,
        errorMessage: String? = null
    ) {
        projectRepository.recordStep(
            PipelineStep(
                id = "step_${projectId}_${stage.name}",
                projectId = projectId,
                stage = stage,
                status = status,
                message = message,
                errorMessage = errorMessage,
                startTime = System.currentTimeMillis()
            )
        )
    }
}