package com.vineyard.aivideostudio.ui.screens.activity

import androidx.lifecycle.ViewModel
import com.vineyard.aivideostudio.processing.logger.LogEntry
import com.vineyard.aivideostudio.processing.logger.ProcessingLogger
import kotlinx.coroutines.flow.StateFlow

class ActivityViewModel(
    private val logger: ProcessingLogger
) : ViewModel() {

    val logs: StateFlow<List<LogEntry>> = logger.logs

    fun clearLogs() {
        logger.clear()
    }
}
