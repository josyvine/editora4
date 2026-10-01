package com.vineyard.aivideostudio.processing.logger

import com.vineyard.aivideostudio.core.model.PipelineStatus
import com.vineyard.aivideostudio.core.util.TimeUtils
import com.vineyard.aivideostudio.data.local.database.dao.PersistentLogDao
import com.vineyard.aivideostudio.data.local.database.entity.PersistentLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class LogSeverity {
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
    CRASH
}

data class LogEntry(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val stage: PipelineStatus,
    val message: String,
    val severity: LogSeverity = LogSeverity.INFO,
    val technicalDetails: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class ProcessingLogger(
    private val persistentLogDao: PersistentLogDao? = null
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _inMemoryLogs = MutableStateFlow<List<LogEntry>>(emptyList())

    val logs: StateFlow<List<LogEntry>> = if (persistentLogDao != null) {
        persistentLogDao.getAllLogs().map { entities ->
            entities.map { it.toDomain() }
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )
    } else {
        _inMemoryLogs.asStateFlow()
    }

    val errorCount: StateFlow<Int> = if (persistentLogDao != null) {
        persistentLogDao.getErrorCount().stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = 0
        )
    } else {
        MutableStateFlow(0).asStateFlow()
    }

    /**
     * Records a diagnostic log entry with optional multi-line technical details or full Java/OpenGL Throwable stack traces.
     */
    fun log(
        projectId: String,
        stage: PipelineStatus,
        message: String,
        severity: LogSeverity = LogSeverity.INFO,
        details: String? = null,
        throwable: Throwable? = null
    ) {
        val fullDetails = buildString {
            if (!details.isNullOrBlank()) {
                append(details.trim())
            }
            if (throwable != null) {
                if (isNotEmpty()) append("\n\n")
                append("EXCEPTION: ${throwable.javaClass.name}: ${throwable.message}\n")
                val rootCause = findRootCause(throwable)
                if (rootCause !== throwable) {
                    append("ROOT CAUSE: ${rootCause.javaClass.name}: ${rootCause.message}\n")
                }
                append("STACKTRACE:\n").append(throwable.stackTraceToString())
            }
        }.takeIf { it.isNotBlank() }

        val entry = LogEntry(
            projectId = projectId,
            stage = stage,
            message = message,
            severity = severity,
            technicalDetails = fullDetails,
            timestamp = System.currentTimeMillis()
        )

        // Update in-memory log list
        _inMemoryLogs.value = listOf(entry) + _inMemoryLogs.value

        // Persist complete diagnostic record to SQLite Room Database
        if (persistentLogDao != null) {
            scope.launch {
                try {
                    persistentLogDao.insertLog(
                        PersistentLogEntity(
                            projectId = projectId,
                            stage = stage.name,
                            severity = severity.name,
                            message = message,
                            technicalDetails = fullDetails,
                            timestamp = entry.timestamp
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Dedicated helper to log exceptions with full stack traces.
     */
    fun logError(
        projectId: String,
        stage: PipelineStatus,
        message: String,
        throwable: Throwable? = null
    ) {
        log(
            projectId = projectId,
            stage = stage,
            message = message,
            severity = LogSeverity.ERROR,
            throwable = throwable
        )
    }

    private fun findRootCause(throwable: Throwable): Throwable {
        var current = throwable
        while (current.cause != null && current.cause !== current) {
            current = current.cause!!
        }
        return current
    }

    fun clear() {
        _inMemoryLogs.value = emptyList()
        if (persistentLogDao != null) {
            scope.launch {
                try {
                    persistentLogDao.clearAllLogs()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun buildExportableLogText(): String = withContext(Dispatchers.Default) {
        val currentLogs = logs.value
        val sb = StringBuilder(currentLogs.size * 256)
        sb.append("=== EDITORA AI VIDEO STUDIO — COMPLETE AUDIT LOG ===\n")
        sb.append("Generated: ${TimeUtils.formatTimestamp(System.currentTimeMillis())}\n")
        sb.append("Total Entries: ${currentLogs.size}\n\n")

        for (item in currentLogs) {
            sb.append("[${TimeUtils.formatTimestamp(item.timestamp)}] ")
            sb.append("[${item.severity.name}] ")
            sb.append("[${item.stage.name}] ")
            sb.append(item.message)
            if (!item.technicalDetails.isNullOrBlank()) {
                sb.append("\n  >>> DIAGNOSTIC DETAILS:\n  ")
                sb.append(item.technicalDetails.trim().replace("\n", "\n  "))
            }
            sb.append("\n\n")
        }
        sb.toString()
    }

    private fun PersistentLogEntity.toDomain(): LogEntry {
        val stageStatus = try {
            PipelineStatus.valueOf(stage)
        } catch (_: Exception) {
            PipelineStatus.IDLE
        }
        val logSeverity = try {
            LogSeverity.valueOf(severity)
        } catch (_: Exception) {
            LogSeverity.INFO
        }
        return LogEntry(
            id = id.toString(),
            projectId = projectId,
            stage = stageStatus,
            message = message,
            severity = logSeverity,
            technicalDetails = technicalDetails,
            timestamp = timestamp
        )
    }
}