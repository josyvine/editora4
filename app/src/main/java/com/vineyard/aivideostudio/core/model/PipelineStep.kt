package com.vineyard.aivideostudio.core.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PipelineStep(
    val id: String,
    val projectId: String,
    val stage: PipelineStatus,
    val status: StepStatus,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val inputArtifactUri: String? = null,
    val outputArtifactUri: String? = null,
    val message: String? = null,
    val errorMessage: String? = null,
    val qaVerdict: QaVerdict? = null,
    val startTime: Long? = null,
    val endTime: Long? = null
)
