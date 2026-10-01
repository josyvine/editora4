package com.vineyard.aivideostudio.core.util

import android.util.Log
import com.vineyard.aivideostudio.core.model.PipelineStatus
import com.vineyard.aivideostudio.processing.logger.LogSeverity
import com.vineyard.aivideostudio.processing.logger.ProcessingLogger
import java.io.PrintWriter
import java.io.StringWriter

object CrashHandler {
    private var isInitialized = false

    fun install(logger: ProcessingLogger) {
        if (isInitialized) return
        isInitialized = true

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                val stackTrace = sw.toString()

                val crashMessage = "FATAL CRASH on thread '${thread.name}': ${throwable.javaClass.simpleName} - ${throwable.message}"
                Log.e("EditoraCrashHandler", crashMessage, throwable)

                logger.log(
                    projectId = "GLOBAL",
                    stage = PipelineStatus.FAILED,
                    message = crashMessage,
                    severity = LogSeverity.CRASH,
                    details = stackTrace
                )

                // Give IO thread 300ms to flush to SQLite
                Thread.sleep(350)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
