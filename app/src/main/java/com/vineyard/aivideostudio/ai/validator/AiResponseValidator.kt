package com.vineyard.aivideostudio.ai.validator

import com.vineyard.aivideostudio.ai.model.CaptionDecision
import com.vineyard.aivideostudio.ai.model.CommentaryDecision
import com.vineyard.aivideostudio.ai.model.CropDecision
import com.vineyard.aivideostudio.ai.model.MasterRecipe
import com.vineyard.aivideostudio.ai.model.SourceAnalysis
import com.vineyard.aivideostudio.ai.model.TrimDecision
import com.vineyard.aivideostudio.ai.model.ZoomDecision
import com.vineyard.aivideostudio.core.model.effects.BlurSpec
import com.vineyard.aivideostudio.core.model.effects.ColorGradeSpec
import com.vineyard.aivideostudio.core.model.effects.ReplacementOverlaySpec
import com.vineyard.aivideostudio.core.model.effects.SpeedRampSpec
import com.vineyard.aivideostudio.core.model.effects.TextCardSpec
import com.vineyard.aivideostudio.core.model.effects.TrackingIndicatorSpec
import com.vineyard.aivideostudio.core.validation.ValidationResult

object AiResponseValidator {

    /**
     * Comprehensive validator for Master Recipe JSON scripts including advanced tools,
     * instruction cards, and precision callouts.
     */
    fun validateMasterRecipe(recipe: MasterRecipe, sourceDurationSeconds: Double): ValidationResult {
        val durationMs = (sourceDurationSeconds * 1000).toLong()

        // 1. If Audio-Only Mode is active, bypass video transformation constraints
        if (recipe.audioOnlyMode) {
            if (recipe.commentary.fullScript.isBlank() && recipe.commentary.segments.isNullOrEmpty()) {
                return ValidationResult.Invalid("Audio-only recipe must provide a commentary script or audio replacement track.", "commentary")
            }
        }

        // 2. Validate Speed Adjustments
        val speedSpecs = recipe.editingPlan.speedAdjustments.map { it.toSpeedRampSpec() }
        val speedValidation = validateSpeedRamps(speedSpecs, durationMs)
        if (speedValidation is ValidationResult.Invalid) return speedValidation

        // 3. Validate Blur Effects
        val blurSpecs = recipe.editingPlan.blurEffects.map { it.toBlurSpec() }
        val blurValidation = validateBlurSpecs(blurSpecs, durationMs)
        if (blurValidation is ValidationResult.Invalid) return blurValidation

        // 4. Validate Replacement Overlays (1:1 Logo/Emoji covers)
        val overlaySpecs = recipe.editingPlan.replacementOverlays.mapIndexed { idx, dto -> dto.toReplacementOverlaySpec(idx) }
        val overlayValidation = validateReplacementOverlays(overlaySpecs, durationMs)
        if (overlayValidation is ValidationResult.Invalid) return overlayValidation

        // 5. Validate Color Grading
        recipe.editingPlan.colorGrade?.toColorGradeSpec()?.let { colorGradeSpec ->
            val colorValidation = validateColorGrade(colorGradeSpec)
            if (colorValidation is ValidationResult.Invalid) return colorValidation
        }

        // 6. Validate Motion Tracking & UI Callouts
        val trackingSpecs = recipe.editingPlan.trackingIndicators.mapIndexed { idx, dto -> dto.toTrackingIndicatorSpec(idx) }
        val trackingValidation = validateTrackingIndicators(trackingSpecs, durationMs)
        if (trackingValidation is ValidationResult.Invalid) return trackingValidation

        // 7. Validate Presentation Text Cards (Instruction & Conclusion Slates)
        val cardSpecs = recipe.editingPlan.textCards.mapIndexed { idx, dto -> dto.toTextCardSpec(idx) }
        val cardValidation = validateTextCards(cardSpecs, durationMs)
        if (cardValidation is ValidationResult.Invalid) return cardValidation

        return ValidationResult.Valid
    }

    /**
     * Validates instruction slates, conclusion boards, and floating text cards.
     */
    fun validateTextCards(specs: List<TextCardSpec>, totalDurationMs: Long): ValidationResult {
        for (card in specs) {
            if (card.title.isBlank()) {
                return ValidationResult.Invalid("Text card title cannot be blank (${card.id})", "textCard.title")
            }
            if (card.startTimeMs < 0L || card.endTimeMs <= card.startTimeMs) {
                return ValidationResult.Invalid("Invalid text card timing [${card.startTimeMs}ms, ${card.endTimeMs}ms]", "textCard.timing")
            }
            if (card.endTimeMs > totalDurationMs + 2000L) {
                return ValidationResult.Invalid("Text card end time (${card.endTimeMs}ms) exceeds video duration", "textCard.endTimeMs")
            }
            if (card.backgroundOpacity !in 0.0f..1.0f) {
                return ValidationResult.Invalid("Background opacity must be within [0.0, 1.0]", "textCard.backgroundOpacity")
            }
        }
        return ValidationResult.Valid
    }

