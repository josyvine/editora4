package com.vineyard.aivideostudio.media.transformer.effects

import android.content.Context
import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.effect.SingleFrameGlShaderProgram
import com.vineyard.aivideostudio.core.model.effects.ColorGradeSpec
import com.vineyard.aivideostudio.core.model.effects.ColorPreset

/**
 * Custom Media3 OpenGL shader effect for cinematic color grading presets
 * (Dawn, Dusk, Vintage, Cyberpunk, B&W, etc.) and manual adjustments
 * (Brightness, Contrast, Saturation, Sharpness, Hue).
 */
@OptIn(UnstableApi::class)
class ColorFilterGlEffect(
    private val colorGradeSpec: ColorGradeSpec
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return ColorFilterGlShaderProgram(context, useHdr, colorGradeSpec)
    }
}

@OptIn(UnstableApi::class)
private class ColorFilterGlShaderProgram(
    context: Context,
    useHdr: Boolean,
    private val spec: ColorGradeSpec
) : SingleFrameGlShaderProgram(useHdr) {

    private val glProgram: GlProgram
    private var currentWidth: Int = 1080
    private var currentHeight: Int = 1920

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aFramePosition;
            varying vec2 vTexSamplingCoords;
            void main() {
                gl_Position = aFramePosition;
                vTexSamplingCoords = (aFramePosition.xy + vec2(1.0, 1.0)) * 0.5;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            varying vec2 vTexSamplingCoords;

            uniform vec2 uTexSize;
            uniform int uPreset;          // 0: NONE, 1: VINTAGE, 2: DAWN, 3: DUSK, 4: HALO, 5: RETRO_FILM, 6: BW, 7: HIGH_CONTRAST, 8: CYBERPUNK, 9: WARM, 10: COOL
            uniform float uBrightness;    // -1.0 to 1.0
            uniform float uContrast;      // -1.0 to 1.0
            uniform float uSaturation;    // 0.0 to 2.0
            uniform float uSharpness;     // 0.0 to 1.0
            uniform float uHue;           // -180.0 to 180.0

            vec3 rgbToHsv(vec3 c) {
                vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
                vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
                vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
                float d = q.x - min(q.w, q.y);
                float e = 1.0e-10;
                return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
            }

            vec3 hsvToRgb(vec3 c) {
                vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
                vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
                return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
            }

            vec3 applyPreset(vec3 color, int preset) {
                if (preset == 1) { // VINTAGE (Sepia tone & softened highlights)
                    vec3 sepia = vec3(
                        dot(color, vec3(0.393, 0.769, 0.189)),
                        dot(color, vec3(0.349, 0.686, 0.168)),
                        dot(color, vec3(0.272, 0.534, 0.131))
                    );
                    return mix(color, sepia, 0.65) * vec3(1.05, 0.95, 0.85);
                } else if (preset == 2) { // DAWN (Golden pink morning warmth)
                    return color * vec3(1.15, 0.95, 0.90) + vec3(0.05, 0.02, 0.04);
                } else if (preset == 3) { // DUSK (Deep twilight purple/amber)
                    return color * vec3(0.90, 0.85, 1.15) + vec3(0.06, 0.03, 0.08);
                } else if (preset == 4) { // HALO (Dreamy softened glow)
                    return pow(color, vec3(0.85)) * vec3(1.08, 1.04, 0.96);
                } else if (preset == 5) { // RETRO_FILM (Faded blacks, rich midtones)
                    vec3 film = (color - 0.5) * 1.2 + 0.5;
                    return film * vec3(1.02, 0.98, 0.90) + vec3(0.04, 0.04, 0.04);
                } else if (preset == 6) { // BW (Monochrome luminance)
                    float lum = dot(color, vec3(0.2126, 0.7152, 0.0722));
                    return vec3(lum);
                } else if (preset == 7) { // HIGH_CONTRAST
                    return (color - 0.5) * 1.45 + 0.5;
                } else if (preset == 8) { // CYBERPUNK (Teal/Cyan shadows, Magenta highlights)
                    float lum = dot(color, vec3(0.2126, 0.7152, 0.0722));
                    vec3 cyber = mix(vec3(0.0, 0.85, 0.95), vec3(1.0, 0.1, 0.6), lum);
                    return mix(color, cyber, 0.40);
                } else if (preset == 9) { // WARM
                    return color * vec3(1.12, 1.02, 0.88);
                } else if (preset == 10) { // COOL
                    return color * vec3(0.88, 0.98, 1.14);
                }
                return color;
            }

            vec3 applySharpness(vec2 uv, vec3 centerColor) {
                if (uSharpness <= 0.01) return centerColor;
                vec2 step = 1.0 / uTexSize;
                vec3 n = texture2D(uTexSampler, uv + vec2(0.0, -step.y)).rgb;
                vec3 s = texture2D(uTexSampler, uv + vec2(0.0, step.y)).rgb;
                vec3 e = texture2D(uTexSampler, uv + vec2(step.x, 0.0)).rgb;
                vec3 w = texture2D(uTexSampler, uv + vec2(-step.x, 0.0)).rgb;
                vec3 laplacian = (4.0 * centerColor) - (n + s + e + w);
                return centerColor + (laplacian * uSharpness * 0.75);
            }

            void main() {
                vec4 sample = texture2D(uTexSampler, vTexSamplingCoords);
                vec3 color = sample.rgb;

                // 1. Sharpness (Laplacian Kernel)
                color = applySharpness(vTexSamplingCoords, color);

                // 2. Preset Grading Filter
                color = applyPreset(color, uPreset);

                // 3. Brightness
                color += uBrightness;

                // 4. Contrast
                color = (color - 0.5) * (1.0 + uContrast) + 0.5;

                // 5. Saturation
                float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
                color = mix(vec3(luminance), color, uSaturation);

                // 6. Hue Shift
                if (abs(uHue) > 0.01) {
                    vec3 hsv = rgbToHsv(clamp(color, 0.0, 1.0));
                    hsv.x = fract(hsv.x + (uHue / 360.0));
                    color = hsvToRgb(hsv);
                }

                gl_FragColor = vec4(clamp(color, 0.0, 1.0), sample.a);
            }
        """
    }

    init {
        try {
            glProgram = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            // Bind the full-screen quad vertex position buffer to aFramePosition
            glProgram.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )
        } catch (e: Exception) {
            throw VideoFrameProcessingException("Failed to initialize ColorFilterGlShaderProgram", e)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        currentWidth = inputWidth
        currentHeight = inputHeight
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            glProgram.use()

            val presetIndex = when (spec.preset) {
                ColorPreset.NONE -> 0
                ColorPreset.VINTAGE -> 1
                ColorPreset.DAWN -> 2
                ColorPreset.DUSK -> 3
                ColorPreset.HALO -> 4
                ColorPreset.RETRO_FILM -> 5
                ColorPreset.BW -> 6
                ColorPreset.HIGH_CONTRAST -> 7
                ColorPreset.CYBERPUNK -> 8
                ColorPreset.WARM -> 9
                ColorPreset.COOL -> 10
            }

            glProgram.setIntUniform("uPreset", presetIndex)
            glProgram.setFloatUniform("uBrightness", spec.brightness)
            glProgram.setFloatUniform("uContrast", spec.contrast)
            glProgram.setFloatUniform("uSaturation", spec.saturation)
            glProgram.setFloatUniform("uSharpness", spec.sharpness)
            glProgram.setFloatUniform("uHue", spec.hue)

            glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            glProgram.setFloatsUniform("uTexSize", floatArrayOf(currentWidth.toFloat(), currentHeight.toFloat()))

            // Re-bind quad vertex buffer attribute before drawing
            glProgram.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )

            glProgram.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtil.checkGlError()
        } catch (e: Exception) {
            throw VideoFrameProcessingException("OpenGL error during ColorFilterGlShaderProgram drawFrame", e)
        }
    }

    override fun release() {
        super.release()
        try {
            glProgram.delete()
        } catch (_: Exception) {}
    }
}