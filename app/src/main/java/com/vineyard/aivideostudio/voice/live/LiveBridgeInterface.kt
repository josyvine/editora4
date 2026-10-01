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
}