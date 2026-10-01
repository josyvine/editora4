package com.vineyard.aivideostudio.core.model.effects

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Domain and JSON data models representing granular editing tools
 * configured exclusively via the Master Recipe JSON script.
 * Uses Moshi code generation matching the rest of the project.
 */

enum class CardLayout {
    @Json(name = "full_screen_slate") FULL_SCREEN_SLATE,
    @Json(name = "floating_modal") FLOATING_MODAL
}

@JsonClass(generateAdapter = true)
data class TextCardSpec(
    @Json(name = "id") val id: String,
    @Json(name = "start_time_ms") val startTimeMs: Long,
    @Json(name = "end_time_ms") val endTimeMs: Long,
    @Json(name = "layout") val layout: CardLayout = CardLayout.FULL_SCREEN_SLATE,
    @Json(name = "tag") val tag: String? = null, // e.g. "INSTRUCTIONS", "SUMMARY", "TIP"
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
    init {
        require(startTimeMs < endTimeMs) { "startTimeMs must be less than endTimeMs" }
        require(title.isNotBlank()) { "Card title cannot be blank" }
    }
}

enum class BlurShape {
    @Json(name = "rectangle") RECTANGLE,
    @Json(name = "circle") CIRCLE,
    @Json(name = "full_frame") FULL_FRAME
}

enum class BlurType {
    @Json(name = "gaussian") GAUSSIAN,
    @Json(name = "mosaic") MOSAIC,
    @Json(name = "privacy_box") PRIVACY_BOX
}

