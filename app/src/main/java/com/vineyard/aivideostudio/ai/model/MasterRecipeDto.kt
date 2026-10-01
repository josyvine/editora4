package com.vineyard.aivideostudio.ai.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.vineyard.aivideostudio.core.model.Caption
import com.vineyard.aivideostudio.core.model.effects.ArrowDirection
import com.vineyard.aivideostudio.core.model.effects.BlurShape
import com.vineyard.aivideostudio.core.model.effects.BlurSpec
import com.vineyard.aivideostudio.core.model.effects.BlurType
import com.vineyard.aivideostudio.core.model.effects.CardLayout
import com.vineyard.aivideostudio.core.model.effects.ColorGradeSpec
import com.vineyard.aivideostudio.core.model.effects.ColorPreset
import com.vineyard.aivideostudio.core.model.effects.NormalizedBounds
import com.vineyard.aivideostudio.core.model.effects.OverlayType
import com.vineyard.aivideostudio.core.model.effects.ReplacementOverlaySpec
import com.vineyard.aivideostudio.core.model.effects.SpeedRampSpec
import com.vineyard.aivideostudio.core.model.effects.TextCardSpec
import com.vineyard.aivideostudio.core.model.effects.TrackingIndicatorSpec
import com.vineyard.aivideostudio.core.model.effects.TrackingKeyframe
import com.vineyard.aivideostudio.core.model.effects.TrackingStyle

@JsonClass(generateAdapter = true)
data class MasterRecipe(
    @Json(name = "projectInfo") val projectInfo: ProjectInfoDto? = null,
    @Json(name = "audioOnlyMode") val audioOnlyMode: Boolean = false, // When true: untouched original video frames
    @Json(name = "editingPlan") val editingPlan: EditingPlanDto,
    @Json(name = "captions") val captions: List<RecipeCaptionDto> = emptyList(),
    @Json(name = "commentary") val commentary: RecipeCommentaryDto,
    @Json(name = "voiceEngine") val voiceEngine: String? = null,       // Root override: "live" or "tts"
    @Json(name = "cueMode") val cueMode: String? = null,               // Root override: "single" or "multiple"
    @Json(name = "cueConcurrency") val cueConcurrency: Int? = null    // Root override: e.g. 10
)

@JsonClass(generateAdapter = true)
data class ProjectInfoDto(
    @Json(name = "title") val title: String? = null,
    @Json(name = "targetFormat") val targetFormat: String? = "SHORTS", // "SHORTS" or "VIDEO"
    @Json(name = "targetAspectRatio") val targetAspectRatio: String? = "9:16", // "9:16", "16:9", "1:1", "ORIGINAL"
    @Json(name = "targetDurationSeconds") val targetDurationSeconds: Double? = 60.0
)

@JsonClass(generateAdapter = true)
data class EditingPlanDto(
    @Json(name = "segmentsToKeep") val segmentsToKeep: List<HighlightSegmentDto> = emptyList(),
    @Json(name = "crop") val crop: RecipeCropDto? = null,
    @Json(name = "zoom") val zoom: RecipeZoomDto? = null,
    @Json(name = "speedAdjustments") val speedAdjustments: List<RecipeSpeedRampDto> = emptyList(),
    @Json(name = "blurEffects") val blurEffects: List<RecipeBlurDto> = emptyList(),
    @Json(name = "replacementOverlays") val replacementOverlays: List<RecipeReplacementDto> = emptyList(),
    @Json(name = "colorGrade") val colorGrade: RecipeColorGradeDto? = null,
    @Json(name = "trackingIndicators") val trackingIndicators: List<RecipeTrackingDto> = emptyList(),
    @Json(name = "textCards") val textCards: List<RecipeTextCardDto> = emptyList() // Intro slates, instruction cards & outro summaries
)

