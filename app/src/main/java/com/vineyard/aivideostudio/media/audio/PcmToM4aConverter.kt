package com.vineyard.aivideostudio.media.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import com.vineyard.aivideostudio.core.common.DispatcherProvider
import com.vineyard.aivideostudio.core.result.AppError
import com.vineyard.aivideostudio.core.result.AppResult
import com.vineyard.aivideostudio.processing.logger.ProcessingLogger
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer

/**
 * Encodes raw PCM audio stream files into standard AAC/M4A containers.
 */
class PcmToM4aConverter(
    private val dispatcherProvider: DispatcherProvider,
    private val logger: ProcessingLogger
) {

    companion object {
        private const val TAG = "PcmToM4aConverter"
        private const val TIMEOUT_US = 10_000L
        private const val BUFFER_SIZE = 16_384
        private const val DEFAULT_SAMPLE_RATE = 24_000 // Standard Gemini Live Bidi output rate
        private const val DEFAULT_CHANNEL_COUNT = 1   // Mono
        private const val DEFAULT_BIT_RATE = 128_000   // 128 kbps AAC
    }

    /**
     * Converts a raw PCM file to an AAC encoded M4A file.
     *
     * @param pcmFile Source PCM audio file (16-bit signed Little-Endian).
     * @param outputM4aFile Target destination M4A file.
     * @param sampleRate Sampling rate in Hz (default: 24,000 Hz).
     * @param channelCount Number of audio channels (default: 1 mono).
     * @param bitRate Target AAC bitrate in bps (default: 128,000 bps).
     */
    suspend fun convert(
        pcmFile: File,
        outputM4aFile: File,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        channelCount: Int = DEFAULT_CHANNEL_COUNT,
        bitRate: Int = DEFAULT_BIT_RATE
    ): AppResult<File> = withContext(dispatcherProvider.io) {
        if (!pcmFile.exists() || pcmFile.length() == 0L) {
            val err = "Cannot convert PCM to M4A: Source file is missing or empty."
            Log.e(TAG, err)
            return@withContext AppResult.Error(AppError.MediaProcessingError(err))
        }

        outputM4aFile.parentFile?.mkdirs()
        if (outputM4aFile.exists()) {
            outputM4aFile.delete()
        }

        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var inputStream: FileInputStream? = null

        try {
            Log.i(TAG, "Starting PCM to M4A conversion (Size: ${pcmFile.length()} bytes, Rate: ${sampleRate}Hz)...")

            // 1. Configure AAC Audio Format
            val audioFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRate,
                channelCount
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, BUFFER_SIZE)
            }

            // 2. Initialize MediaCodec Encoder
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            // 3. Initialize MediaMuxer
            muxer = MediaMuxer(outputM4aFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            inputStream = FileInputStream(pcmFile)

            val bufferInfo = MediaCodec.BufferInfo()
            val tempBuffer = ByteArray(BUFFER_SIZE)
            var audioTrackIndex = -1
            var isMuxerStarted = false
            var isEndOfInputStream = false
            var totalBytesRead = 0L

            // 16-bit PCM = 2 bytes per sample per channel
            val bytesPerSecond = sampleRate * channelCount * 2L

            // 4. Processing Loop: Feed PCM and Extract Encoded AAC Frames
            while (true) {
                // Feed input buffers if not at end of file
                if (!isEndOfInputStream) {
                    val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer: ByteBuffer? = codec.getInputBuffer(inputBufferIndex)
                        inputBuffer?.clear()

                        val bytesRead = inputStream.read(tempBuffer, 0, tempBuffer.size)
                        if (bytesRead <= 0) {
                            isEndOfInputStream = true
                            codec.queueInputBuffer(
                                inputBufferIndex,
                                0,
                                0,
                                (totalBytesRead * 1_000_000L) / bytesPerSecond,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                        } else {
                            inputBuffer?.put(tempBuffer, 0, bytesRead)
                            val presentationTimeUs = (totalBytesRead * 1_000_000L) / bytesPerSecond
                            totalBytesRead += bytesRead

                            codec.queueInputBuffer(
                                inputBufferIndex,
                                0,
                                bytesRead,
                                presentationTimeUs,
                                0
                            )
                        }
                    }
                }

                // Drain output buffers from encoder
                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)

                when {
                    outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (isEndOfInputStream) {
                            // If stream ended and no more output, break
                            break
                        }
                    }
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (isMuxerStarted) {
                            Log.w(TAG, "Output format changed more than once!")
                        } else {
                            val newFormat = codec.outputFormat
                            audioTrackIndex = muxer.addTrack(newFormat)
                            muxer.start()
                            isMuxerStarted = true
                            Log.d(TAG, "MediaMuxer started with track index: $audioTrackIndex")
                        }
                    }
                    outputBufferIndex >= 0 -> {
                        val outputBuffer: ByteBuffer? = codec.getOutputBuffer(outputBufferIndex)

                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            bufferInfo.size = 0
                        }

                        if (bufferInfo.size != 0 && outputBuffer != null && isMuxerStarted) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(audioTrackIndex, outputBuffer, bufferInfo)
                        }

                        codec.releaseOutputBuffer(outputBufferIndex, false)

                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            Log.i(TAG, "End of stream flag reached on encoder output.")
                            break
                        }
                    }
                }
            }

            Log.i(TAG, "PCM to M4A conversion successful! Output file: ${outputM4aFile.absolutePath} (Size: ${outputM4aFile.length()} bytes)")
            AppResult.Success(outputM4aFile)

        } catch (e: Exception) {
            Log.e(TAG, "Exception during PCM to M4A encoding: ${e.message}", e)
            AppResult.Error(AppError.MediaProcessingError("Failed encoding PCM to M4A: ${e.message}", e))
        } finally {
            try {
                inputStream?.close()
            } catch (_: Exception) {}
            try {
                codec?.stop()
                codec?.release()
            } catch (_: Exception) {}
            try {
                muxer?.stop()
                muxer?.release()
            } catch (_: Exception) {}
        }
    }
}