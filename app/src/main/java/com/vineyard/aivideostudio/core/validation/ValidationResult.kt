package com.vineyard.aivideostudio.core.validation

sealed interface ValidationResult {
    object Valid : ValidationResult
    data class Invalid(val reason: String, val fieldName: String? = null) : ValidationResult

    val isValid: Boolean get() = this is Valid
}
