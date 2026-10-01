package com.vineyard.aivideostudio.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtils {
    fun formatDuration(seconds: Double): String {
        val totalSecs = seconds.toLong()
        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        val secs = totalSecs % 60
        val millis = ((seconds - totalSecs) * 100).toLong()

        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d.%02d", hours, minutes, secs, millis)
        } else {
            String.format(Locale.US, "%02d:%02d.%02d", minutes, secs, millis)
        }
    }

    fun formatDurationShort(seconds: Double): String {
        val totalSecs = seconds.toLong()
        val minutes = totalSecs / 60
        val secs = totalSecs % 60
        return String.format(Locale.US, "%d:%02d", minutes, secs)
    }

    fun formatTimestamp(epochMillis: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(epochMillis))
    }

    fun formatTimeOnly(epochMillis: Long): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(epochMillis))
    }
}