@JsonClass(generateAdapter = true)
data class RecipeTextCardDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "start_time_ms") val startTimeMs: Long,
    @Json(name = "end_time_ms") val endTimeMs: Long,
    @Json(name = "layout") val layout: String = "full_screen_slate", // "full_screen_slate" or "floating_modal"
    @Json(name = "tag") val tag: String? = null,                     // e.g. "INSTRUCTIONS", "SUMMARY", "TIP"
    @Json(name = "title") val title: String,
    @Json(name = "body_text") val bodyText: String = "",
    @Json(name = "background_color_hex") val backgroundColorHex: String = "#101216",
    @Json(name = "background_opacity") val backgroundOpacity: Float = 0.95f,
    @Json(name = "accent_color_hex") val accentColorHex: String = "#4E9FFF",
    @Json(name = "title_color_hex") val titleColorHex: String = "#FFFFFF",
    @Json(name = "body_color_hex") val bodyColorHex: String = "#E2E8F0",
    @Json(name = "title_font_size_sp") val titleFontSizeSp: Float = 54f,
    @Json(name = "body_font_size_sp") val bodyFontSizeSp: Float = 36f
) {
    fun toTextCardSpec(index: Int): TextCardSpec {
        val cardLayout = when (layout.lowercase()) {
            "floating_modal" -> CardLayout.FLOATING_MODAL
            else -> CardLayout.FULL_SCREEN_SLATE
        }
        return TextCardSpec(
            id = id ?: "card_${index}_${System.currentTimeMillis()}",
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            layout = cardLayout,
            tag = tag,
            title = title,
            bodyText = bodyText,
            backgroundColorHex = backgroundColorHex,
            backgroundOpacity = backgroundOpacity.coerceIn(0.0f, 1.0f),
            accentColorHex = accentColorHex,
            titleColorHex = titleColorHex,
            bodyColorHex = bodyColorHex,
            titleFontSizeSp = titleFontSizeSp,
            bodyFontSizeSp = bodyFontSizeSp
        )
    }
}

@JsonClass(generateAdapter = true)
data class HighlightSegmentDto(
    @Json(name = "start") val start: Double,
    @Json(name = "end") val end: Double,
    @Json(name = "title") val title: String? = "",
    @Json(name = "description") val description: String? = ""
) {
    fun toHighlightSegment(): HighlightSegment {
        return HighlightSegment(
            start = start,
            end = end,
            title = title ?: "",
            description = description ?: "",
            importance = "CRITICAL"
        )
    }
}

@JsonClass(generateAdapter = true)
data class RecipeCropDto(
    @Json(name = "isNecessary") val isNecessary: Boolean = true,
    @Json(name = "x") val x: Float = 0.0f,
    @Json(name = "y") val y: Float = 0.0f,
    @Json(name = "width") val width: Float = 1.0f,
    @Json(name = "height") val height: Float = 1.0f,
    @Json(name = "targetAspectRatio") val targetAspectRatio: String = "9:16"
)

@JsonClass(generateAdapter = true)
data class RecipeZoomDto(
    @Json(name = "isNecessary") val isNecessary: Boolean = true,
    @Json(name = "scale") val scale: Float = 1.25f,
    @Json(name = "centerX") val centerX: Float = 0.5f,
    @Json(name = "centerY") val centerY: Float = 0.5f
)

@JsonClass(generateAdapter = true)
data class RecipeSpeedRampDto(
    @Json(name = "start_time_ms") val startTimeMs: Long,
    @Json(name = "end_time_ms") val endTimeMs: Long,
    @Json(name = "speed_multiplier") val speedMultiplier: Float
) {
    fun toSpeedRampSpec(): SpeedRampSpec {
        return SpeedRampSpec(
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            speedMultiplier = speedMultiplier.coerceIn(0.25f, 8.0f)
        )
    }
}

@JsonClass(generateAdapter = true)
data class NormalizedBoundsDto(
    @Json(name = "left") val left: Float,
    @Json(name = "top") val top: Float,
    @Json(name = "right") val right: Float,
    @Json(name = "bottom") val bottom: Float
) {
    fun toNormalizedBounds(): NormalizedBounds {
        return NormalizedBounds(
            left = left.coerceIn(0.0f, 1.0f),
            top = top.coerceIn(0.0f, 1.0f),
            right = right.coerceIn(0.0f, 1.0f),
            bottom = bottom.coerceIn(0.0f, 1.0f)
        )
    }
}

@JsonClass(generateAdapter = true)
data class RecipeBlurDto(
    @Json(name = "start_time_ms") val startTimeMs: Long,
    @Json(name = "end_time_ms") val endTimeMs: Long,
    @Json(name = "shape") val shape: String = "rectangle", // "rectangle", "circle", "full_frame"
    @Json(name = "type") val type: String = "gaussian",    // "gaussian", "mosaic", "privacy_box"
    @Json(name = "bounds") val bounds: NormalizedBoundsDto,
    @Json(name = "intensity") val intensity: Float = 15.0f
) {
    fun toBlurSpec(): BlurSpec {
        val blurShape = when (shape.lowercase()) {
            "circle" -> BlurShape.CIRCLE
            "full_frame" -> BlurShape.FULL_FRAME
            else -> BlurShape.RECTANGLE
        }
        val blurType = when (type.lowercase()) {
            "mosaic" -> BlurType.MOSAIC
            "privacy_box" -> BlurType.PRIVACY_BOX
            else -> BlurType.GAUSSIAN
        }
        return BlurSpec(
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            shape = blurShape,
            type = blurType,
            bounds = bounds.toNormalizedBounds(),
            intensity = intensity
        )
    }
}

