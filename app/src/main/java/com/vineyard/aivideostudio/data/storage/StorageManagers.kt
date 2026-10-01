package com.vineyard.aivideostudio.data.storage

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.vineyard.aivideostudio.core.model.ArtifactType
import com.vineyard.aivideostudio.core.model.MediaArtifact
import com.vineyard.aivideostudio.core.model.PipelineStatus
import java.io.File
import java.util.UUID

class StorageManager(private val context: Context) {
    val baseProjectsDir: File
        get() {
            val dir = File(context.filesDir, "Projects")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    fun getProjectDirectory(projectId: String): File {
        val dir = File(baseProjectsDir, projectId)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getStageDirectory(projectId: String, stageName: String): File {
        val projectDir = getProjectDirectory(projectId)
        val stageDir = File(projectDir, "stages/$stageName")
        if (!stageDir.exists()) stageDir.mkdirs()
        return stageDir
    }

    fun getFinalDirectory(projectId: String): File {
        val projectDir = getProjectDirectory(projectId)
        val finalDir = File(projectDir, "final")
        if (!finalDir.exists()) finalDir.mkdirs()
        return finalDir
    }

    fun getAudioDirectory(projectId: String): File {
        val projectDir = getProjectDirectory(projectId)
        val audioDir = File(projectDir, "audio")
        if (!audioDir.exists()) audioDir.mkdirs()
        return audioDir
    }

    fun getSourceFile(projectId: String, extension: String = "mp4"): File {
        val projectDir = getProjectDirectory(projectId)
        val sourceDir = File(projectDir, "source")
        if (!sourceDir.exists()) sourceDir.mkdirs()
        return File(sourceDir, "source.$extension")
    }

    fun createIntermediateFile(projectId: String, stage: PipelineStatus, extension: String = "mp4"): File {
        val stageName = when (stage) {
            PipelineStatus.TRIM_EXECUTION -> "01_trim"
            PipelineStatus.CROP_EXECUTION -> "02_crop"
            PipelineStatus.ZOOM_EXECUTION -> "03_zoom"
            PipelineStatus.CAPTION_EXECUTION -> "04_caption"
            PipelineStatus.AUDIO_MIX -> "05_audio"
            else -> stage.name.lowercase()
        }
        val dir = getStageDirectory(projectId, stageName)
        return File(dir, "output_${System.currentTimeMillis()}.$extension")
    }

    fun createFinalFile(projectId: String, extension: String = "mp4"): File {
        val dir = getFinalDirectory(projectId)
        return File(dir, "final_export_${System.currentTimeMillis()}.$extension")
    }

    fun createAudioFile(projectId: String, name: String, extension: String = "m4a"): File {
        val dir = getAudioDirectory(projectId)
        return File(dir, "${name}_${System.currentTimeMillis()}.$extension")
    }

    fun getUriForFile(file: File): Uri {
        return Uri.fromFile(file)
    }

    fun deleteProjectFiles(projectId: String): Boolean {
        val dir = File(baseProjectsDir, projectId)
        return if (dir.exists()) dir.deleteRecursively() else true
    }
}

class ProjectStorageManager(
    private val storageManager: StorageManager
) {
    fun setupProjectStructure(projectId: String) {
        storageManager.getProjectDirectory(projectId)
        storageManager.getStageDirectory(projectId, "01_trim")
        storageManager.getStageDirectory(projectId, "02_crop")
        storageManager.getStageDirectory(projectId, "03_zoom")
        storageManager.getStageDirectory(projectId, "04_caption")
        storageManager.getStageDirectory(projectId, "05_audio")
        storageManager.getAudioDirectory(projectId)
        storageManager.getFinalDirectory(projectId)
    }

    fun getSourceFile(projectId: String): File = storageManager.getSourceFile(projectId)
    fun createStageOutputFile(projectId: String, stage: PipelineStatus): File = storageManager.createIntermediateFile(projectId, stage)
    fun createFinalOutputFile(projectId: String): File = storageManager.createFinalFile(projectId)
    fun createAudioOutputFile(projectId: String, tag: String): File = storageManager.createAudioFile(projectId, tag)
    fun cleanupIntermediates(projectId: String) {
        val stagesDir = File(storageManager.getProjectDirectory(projectId), "stages")
        if (stagesDir.exists()) stagesDir.deleteRecursively()
    }
    fun deleteProject(projectId: String): Boolean = storageManager.deleteProjectFiles(projectId)
}
