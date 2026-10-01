package com.vineyard.aivideostudio.core.result

sealed class AppError(open val message: String, open val cause: Throwable? = null) {
    data class NetworkError(override val message: String, override val cause: Throwable? = null) : AppError(message, cause)
    data class ApiKeyError(override val message: String = "Gemini API key is invalid or missing") : AppError(message)
    data class QuotaExceededError(override val message: String = "Gemini API quota exceeded") : AppError(message)
    data class ModelUnavailableError(override val message: String) : AppError(message)
    data class MediaProcessingError(override val message: String, override val cause: Throwable? = null) : AppError(message, cause)
    data class ValidationError(override val message: String) : AppError(message)
    data class StorageError(override val message: String) : AppError(message)
    data class QaFailureError(override val message: String, val stage: String, val retryCount: Int) : AppError(message)
    data class UnknownError(override val message: String, override val cause: Throwable? = null) : AppError(message, cause)
}