@JsonClass(generateAdapter = true)
data class RecipeReplacementDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "start_time_ms") val startTimeMs: Long,
    @Json(name = "end_time_ms") val endTimeMs: Long,
    @Json(name = "type") val type: String = "emoji", // "emoji", "brand_logo", "solid_badge", "sticker"
    @Json(name = "content_value") val contentValue: String,
    @Json(name = "bounds") val bounds: NormalizedBoundsDto,
    @Json(name = "rotation_degrees") val rotationDegrees: Float = 0.0f,
    @Json(name = "opacity") val opacity: Float = 1.0f
) {
    fun toReplacementOverlaySpec(index: Int): ReplacementOverlaySpec {
        val overlayType = when (type.lowercase()) {
            "brand_logo" -> OverlayType.BRAND_LOGO
            "solid_badge" -> OverlayType.SOLID_BADGE
            "sticker" -> OverlayType.STICKER
            else -> OverlayType.EMOJI
        }
        return ReplacementOverlaySpec(
            id = id ?: "replace_ovl_${index}_${System.currentTimeMillis()}",
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            type = overlayType,
            contentValue = contentValue,
            bounds = bounds.toNormalizedBounds(),
            rotationDegrees = rotationDegrees,
            opacity = opacity.coerceIn(0.0f, 1.0f)
        )
    }
}

@JsonClass(generateAdapter = true)
data class RecipeColorGradeDto(
    @Json(name = "preset") val preset: String = "none",
    @Json(name = "brightness") val brightness: Float = 0.0f,
    @Json(name = "contrast") val contrast: Float = 0.0f,
    @Json(name = "saturation") val saturation: Float = 1.0f,
    @Json(name = "sharpness") val sharpness: Float = 0.0f,
    @Json(name = "hue") val hue: Float = 0.0f
) {
    fun toColorGradeSpec(): ColorGradeSpec {
        val colorPreset = when (preset.lowercase()) {
            "vintage" -> ColorPreset.VINTAGE
            "dawn" -> ColorPreset.DAWN
            "dusk" -> ColorPreset.DUSK
            "halo" -> ColorPreset.HALO
            "retro_film" -> ColorPreset.RETRO_FILM
            "bw" -> ColorPreset.BW
            "high_contrast" -> ColorPreset.HIGH_CONTRAST
            "cyberpunk" -> ColorPreset.CYBERPUNK
            "warm" -> ColorPreset.WARM
            "cool" -> ColorPreset.COOL
            else -> ColorPreset.NONE
        }
        return ColorGradeSpec(
            preset = colorPreset,
            brightness = brightness.coerceIn(-1.0f, 1.0f),
            contrast = contrast.coerceIn(-1.0f, 1.0f),
            saturation = saturation.coerceIn(0.0f, 2.0f),
            sharpness = sharpness.coerceIn(0.0f, 1.0f),
            hue = hue.coerceIn(-180.0f, 180.0f)
        )
    }
}

@JsonClass(generateAdapter = true)
data class RecipeKeyframeDto(
    @Json(name = "time_ms") val timeMs: Long,
    @Json(name = "x") val x: Float,
    @Json(name = "y") val y: Float,
    @Json(name = "width") val width: Float = 0.15f,
    @Json(name = "height") val height: Float = 0.15f
) {
    fun toTrackingKeyframe(): TrackingKeyframe {
        return TrackingKeyframe(
            timeMs = timeMs,
            x = x.coerceIn(0.0f, 1.0f),
            y = y.coerceIn(0.0f, 1.0f),
            width = width.coerceIn(0.01f, 1.0f),
            height = height.coerceIn(0.01f, 1.0f)
        )
    }
}

