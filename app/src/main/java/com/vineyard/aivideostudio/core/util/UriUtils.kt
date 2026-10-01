package com.vineyard.aivideostudio.core.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

object UriUtils {
    fun getFileName(context: Context, uri: Uri): String {
        var name = "video_${System.currentTimeMillis()}.mp4"
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIndex)
                    }
                }
            } catch (e: Exception) {
                // Fallback to last path segment
                uri.lastPathSegment?.let { name = it }
            }
        } else {
            uri.lastPathSegment?.let { name = it }
        }
        return name
    }

    fun getFileSize(context: Context, uri: Uri): Long {
        var size: Long = 0L
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1 && cursor.moveToFirst()) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            } catch (_: Exception) {}
        }
        return size
    }
}