@JsonClass(generateAdapter = true)
data class NormalizedBounds(
    @Json(name = "left") val left: Float,
    @Json(name = "top") val top: Float,
    @Json(name = "right") val right: Float,
    @Json(name = "bottom") val bottom: Float
) {
    init {
        require(left in 0.0f..1.0f) { "left bound must be between 0.0 and 1.0" }
        require(top in 0.0f..1.0f) { "top bound must be between 0.0 and 1.0" }
        require(right in 0.0f..1.0f) { "right bound must be between 0.0 and 1.0" }
        require(bottom in 0.0f..1.0f) { "bottom bound must be between 0.0 and 1.0" }
        require(left <= right) { "left must be <= right" }
        require(top <= bottom) { "top must be <= bottom" }
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = left + (width / 2.0f)
    val centerY: Float get() = top + (height / 2.0f)
}

@JsonClass(generateAdapter = true)
data class BlurSpec(
    @Json(name = "start_time_ms") val startTimeMs: Long,
    @Json(name = "end_time_ms") val endTimeMs: Long,
    @Json(name = "shape") val shape: BlurShape = BlurShape.RECTANGLE,
    @Json(name = "type") val type: BlurType = BlurType.GAUSSIAN,
    @Json(name = "bounds") val bounds: NormalizedBounds,
    @Json(name = "intensity") val intensity: Float = 15.0f // 1.0 to 50.0 radius / pixel size
)

@JsonClass(generateAdapter = true)
data class SpeedRampSpec(
    @Json(name = "start_time_ms") val startTimeMs: Long,
    @Json(name = "end_time_ms") val endTimeMs: Long,
    @Json(name = "speed_multiplier") val speedMultiplier: Float // e.g. 0.5x, 2.0x, 4.0x
) {
    init {
        require(speedMultiplier in 0.25f..8.0f) { "Speed multiplier must be between 0.25x and 8.0x" }
        require(startTimeMs < endTimeMs) { "startTimeMs must be less than endTimeMs" }
    }
}

enum class OverlayType {
    @Json(name = "emoji") EMOJI,
    @Json(name = "brand_logo") BRAND_LOGO,
    @Json(name = "solid_badge") SOLID_BADGE,
    @Json(name = "sticker") STICKER
}

@JsonClass(generateAdapter = true)
data class ReplacementOverlaySpec(
    @Json(name = "id") val id: String,
    @Json(name = "start_time_ms") val startTimeMs: Long,
    @Json(name = "end_time_ms") val endTimeMs: Long,
    @Json(name = "type") val type: OverlayType,
    @Json(name = "content_value") val contentValue: String, // Emoji unicode, asset path, or base64
    @Json(name = "bounds") val bounds: NormalizedBounds,
    @Json(name = "rotation_degrees") val rotationDegrees: Float = 0.0f,
    @Json(name = "opacity") val opacity: Float = 1.0f
)

enum class ColorPreset {
    @Json(name = "none") NONE,
    @Json(name = "vintage") VINTAGE,
    @Json(name = "dawn") DAWN,
    @Json(name = "dusk") DUSK,
    @Json(name = "halo") HALO,
    @Json(name = "retro_film") RETRO_FILM,
    @Json(name = "bw") BW,
    @Json(name = "high_contrast") HIGH_CONTRAST,
    @Json(name = "cyberpunk") CYBERPUNK,
    @Json(name = "warm") WARM,
    @Json(name = "cool") COOL
}

@JsonClass(generateAdapter = true)
data class ColorGradeSpec(
    @Json(name = "preset") val preset: ColorPreset = ColorPreset.NONE,
    @Json(name = "brightness") val brightness: Float = 0.0f,    // -1.0 to 1.0 (0.0 = neutral)
    @Json(name = "contrast") val contrast: Float = 0.0f,        // -1.0 to 1.0 (0.0 = neutral)
    @Json(name = "saturation") val saturation: Float = 1.0f,    // 0.0 (B&W) to 2.0 (vibrant)
    @Json(name = "sharpness") val sharpness: Float = 0.0f,      // 0.0 to 1.0
    @Json(name = "hue") val hue: Float = 0.0f                  // -180.0 to 180.0 degrees
)

enum class TrackingStyle {
    @Json(name = "red_box") RED_BOX,
    @Json(name = "highlight_circle") HIGHLIGHT_CIRCLE,
    @Json(name = "flashing_arrow") FLASHING_ARROW,
    @Json(name = "spotlight") SPOTLIGHT,
    @Json(name = "button_highlight") BUTTON_HIGHLIGHT,     // Pulsating corner brackets on UI buttons (e.g. "Copy" icon)
    @Json(name = "vertical_column") VERTICAL_COLUMN        // Full-height person / athlete framing pillar
}

enum class ArrowDirection {
    @Json(name = "up") UP,
    @Json(name = "down") DOWN,
    @Json(name = "left") LEFT,
    @Json(name = "right") RIGHT
}

@JsonClass(generateAdapter = true)
data class TrackingKeyframe(
    @Json(name = "time_ms") val timeMs: Long,
    @Json(name = "x") val x: Float, // Normalized 0.0 - 1.0
    @Json(name = "y") val y: Float, // Normalized 0.0 - 1.0
    @Json(name = "width") val width: Float = 0.15f,
    @Json(name = "height") val height: Float = 0.15f
)

@JsonClass(generateAdapter = true)
data class TrackingIndicatorSpec(
    @Json(name = "id") val id: String,
    @Json(name = "style") val style: TrackingStyle = TrackingStyle.RED_BOX,
    @Json(name = "arrow_direction") val arrowDirection: ArrowDirection = ArrowDirection.DOWN,
    @Json(name = "color_hex") val colorHex: String = "#FF0000",
    @Json(name = "stroke_width_px") val strokeWidthPx: Float = 6.0f,
    @Json(name = "label") val label: String? = null,
    @Json(name = "start_time_ms") val startTimeMs: Long = 0L,
    @Json(name = "end_time_ms") val endTimeMs: Long = Long.MAX_VALUE,
    @Json(name = "static_bounds") val staticBounds: NormalizedBounds? = null, // For stationary targets like buttons
    @Json(name = "dim_background_opacity") val dimBackgroundOpacity: Float = 0.0f, // 0.0 = none, 0.6 = darkens background for spotlight
    @Json(name = "keyframes") val keyframes: List<TrackingKeyframe> = emptyList(),
    @Json(name = "tracking_mode") val trackingMode: String = "auto", // "static", "keyframes", or "auto"
    @Json(name = "target_text") val targetText: String? = null      // Target text query for on-device OCR snap
)