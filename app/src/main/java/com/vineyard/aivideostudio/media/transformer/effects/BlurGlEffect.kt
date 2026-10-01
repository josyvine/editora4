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
import com.vineyard.aivideostudio.core.model.effects.BlurShape
import com.vineyard.aivideostudio.core.model.effects.BlurSpec
import com.vineyard.aivideostudio.core.model.effects.BlurType

/**
 * Custom Media3 OpenGL shader effect that applies selective Gaussian,
 * Mosaic, or Privacy Box blur to specified normalized coordinates and time windows.
 */
@OptIn(UnstableApi::class)
class BlurGlEffect(
    private val blurSpecs: List<BlurSpec>
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return BlurGlShaderProgram(context, useHdr, blurSpecs)
    }
}

@OptIn(UnstableApi::class)
private class BlurGlShaderProgram(
    context: Context,
    useHdr: Boolean,
    private val blurSpecs: List<BlurSpec>
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
            uniform int uActive;
            uniform int uShape;      // 0: RECTANGLE, 1: CIRCLE, 2: FULL_FRAME
            uniform int uType;       // 0: GAUSSIAN, 1: MOSAIC, 2: PRIVACY_BOX
            uniform vec4 uBounds;    // x: left, y: top, z: right, w: bottom
            uniform float uIntensity;

            bool isInsideRegion(vec2 uv) {
                // Media3 OpenGL coordinate: Y is inverted (0.0 top, 1.0 bottom)
                float normY = 1.0 - uv.y;
                float normX = uv.x;

                if (uShape == 2) { // FULL_FRAME
                    return true;
                }
                
                if (uShape == 0) { // RECTANGLE
                    return normX >= uBounds.x && normX <= uBounds.z &&
                           normY >= uBounds.y && normY <= uBounds.w;
                }

                if (uShape == 1) { // CIRCLE
                    vec2 center = vec2((uBounds.x + uBounds.z) * 0.5, (uBounds.y + uBounds.w) * 0.5);
                    float radiusX = (uBounds.z - uBounds.x) * 0.5;
                    float radiusY = (uBounds.w - uBounds.y) * 0.5;
                    float normalizedDist = pow((normX - center.x) / max(radiusX, 0.001), 2.0) +
                                         pow((normY - center.y) / max(radiusY, 0.001), 2.0);
                    return normalizedDist <= 1.0;
                }

                return false;
            }

            vec4 applyMosaic(vec2 uv) {
                float pixelBlock = max(uIntensity * 2.0, 8.0);
                vec2 stepCoord = pixelBlock / uTexSize;
                vec2 coord = floor(uv / stepCoord) * stepCoord + (stepCoord * 0.5);
                return texture2D(uTexSampler, coord);
            }

            vec4 applyGaussian(vec2 uv) {
                float radius = max(uIntensity, 1.0);
                vec2 texOffset = vec2(radius / uTexSize.x, radius / uTexSize.y);
                
                vec4 sum = vec4(0.0);
                // 9-Tap Separable Gaussian Kernel Approximation
                sum += texture2D(uTexSampler, uv + vec2(-texOffset.x, -texOffset.y)) * 0.0625;
                sum += texture2D(uTexSampler, uv + vec2(0.0, -texOffset.y)) * 0.125;
                sum += texture2D(uTexSampler, uv + vec2(texOffset.x, -texOffset.y)) * 0.0625;

                sum += texture2D(uTexSampler, uv + vec2(-texOffset.x, 0.0)) * 0.125;
                sum += texture2D(uTexSampler, uv) * 0.25;
                sum += texture2D(uTexSampler, uv + vec2(texOffset.x, 0.0)) * 0.125;

                sum += texture2D(uTexSampler, uv + vec2(-texOffset.x, texOffset.y)) * 0.0625;
                sum += texture2D(uTexSampler, uv + vec2(0.0, texOffset.y)) * 0.125;
                sum += texture2D(uTexSampler, uv + vec2(texOffset.x, texOffset.y)) * 0.0625;

                return sum;
            }

            void main() {
                if (uActive == 0 || !isInsideRegion(vTexSamplingCoords)) {
                    gl_FragColor = texture2D(uTexSampler, vTexSamplingCoords);
                    return;
                }

                if (uType == 1) { // MOSAIC
                    gl_FragColor = applyMosaic(vTexSamplingCoords);
                } else { // GAUSSIAN or PRIVACY_BOX
                    gl_FragColor = applyGaussian(vTexSamplingCoords);
                }
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
            throw VideoFrameProcessingException("Failed to initialize BlurGlShaderProgram", e)
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

            val currentTimeMs = presentationTimeUs / 1000L
            val activeSpec = blurSpecs.firstOrNull { spec ->
                currentTimeMs in spec.startTimeMs..spec.endTimeMs
            }

            if (activeSpec != null) {
                glProgram.setIntUniform("uActive", 1)
                glProgram.setIntUniform(
                    "uShape",
                    when (activeSpec.shape) {
                        BlurShape.RECTANGLE -> 0
                        BlurShape.CIRCLE -> 1
                        BlurShape.FULL_FRAME -> 2
                    }
                )
                glProgram.setIntUniform(
                    "uType",
                    when (activeSpec.type) {
                        BlurType.GAUSSIAN -> 0
                        BlurType.MOSAIC -> 1
                        BlurType.PRIVACY_BOX -> 2
                    }
                )
                glProgram.setFloatsUniform(
                    "uBounds",
                    floatArrayOf(
                        activeSpec.bounds.left,
                        activeSpec.bounds.top,
                        activeSpec.bounds.right,
                        activeSpec.bounds.bottom
                    )
                )
                glProgram.setFloatUniform("uIntensity", activeSpec.intensity)
            } else {
                glProgram.setIntUniform("uActive", 0)
                glProgram.setFloatsUniform("uBounds", floatArrayOf(0f, 0f, 0f, 0f))
                glProgram.setFloatUniform("uIntensity", 0f)
            }

            // Set frame buffer / texture parameters
            glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            glProgram.setFloatsUniform("uTexSize", floatArrayOf(currentWidth.toFloat(), currentHeight.toFloat()))

            // Re-bind quad vertex buffer attribute before drawing
            glProgram.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )

            // Draw full-screen quad through Media3 vertex buffers
            glProgram.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtil.checkGlError()
        } catch (e: Exception) {
            throw VideoFrameProcessingException("OpenGL error during BlurGlShaderProgram drawFrame", e)
        }
    }

    override fun release() {
        super.release()
        try {
            glProgram.delete()
        } catch (_: Exception) {}
    }
}