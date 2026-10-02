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

            // Slot 0
            uniform int uActive0;
            uniform int uShape0;
            uniform int uType0;
            uniform vec4 uBounds0;
            uniform float uIntensity0;

            // Slot 1
            uniform int uActive1;
            uniform int uShape1;
            uniform int uType1;
            uniform vec4 uBounds1;
            uniform float uIntensity1;

            // Slot 2
            uniform int uActive2;
            uniform int uShape2;
            uniform int uType2;
            uniform vec4 uBounds2;
            uniform float uIntensity2;

            // Slot 3
            uniform int uActive3;
            uniform int uShape3;
            uniform int uType3;
            uniform vec4 uBounds3;
            uniform float uIntensity3;

            bool checkInside(vec2 uv, int shape, vec4 b) {
                float normY = 1.0 - uv.y;
                float normX = uv.x;

                if (shape == 2) { // FULL_FRAME
                    return true;
                }
                if (shape == 0) { // RECTANGLE
                    return normX >= b.x && normX <= b.z && normY >= b.y && normY <= b.w;
                }
                if (shape == 1) { // CIRCLE / ELLIPSE
                    vec2 center = vec2((b.x + b.z) * 0.5, (b.y + b.w) * 0.5);
                    float rx = (b.z - b.x) * 0.5;
                    float ry = (b.w - b.y) * 0.5;
                    float dist = pow((normX - center.x) / max(rx, 0.001), 2.0) +
                                 pow((normY - center.y) / max(ry, 0.001), 2.0);
                    return dist <= 1.0;
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
                // 17-Tap Multi-Ring Privacy Obfuscation Convolution
                sum += texture2D(uTexSampler, uv) * 0.18;

                sum += texture2D(uTexSampler, uv + vec2(-texOffset.x, 0.0)) * 0.11;
                sum += texture2D(uTexSampler, uv + vec2(texOffset.x, 0.0)) * 0.11;
                sum += texture2D(uTexSampler, uv + vec2(0.0, -texOffset.y)) * 0.11;
                sum += texture2D(uTexSampler, uv + vec2(0.0, -texOffset.y)) * 0.11;

                sum += texture2D(uTexSampler, uv + vec2(-texOffset.x, -texOffset.y)) * 0.07;
                sum += texture2D(uTexSampler, uv + vec2(texOffset.x, -texOffset.y)) * 0.07;
                sum += texture2D(uTexSampler, uv + vec2(-texOffset.x, texOffset.y)) * 0.07;
                sum += texture2D(uTexSampler, uv + vec2(texOffset.x, texOffset.y)) * 0.07;

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
                // Slot 0
                if (uActive0 == 1 && checkInside(vTexSamplingCoords, uShape0, uBounds0)) {
                    if (uType0 == 1) gl_FragColor = applyMosaic(vTexSamplingCoords, uIntensity0);
                    else gl_FragColor = applyDenseGaussian(vTexSamplingCoords, uIntensity0);
                    return;
                }

                // Slot 1
                if (uActive1 == 1 && checkInside(vTexSamplingCoords, uShape1, uBounds1)) {
                    if (uType1 == 1) gl_FragColor = applyMosaic(vTexSamplingCoords, uIntensity1);
                    else gl_FragColor = applyDenseGaussian(vTexSamplingCoords, uIntensity1);
                    return;
                }

                // Slot 2
                if (uActive2 == 1 && checkInside(vTexSamplingCoords, uShape2, uBounds2)) {
                    if (uType2 == 1) gl_FragColor = applyMosaic(vTexSamplingCoords, uIntensity2);
                    else gl_FragColor = applyDenseGaussian(vTexSamplingCoords, uIntensity2);
                    return;
                }

                // Slot 3
                if (uActive3 == 1 && checkInside(vTexSamplingCoords, uShape3, uBounds3)) {
                    if (uType3 == 1) gl_FragColor = applyMosaic(vTexSamplingCoords, uIntensity3);
                    else gl_FragColor = applyDenseGaussian(vTexSamplingCoords, uIntensity3);
                    return;
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
            }.take(4)

            // Bind Slot 0
            bindSlot(0, activeSpecs.getOrNull(0))
            // Bind Slot 1
            bindSlot(1, activeSpecs.getOrNull(1))
            // Bind Slot 2
            bindSlot(2, activeSpecs.getOrNull(2))
            // Bind Slot 3
            bindSlot(3, activeSpecs.getOrNull(3))

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

    private fun bindSlot(slotIndex: Int, spec: BlurSpec?) {
        val activeKey = "uActive$slotIndex"
        val shapeKey = "uShape$slotIndex"
        val typeKey = "uType$slotIndex"
        val boundsKey = "uBounds$slotIndex"
        val intensityKey = "uIntensity$slotIndex"

        if (spec != null) {
            glProgram.setIntUniform(activeKey, 1)
            glProgram.setIntUniform(
                shapeKey,
                when (spec.shape) {
                    BlurShape.RECTANGLE -> 0
                    BlurShape.CIRCLE -> 1
                    BlurShape.FULL_FRAME -> 2
                }
            )
            glProgram.setIntUniform(
                typeKey,
                when (spec.type) {
                    BlurType.GAUSSIAN -> 0
                    BlurType.MOSAIC -> 1
                    BlurType.PRIVACY_BOX -> 2
                }
            )
            glProgram.setFloatsUniform(
                boundsKey,
                floatArrayOf(
                    spec.bounds.left,
                    spec.bounds.top,
                    spec.bounds.right,
                    spec.bounds.bottom
                )
            )
            glProgram.setFloatUniform(intensityKey, spec.intensity)
        } else {
            glProgram.setIntUniform(activeKey, 0)
            glProgram.setIntUniform(shapeKey, 0)
            glProgram.setIntUniform(typeKey, 0)
            glProgram.setFloatsUniform(boundsKey, floatArrayOf(0f, 0f, 0f, 0f))
            glProgram.setFloatUniform(intensityKey, 0f)
        }
    }

    override fun release() {
        super.release()
        try {
            glProgram.delete()
        } catch (_: Exception) {}
    }
}