package com.vineyard.aivideostudio.core.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Caption(
    val id: String,
    val projectId: String,
    val text: String,
    val start: Double,
    val end: Double,
    val x: Float = 0.5f,
    val y: Float = 0.85f,
    val fontSizeSp: Float = 22f,
    val fontColorHex: String = "#FFFFFF",
    val backgroundColorHex: String? = "#80000000",
    val style: String = "BOLD"
)
