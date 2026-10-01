package com.vineyard.aivideostudio.core.model

enum class PipelineStatus {
    IDLE,
    IMPORTING,
    SOURCE_ANALYSIS,
    SOURCE_ANALYSIS_COMPLETE,
    AUDIO_EXTRACTION,
    TRANSCRIPTION,
    TRIM_ANALYSIS,
    TRIM_EXECUTION,
    TRIM_QA,
    CROP_ANALYSIS,
    CROP_EXECUTION,
    CROP_QA,
    ZOOM_ANALYSIS,
    ZOOM_EXECUTION,
    ZOOM_QA,
    CAPTION_ANALYSIS,
    CAPTION_EXECUTION,
    CAPTION_QA,
    COMMENTARY_ANALYSIS,
    COMMENTARY_GENERATION,
    TTS_GENERATION,
    AUDIO_MIX,
    AUDIO_QA,
    FINAL_QA,
    EXPORTING,
    COMPLETED,
    FAILED,
    NEEDS_REVIEW,
    CANCELLED;

    val isTerminal: Boolean
        get() = this == COMPLETED || this == FAILED || this == NEEDS_REVIEW || this == CANCELLED

    val isProcessing: Boolean
        get() = !isTerminal && this != IDLE
}

enum class StepStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    SKIPPED,
    FAILED,
    RETRYING,
    CANCELLED
}