    /**
     * Validates motion tracking indicators, keyframes, stationary button callouts, and spotlight dimming.
     */
    fun validateTrackingIndicators(specs: List<TrackingIndicatorSpec>, totalDurationMs: Long): ValidationResult {
        for (spec in specs) {
            // Either keyframes or static bounds must be provided
            if (spec.keyframes.isEmpty() && spec.staticBounds == null) {
                return ValidationResult.Invalid(
                    "Callout indicator '${spec.id}' must provide either keyframes or static_bounds for stationary targets.",
                    "tracking.target"
                )
            }

            // Validate static bounds if present
            spec.staticBounds?.let { b ->
                if (b.left !in 0f..1f || b.top !in 0f..1f || b.right !in 0f..1f || b.bottom !in 0f..1f) {
                    return ValidationResult.Invalid("Static bounds coordinates must be normalized within [0.0, 1.0]", "tracking.staticBounds")
                }
            }

            // Validate chronological keyframe progression if motion tracking
            if (spec.keyframes.isNotEmpty()) {
                var lastTime = -1L
                for (kf in spec.keyframes) {
                    if (kf.timeMs < lastTime) {
                        return ValidationResult.Invalid("Keyframes must be chronologically ordered in indicator '${spec.id}'", "tracking.keyframe.time")
                    }
                    if (kf.x !in 0f..1f || kf.y !in 0f..1f) {
                        return ValidationResult.Invalid("Target coordinates must be normalized within [0.0, 1.0]", "tracking.keyframe.coords")
                    }
                    lastTime = kf.timeMs
                }
            }

            if (spec.dimBackgroundOpacity !in 0.0f..1.0f) {
                return ValidationResult.Invalid("Dim background opacity must be within [0.0, 1.0]", "tracking.dimBackgroundOpacity")
            }
        }
        return ValidationResult.Valid
    }

    /**
     * Validates speed-ramping specifications and ensures non-overlapping ranges.
     */
    fun validateSpeedRamps(specs: List<SpeedRampSpec>, totalDurationMs: Long): ValidationResult {
        val sorted = specs.sortedBy { it.startTimeMs }
        var lastEnd = 0L

        for (spec in sorted) {
            if (spec.startTimeMs < 0L) {
                return ValidationResult.Invalid("Speed ramp start time cannot be negative (${spec.startTimeMs}ms)", "speedRamp.startTimeMs")
            }
            if (spec.endTimeMs <= spec.startTimeMs) {
                return ValidationResult.Invalid("Speed ramp end time (${spec.endTimeMs}ms) must be > start time (${spec.startTimeMs}ms)", "speedRamp.endTimeMs")
            }
            if (spec.endTimeMs > totalDurationMs + 1000L) {
                return ValidationResult.Invalid("Speed ramp end time (${spec.endTimeMs}ms) exceeds video duration (${totalDurationMs}ms)", "speedRamp.endTimeMs")
            }
            if (spec.startTimeMs < lastEnd) {
                return ValidationResult.Invalid("Overlapping speed ramp intervals detected at ${spec.startTimeMs}ms", "speedRamp")
            }
            if (spec.speedMultiplier !in 0.25f..8.0f) {
                return ValidationResult.Invalid("Speed multiplier (${spec.speedMultiplier}x) out of supported bounds [0.25x, 8.0x]", "speedMultiplier")
            }
            lastEnd = spec.endTimeMs
        }
        return ValidationResult.Valid
    }

    /**
     * Validates selective Gaussian and mosaic blur areas.
     */
    fun validateBlurSpecs(specs: List<BlurSpec>, totalDurationMs: Long): ValidationResult {
        for (spec in specs) {
            if (spec.startTimeMs < 0L || spec.endTimeMs <= spec.startTimeMs || spec.endTimeMs > totalDurationMs + 1000L) {
                return ValidationResult.Invalid("Invalid blur timing window [${spec.startTimeMs}ms, ${spec.endTimeMs}ms]", "blur.timing")
            }
            if (spec.bounds.left !in 0f..1f || spec.bounds.top !in 0f..1f ||
                spec.bounds.right !in 0f..1f || spec.bounds.bottom !in 0f..1f
            ) {
                return ValidationResult.Invalid("Blur coordinates must be normalized within [0.0, 1.0]", "blur.bounds")
            }
            if (spec.intensity <= 0f || spec.intensity > 100f) {
                return ValidationResult.Invalid("Blur intensity must be within (0.0, 100.0] (got ${spec.intensity})", "blur.intensity")
            }
        }
        return ValidationResult.Valid
    }