@JsonClass(generateAdapter = true)
data class RecipeTrackingDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "style") val style: String = "red_box", // "red_box", "highlight_circle", "flashing_arrow", "spotlight", "button_highlight", "vertical_column"
    @Json(name = "arrow_direction") val arrowDirection: String = "down", // "up", "down", "left", "right"
    @Json(name = "color_hex") val colorHex: String = "#FF0000",
    @Json(name = "stroke_width_px") val strokeWidthPx: Float = 6.0f,
    @Json(name = "label") val label: String? = null,
    @Json(name = "targetText") val targetText: String? = null,          // OCR search query (e.g. "Grounding with Google Search")
    @Json(name = "target_text") val targetTextSnake: String? = null,    // snake_case support
    @Json(name = "trackingMode") val trackingMode: String? = null,      // "static", "keyframes", or "auto"
    @Json(name = "tracking_mode") val trackingModeSnake: String? = null, // snake_case support
    @Json(name = "start_time_ms") val startTimeMs: Long = 0L,
    @Json(name = "end_time_ms") val endTimeMs: Long = Long.MAX_VALUE,
    @Json(name = "static_bounds") val staticBounds: NormalizedBoundsDto? = null, // For stationary UI buttons (e.g. "Copy" icon after scroll)
    @Json(name = "dim_background_opacity") val dimBackgroundOpacity: Float = 0.0f, // 0.0 = off, 0.6 = darkens background for spotlight
    @Json(name = "keyframes") val keyframes: List<RecipeKeyframeDto> = emptyList()
) {
    fun toTrackingIndicatorSpec(index: Int): TrackingIndicatorSpec {
        val trackingStyle = when (style.lowercase()) {
            "highlight_circle" -> TrackingStyle.HIGHLIGHT_CIRCLE
            "flashing_arrow" -> TrackingStyle.FLASHING_ARROW
            "spotlight" -> TrackingStyle.SPOTLIGHT
            "button_highlight" -> TrackingStyle.BUTTON_HIGHLIGHT
            "vertical_column" -> TrackingStyle.VERTICAL_COLUMN
            else -> TrackingStyle.RED_BOX
        }
        val direction = when (arrowDirection.lowercase()) {
            "up" -> ArrowDirection.UP
            "left" -> ArrowDirection.LEFT
            "right" -> ArrowDirection.RIGHT
            else -> ArrowDirection.DOWN
        }
        val resolvedMode = trackingMode ?: trackingModeSnake ?: "auto"
        val resolvedTargetText = targetText ?: targetTextSnake

        return TrackingIndicatorSpec(
            id = id ?: "track_ind_${index}_${System.currentTimeMillis()}",
            style = trackingStyle,
            arrowDirection = direction,
            colorHex = colorHex,
            strokeWidthPx = strokeWidthPx,
            label = label,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            staticBounds = staticBounds?.toNormalizedBounds(),
            dimBackgroundOpacity = dimBackgroundOpacity.coerceIn(0.0f, 1.0f),
            keyframes = keyframes.map { it.toTrackingKeyframe() },
            trackingMode = resolvedMode,
            targetText = resolvedTargetText
        )
    }
}

@JsonClass(generateAdapter = true)
data class RecipeCaptionDto(
    @Json(name = "start") val start: Double,
    @Json(name = "end") val end: Double,
    @Json(name = "text") val text: String,
    @Json(name = "x") val x: Float = 0.5f,
    @Json(name = "y") val y: Float = 0.90f,
    @Json(name = "colorHex") val colorHex: String = "#FFFFFF",
    @Json(name = "style") val style: String = "BOLD"
) {
    fun toCaption(projectId: String, index: Int): Caption {
        return Caption(
            id = "cap_recipe_${index}_${System.currentTimeMillis()}",
            projectId = projectId,
            text = text,
            start = start,
            end = end,
            x = x,
            y = if (y in 0.75f..0.96f) 0.90f else y,
            fontSizeSp = 22f,
            fontColorHex = colorHex,
            backgroundColorHex = "#FF000000",
            style = style
        )
    }
}

@JsonClass(generateAdapter = true)
data class RecipeCommentaryDto(
    @Json(name = "tone") val tone: String? = "Genre-adapted dynamic commentary",
    @Json(name = "voiceName") val voiceName: String? = "Puck",
    @Json(name = "voiceEngine") val voiceEngine: String? = "live",    // "live" (Unlimited Live API) or "tts" (REST TTS)
    @Json(name = "fullScript") val fullScript: String,
    @Json(name = "segments") val segments: List<CommentarySegmentDto>? = null,
    @Json(name = "cueMode") val cueMode: String? = "single",          // "single" or "multiple"
    @Json(name = "cueConcurrency") val cueConcurrency: Int? = 1       // Dynamic concurrency (e.g., 10)
)

@JsonClass(generateAdapter = true)
data class CommentarySegmentDto(
    @Json(name = "start") val start: Double? = 0.0,
    @Json(name = "end") val end: Double? = 0.0,
    @Json(name = "text") val text: String
)