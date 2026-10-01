package com.vineyard.aivideostudio.core.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale

object FileUtils {
    fun copyUriToFile(context: Context, uri: Uri, destinationFile: File): Boolean {
        return try {
            destinationFile.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }
            destinationFile.exists() && destinationFile.length() > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
    }

    fun deleteRecursivelySafe(fileOrDir: File): Boolean {
        return try {
            if (fileOrDir.isDirectory) {
                fileOrDir.listFiles()?.forEach { deleteRecursivelySafe(it) }
            }
            fileOrDir.delete()
        } catch (e: Exception) {
            false
        }
    }
}