    /**
     * Validates 1:1 watermark, brand logo, and emoji replacements.
     */
    fun validateReplacementOverlays(specs: List<ReplacementOverlaySpec>, totalDurationMs: Long): ValidationResult {
        for (spec in specs) {
            if (spec.contentValue.isBlank()) {
                return ValidationResult.Invalid("Overlay content value cannot be blank", "replacement.contentValue")
            }
            if (spec.startTimeMs < 0L || spec.endTimeMs <= spec.startTimeMs || spec.endTimeMs > totalDurationMs + 1000L) {
                return ValidationResult.Invalid("Invalid overlay timing window [${spec.startTimeMs}ms, ${spec.endTimeMs}ms]", "replacement.timing")
            }
            if (spec.bounds.left !in 0f..1f || spec.bounds.top !in 0f..1f ||
                spec.bounds.right !in 0f..1f || spec.bounds.bottom !in 0f..1f
            ) {
                return ValidationResult.Invalid("Overlay bounds must be normalized within [0.0, 1.0]", "replacement.bounds")
            }
            if (spec.opacity !in 0f..1f) {
                return ValidationResult.Invalid("Overlay opacity must be within [0.0, 1.0] (got ${spec.opacity})", "replacement.opacity")
            }
        }
        return ValidationResult.Valid
    }

    /**
     * Validates color grading and visual adjustment ranges.
     */
    fun validateColorGrade(spec: ColorGradeSpec): ValidationResult {
        if (spec.brightness !in -1.0f..1.0f) {
            return ValidationResult.Invalid("Brightness must be within [-1.0, 1.0] (got ${spec.brightness})", "colorGrade.brightness")
        }
        if (spec.contrast !in -1.0f..1.0f) {
            return ValidationResult.Invalid("Contrast must be within [-1.0, 1.0] (got ${spec.contrast})", "colorGrade.contrast")
        }
        if (spec.saturation !in 0.0f..2.0f) {
            return ValidationResult.Invalid("Saturation must be within [0.0, 2.0] (got ${spec.saturation})", "colorGrade.saturation")
        }
        if (spec.sharpness !in 0.0f..1.0f) {
            return ValidationResult.Invalid("Sharpness must be within [0.0, 1.0] (got ${spec.sharpness})", "colorGrade.sharpness")
        }
        if (spec.hue !in -180.0f..180.0f) {
            return ValidationResult.Invalid("Hue rotation must be within [-180.0, 180.0] degrees (got ${spec.hue})", "colorGrade.hue")
        }
        return ValidationResult.Valid
    }

    /**
     * Validates source video analysis output.
     */
    fun validateSourceAnalysis(analysis: SourceAnalysis): ValidationResult {
        if (analysis.duration <= 0.0) {
            return ValidationResult.Invalid("Source analysis duration must be positive (got ${analysis.duration})", "duration")
        }
        if (analysis.summary.isBlank()) {
            return ValidationResult.Invalid("Source analysis summary cannot be blank", "summary")
        }
        for (scene in analysis.scenes) {
            if (scene.start < 0.0 || scene.end <= scene.start || scene.end > analysis.duration + 1.0) {
                return ValidationResult.Invalid(
                    "Invalid scene timestamp bounds: [${scene.start}, ${scene.end}] for duration ${analysis.duration}",
                    "scenes"
                )
            }
        }
        return ValidationResult.Valid
    }

    /**
     * Validates trim decisions.
     */
    fun validateTrim(
        decision: TrimDecision,
        currentDuration: Double,
        enforceTransformativeCut: Boolean = true
    ): ValidationResult {
        if (enforceTransformativeCut && (!decision.isNecessary || decision.segmentsToRemove.isEmpty())) {
            return ValidationResult.Invalid(
                "Transformative editing mandate requires at least one cut segment to tighten pacing and alter source structure.",
                "segmentsToRemove"
            )
        }

        if (!decision.isNecessary || decision.segmentsToRemove.isEmpty()) {
            return ValidationResult.Valid
        }

        val sorted = decision.segmentsToRemove.sortedBy { it.start }
        var lastEnd = 0.0

        for (segment in sorted) {
            if (segment.start < 0.0) {
                return ValidationResult.Invalid("Trim start timestamp must be >= 0.0 (got ${segment.start})", "start")
            }
            if (segment.end <= segment.start) {
                return ValidationResult.Invalid("Trim end must be greater than start (got ${segment.start} to ${segment.end})", "end")
            }
            if (segment.end > currentDuration + 0.2) {
                return ValidationResult.Invalid("Trim cut end (${segment.end}s) exceeds video duration (${currentDuration}s)", "end")
            }
            if (segment.start < lastEnd) {
                return ValidationResult.Invalid("Overlapping trim cuts detected at ${segment.start}s", "segments")
            }
            lastEnd = segment.end
        }

        val totalRemoved = sorted.sumOf { it.end - it.start }
        if (totalRemoved >= currentDuration - 0.5) {
            return ValidationResult.Invalid("Trim plan would remove almost entire video ($totalRemoved of $currentDuration s)", "segments")
        }

        return ValidationResult.Valid
    }

