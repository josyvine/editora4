package com.vineyard.aivideostudio.voice.live

import android.webkit.JavascriptInterface

/**
 * Listener interface for events dispatched by the JavaScript Live Commentator engine.
 */
interface LiveCommentaryListener {
    fun onConnecting()
    fun onConnected()
    fun onAudioChunkReceived(base64PcmData: String)
    fun onCommentaryText(text: String)
    fun onCommentaryFinished()
    fun onError(errorMessage: String)
    fun onDiagnostic(message: String, category: String)
    fun onTargetCoordinatesReceived(targetId: String, left: Float, top: Float, right: Float, bottom: Float) {}
    fun onTargetCoordinatesBatchReceived(targetCoordinatesMap: Map<String, FloatArray>) {}
}

/**
 * JavaScript interface bridge bound to 'window.AndroidInterface' inside WebView.
 */
class LiveBridgeInterface(
    private val apiKeyProvider: () -> String,
    private val modelIdProvider: () -> String,
    private val voiceNameProvider: () -> String,
    private val listener: LiveCommentaryListener
) {

    @JavascriptInterface
    fun getGeminiApiKey(): String {
        return apiKeyProvider().trim()
    }

    @JavascriptInterface
    fun getModelId(): String {
        return modelIdProvider().trim()
    }

    @JavascriptInterface
    fun getVoiceName(): String {
        return voiceNameProvider().trim()
    }

    @JavascriptInterface
    fun onLiveSessionConnecting() {
        listener.onConnecting()
    }

    @JavascriptInterface
    fun onLiveSessionConnected() {
        listener.onConnected()
    }

    @JavascriptInterface
    fun onLiveAudioChunkReceived(base64PcmData: String) {
        if (base64PcmData.isNotEmpty()) {
            listener.onAudioChunkReceived(base64PcmData)
        }
    }

    @JavascriptInterface
    fun onLiveCommentaryText(text: String) {
        if (text.isNotEmpty()) {
            listener.onCommentaryText(text)
        }
    }

    @JavascriptInterface
    fun onLiveCommentaryFinished() {
        listener.onCommentaryFinished()
    }

    @JavascriptInterface
    fun onLiveError(errorMessage: String) {
        listener.onError(errorMessage)
    }

    @JavascriptInterface
    fun logDiagnostic(message: String, category: String) {
        listener.onDiagnostic(message, category)
    }

    @JavascriptInterface
    fun onTargetCoordinatesReceived(targetId: String, left: Double, top: Double, right: Double, bottom: Double) {
        val rawL = left.toFloat().coerceIn(0.0f, 1.0f)
        val rawT = top.toFloat().coerceIn(0.0f, 1.0f)
        val rawR = right.toFloat().coerceIn(0.0f, 1.0f)
        val rawB = bottom.toFloat().coerceIn(0.0f, 1.0f)

        val safeLeft = minOf(rawL, rawR)
        val safeRight = maxOf(rawL, rawR)
        val safeTop = minOf(rawT, rawB)
        val safeBottom = maxOf(rawT, rawB)

        listener.onTargetCoordinatesReceived(
            targetId = targetId.trim(),
            left = safeLeft,
            top = safeTop,
            right = safeRight,
            bottom = safeBottom
        )
    }

    @JavascriptInterface
    fun onTargetBatchReceived(jsonBatch: String) {
        if (jsonBatch.isBlank()) return
        try {
            val jsonObject = org.json.JSONObject(jsonBatch)
            val resultMap = mutableMapOf<String, FloatArray>()
            val keys = jsonObject.keys()

            while (keys.hasNext()) {
                val key = keys.next()
                val array = jsonObject.getJSONArray(key)
                if (array.length() >= 4) {
                    val l = array.getDouble(0).toFloat().coerceIn(0.0f, 1.0f)
                    val t = array.getDouble(1).toFloat().coerceIn(0.0f, 1.0f)
                    val r = array.getDouble(2).toFloat().coerceIn(0.0f, 1.0f)
                    val b = array.getDouble(3).toFloat().coerceIn(0.0f, 1.0f)

                    resultMap[key] = floatArrayOf(minOf(l, r), minOf(t, b), maxOf(l, r), maxOf(t, b))
                }
            }

            if (resultMap.isNotEmpty()) {
                listener.onTargetCoordinatesBatchReceived(resultMap)
            }
        } catch (_: Exception) {}
    }
}