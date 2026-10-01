package com.vineyard.aivideostudio.data.remote.gemini

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ListModelsResponse(
    val models: List<ModelDto> = emptyList(),
    val nextPageToken: String? = null
)

@JsonClass(generateAdapter = true)
data class ModelDto(
    val name: String, // e.g. "models/gemini-2.5-flash"
    val baseModelId: String? = null,
    val version: String? = null,
    val displayName: String? = null,
    val description: String? = null,
    val inputTokenLimit: Int? = null,
    val outputTokenLimit: Int? = null,
    val supportedGenerationMethods: List<String> = emptyList(),
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<ContentDto>,
    val generationConfig: GenerationConfigDto? = null,
    val systemInstruction: ContentDto? = null
)

@JsonClass(generateAdapter = true)
data class ContentDto(
    val role: String? = "user",
    val parts: List<PartDto>
)

@JsonClass(generateAdapter = true)
data class PartDto(
    val text: String? = null,
    val inlineData: InlineDataDto? = null,
    @Json(name = "file_data") val fileData: FileDataDto? = null
)

@JsonClass(generateAdapter = true)
data class FileDataDto(
    @Json(name = "file_uri") val fileUri: String,
    @Json(name = "mime_type") val mimeType: String = "video/mp4"
)

@JsonClass(generateAdapter = true)
data class InlineDataDto(
    @Json(name = "mime_type") val mimeType: String,
    val data: String // base64
)

@JsonClass(generateAdapter = true)
data class GenerationConfigDto(
    val responseMimeType: String? = null,
    val temperature: Float? = null,
    val topP: Float? = null,
    val responseModalities: List<String>? = null,
    val speechConfig: SpeechConfigDto? = null
)

@JsonClass(generateAdapter = true)
data class SpeechConfigDto(
    val voiceConfig: VoiceConfigDto
)

@JsonClass(generateAdapter = true)
data class VoiceConfigDto(
    val prebuiltVoiceConfig: PrebuiltVoiceConfigDto
)

@JsonClass(generateAdapter = true)
data class PrebuiltVoiceConfigDto(
    val voiceName: String
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<CandidateDto>? = null,
    val usageMetadata: UsageMetadataDto? = null
)

@JsonClass(generateAdapter = true)
data class CandidateDto(
    val content: ContentDto? = null,
    val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class UsageMetadataDto(
    val promptTokenCount: Int? = null,
    val candidatesTokenCount: Int? = null,
    val totalTokenCount: Int? = null
)

@JsonClass(generateAdapter = true)
data class FileUploadResponse(
    val file: FileInfoDto? = null
)

@JsonClass(generateAdapter = true)
data class FileInfoDto(
    val name: String? = null,
    val uri: String? = null,
    val mimeType: String? = null,
    val state: String? = null,
    val sizeBytes: String? = null
)