    /**
     * Validates normalized crop and reframing coordinates.
     */
    fun validateCrop(decision: CropDecision): ValidationResult {
        if (!decision.isNecessary) return ValidationResult.Valid

        if (decision.x < 0f || decision.x >= 1f) {
            return ValidationResult.Invalid("Crop x coordinate must be within [0, 1) (got ${decision.x})", "x")
        }
        if (decision.y < 0f || decision.y >= 1f) {
            return ValidationResult.Invalid("Crop y coordinate must be within [0, 1) (got ${decision.y})", "y")
        }
        if (decision.width <= 0f || decision.width > 1.05f) {
            return ValidationResult.Invalid("Crop width must be within (0, 1] (got ${decision.width})", "width")
        }
        if (decision.height <= 0f || decision.height > 1.05f) {
            return ValidationResult.Invalid("Crop height must be within (0, 1] (got ${decision.height})", "height")
        }
        if (decision.x + decision.width > 1.05f) {
            return ValidationResult.Invalid("Crop right edge (x + width) exceeds frame bounds (got ${decision.x + decision.width})", "width")
        }
        if (decision.y + decision.height > 1.05f) {
            return ValidationResult.Invalid("Crop bottom edge (y + height) exceeds frame bounds (got ${decision.y + decision.height})", "height")
        }

        return ValidationResult.Valid
    }

    /**
     * Validates punch-in zoom scale and centering.
     */
    fun validateZoom(decision: ZoomDecision, currentDuration: Double): ValidationResult {
        if (!decision.isNecessary) return ValidationResult.Valid

        if (decision.start < 0.0 || decision.start >= currentDuration) {
            return ValidationResult.Invalid("Zoom start outside timeline: ${decision.start}", "start")
        }
        if (decision.end <= decision.start || decision.end > currentDuration + 0.2) {
            return ValidationResult.Invalid("Zoom end must be > start and <= duration (got ${decision.end})", "end")
        }
        if (decision.fromScale <= 0f || decision.toScale <= 0f) {
            return ValidationResult.Invalid("Zoom scale must be positive", "scale")
        }
        if (decision.toScale < 1.0f || decision.toScale > 2.5f) {
            return ValidationResult.Invalid("Zoom toScale must be within [1.0, 2.5] (got ${decision.toScale})", "toScale")
        }
        if (decision.centerX !in 0f..1f || decision.centerY !in 0f..1f) {
            return ValidationResult.Invalid("Zoom center must be normalized within [0, 1]", "center")
        }

        return ValidationResult.Valid
    }

    /**
     * Validates generated burned-in caption segments.
     */
    fun validateCaptions(decision: CaptionDecision, currentDuration: Double): ValidationResult {
        if (!decision.isNecessary || decision.captions.isEmpty()) return ValidationResult.Valid

        for (caption in decision.captions) {
            if (caption.text.isBlank()) {
                return ValidationResult.Invalid("Caption text cannot be blank", "text")
            }
            if (caption.start < 0.0 || caption.end <= caption.start || caption.end > currentDuration + 0.5) {
                return ValidationResult.Invalid("Invalid caption timing [${caption.start}, ${caption.end}] for duration $currentDuration", "timing")
            }
            if (caption.x !in 0f..1f || caption.y !in 0f..1f) {
                return ValidationResult.Invalid("Caption position coordinates must be normalized within [0, 1]", "position")
            }
        }
        return ValidationResult.Valid
    }

    /**
     * Validates commentary segments.
     */
    fun validateCommentary(
        decision: CommentaryDecision,
        currentDuration: Double,
        requireCommentary: Boolean = true
    ): ValidationResult {
        if (requireCommentary && (!decision.isNecessary || decision.commentarySegments.isEmpty())) {
            return ValidationResult.Invalid(
                "Copyright-safe production requires voiceover commentary to replace the purged original audio track.",
                "commentarySegments"
            )
        }

        if (!decision.isNecessary || decision.commentarySegments.isEmpty()) {
            return ValidationResult.Valid
        }

        for (seg in decision.commentarySegments) {
            if (seg.text.isBlank()) {
                return ValidationResult.Invalid("Commentary narration text cannot be blank", "text")
            }
            if (seg.start < 0.0 || seg.end <= seg.start || seg.end > currentDuration + 1.0) {
                return ValidationResult.Invalid("Invalid commentary timing [${seg.start}, ${seg.end}] for video duration $currentDuration", "timing")
            }
        }

        return ValidationResult.Valid
    }
}