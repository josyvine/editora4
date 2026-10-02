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
 * Supports simultaneous multi-target rendering in a single GPU pass.
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
        private const val MAX_CONCURRENT_BLURS = 8

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
            uniform int uActiveCount;
            uniform int uShapes[$MAX_CONCURRENT_BLURS];      // 0: RECTANGLE, 1: CIRCLE, 2: FULL_FRAME
            uniform int uTypes[$MAX_CONCURRENT_BLURS];       // 0: GAUSSIAN, 1: MOSAIC, 2: PRIVACY_BOX
            uniform vec4 uBounds[$MAX_CONCURRENT_BLURS];     // x: left, y: top, z: right, w: bottom
            uniform float uIntensities[$MAX_CONCURRENT_BLURS];

            bool isInsideRegion(vec2 uv, int idx) {
                float normY = 1.0 - uv.y;
                float normX = uv.x;
                int shape = uShapes[idx];
                vec4 b = uBounds[idx];

                if (shape == 2) { // FULL_FRAME
                    return true;
                }
                
                if (shape == 0) { // RECTANGLE
                    return normX >= b.x && normX <= b.z &&
                           normY >= b.y && normY <= b.w;
                }

                if (shape == 1) { // CIRCLE / ELLIPSE
                    vec2 center = vec2((b.x + b.z) * 0.5, (b.y + b.w) * 0.5);
                    float radiusX = (b.z - b.x) * 0.5;
                    float radiusY = (b.w - b.y) * 0.5;
                    float normalizedDist = pow((normX - center.x) / max(radiusX, 0.001), 2.0) +
                                           pow((normY - center.y) / max(radiusY, 0.001), 2.0);
                    return normalizedDist <= 1.0;
                }

                return false;
            }

            vec4 applyMosaic(vec2 uv, float intensity) {
                float pixelBlock = max(intensity * 3.0, 12.0);
                vec2 stepCoord = pixelBlock / uTexSize;
                vec2 coord = floor(uv / stepCoord) * stepCoord + (stepCoord * 0.5);
                return texture2D(uTexSampler, coord);
            }

            vec4 applyDenseGaussian(vec2 uv, float intensity) {
                float radius = max(intensity * 2.2, 3.5);
                vec2 texOffset = vec2(radius / uTexSize.x, radius / uTexSize.y);
                
                vec4 sum = vec4(0.0);
                // 17-Tap Multi-Ring Heavy Privacy Obfuscation Convolution
                sum += texture2D(uTexSampler, uv) * 0.18;

                sum += texture2D(uTexSampler, uv + vec2(-texOffset.x, 0.0)) * 0.11;
                sum += texture2D(uTexSampler, uv + vec2(texOffset.x, 0.0)) * 0.11;
                sum += texture2D(uTexSampler, uv + vec2(0.0, -texOffset.y)) * 0.11;
                sum += texture2D(uTexSampler, uv + vec2(0.0, texOffset.y)) * 0.11;

                sum += texture2D(uTexSampler, uv + vec2(-texOffset.x, -texOffset.y)) * 0.07;
                sum += texture2D(uTexSampler, uv + vec2(texOffset.x, -texOffset.y)) * 0.07;
                sum += texture2D(uTexSampler, uv + vec2(-texOffset.x, texOffset.y)) * 0.07;
                sum += texture2D(uTexSampler, uv + vec2(texOffset.x, texOffset.y)) * 0.07;

                // Outer sampling ring for complete facial feature concealment
                vec2 outerOffset = texOffset * 1.8;
                sum += texture2D(uTexSampler, uv + vec2(-outerOffset.x, 0.0)) * 0.035;
                sum += texture2D(uTexSampler, uv + vec2(outerOffset.x, 0.0)) * 0.035;
                sum += texture2D(uTexSampler, uv + vec2(0.0, -outerOffset.y)) * 0.035;
                sum += texture2D(uTexSampler, uv + vec2(0.0, outerOffset.y)) * 0.035;

                sum += texture2D(uTexSampler, uv + vec2(-outerOffset.x, -outerOffset.y)) * 0.0175;
                sum += texture2D(uTexSampler, uv + vec2(outerOffset.x, -outerOffset.y)) * 0.0175;
                sum += texture2D(uTexSampler, uv + vec2(-outerOffset.x, outerOffset.y)) * 0.0175;
                sum += texture2D(uTexSampler, uv + vec2(outerOffset.x, outerOffset.y)) * 0.0175;

                return sum;
            }

            void main() {
                if (uActiveCount <= 0) {
                    gl_FragColor = texture2D(uTexSampler, vTexSamplingCoords);
                    return;
                }

                // Check active blur regions simultaneously
                for (int i = 0; i < $MAX_CONCURRENT_BLURS; i++) {
                    if (i >= uActiveCount) {
                        break;
                    }

                    if (isInsideRegion(vTexSamplingCoords, i)) {
                        int blurType = uTypes[i];
                        float intensity = uIntensities[i];

                        if (blurType == 1) { // MOSAIC
                            gl_FragColor = applyMosaic(vTexSamplingCoords, intensity);
                        } else { // GAUSSIAN or PRIVACY_BOX
                            gl_FragColor = applyDenseGaussian(vTexSamplingCoords, intensity);
                        }
                        return;
                    }
                }

                gl_FragColor = texture2D(uTexSampler, vTexSamplingCoords);
            }
        """
    }

    init {
        try {
            glProgram = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
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
            val activeSpecs = blurSpecs.filter { spec ->
                currentTimeMs in spec.startTimeMs..spec.endTimeMs
            }.take(MAX_CONCURRENT_BLURS)

            val activeCount = activeSpecs.size
            glProgram.setIntUniform("uActiveCount", activeCount)

            if (activeCount > 0) {
                val shapes = IntArray(MAX_CONCURRENT_BLURS)
                val types = IntArray(MAX_CONCURRENT_BLURS)
                val bounds = FloatArray(MAX_CONCURRENT_BLURS * 4)
                val intensities = FloatArray(MAX_CONCURRENT_BLURS)

                for (i in 0 until activeCount) {
                    val spec = activeSpecs[i]
                    shapes[i] = when (spec.shape) {
                        BlurShape.RECTANGLE -> 0
                        BlurShape.CIRCLE -> 1
                        BlurShape.FULL_FRAME -> 2
                    }
                    types[i] = when (spec.type) {
                        BlurType.GAUSSIAN -> 0
                        BlurType.MOSAIC -> 1
                        BlurType.PRIVACY_BOX -> 2
                    }
                    val offset = i * 4
                    bounds[offset] = spec.bounds.left
                    bounds[offset + 1] = spec.bounds.top
                    bounds[offset + 2] = spec.bounds.right
                    bounds[offset + 3] = spec.bounds.bottom

                    intensities[i] = spec.intensity
                }

                glProgram.setIntsUniform("uShapes", shapes)
                glProgram.setIntsUniform("uTypes", types)
                glProgram.setFloatsUniform("uBounds", bounds)
                glProgram.setFloatsUniform("uIntensities", intensities)
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