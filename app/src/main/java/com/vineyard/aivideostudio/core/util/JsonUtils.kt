package com.vineyard.aivideostudio.core.util

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object JsonUtils {
    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    fun extractJson(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("```json") && trimmed.endsWith("```")) {
            return trimmed.removePrefix("```json").removeSuffix("```").trim()
        }
        if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
            return trimmed.removePrefix("```").removeSuffix("```").trim()
        }

        // Locate first '{' or '[' and last '}' or ']'
        val firstBrace = trimmed.indexOf('{')
        val firstBracket = trimmed.indexOf('[')
        val firstChar = when {
            firstBrace != -1 && firstBracket != -1 -> Math.min(firstBrace, firstBracket)
            firstBrace != -1 -> firstBrace
            else -> firstBracket
        }

        val lastBrace = trimmed.lastIndexOf('}')
        val lastBracket = trimmed.lastIndexOf(']')
        val lastChar = Math.max(lastBrace, lastBracket)

        return if (firstChar != -1 && lastChar != -1 && lastChar > firstChar) {
            trimmed.substring(firstChar, lastChar + 1)
        } else {
            trimmed
        }
    }

    inline fun <reified T> fromJson(jsonString: String): T? {
        return try {
            val cleanJson = extractJson(jsonString)
            val adapter = moshi.adapter(T::class.java)
            adapter.fromJson(cleanJson)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    inline fun <reified T> toJson(value: T): String {
        return try {
            val adapter = moshi.adapter(T::class.java)
            adapter.toJson(value)
        } catch (e: Exception) {
            ""
        }
    }
}
