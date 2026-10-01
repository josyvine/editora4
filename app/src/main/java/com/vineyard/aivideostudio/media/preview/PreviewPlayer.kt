package com.vineyard.aivideostudio.media.preview

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

class PreviewPlayer(private val context: Context) {

    private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionPollJob: Job? = null

    private var videoPlayer: ExoPlayer? = null
    private var commentaryAudioPlayer: ExoPlayer? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _isOriginalAudioMuted = MutableStateFlow(true)
    val isOriginalAudioMuted: StateFlow<Boolean> = _isOriginalAudioMuted.asStateFlow()

    private val _hasCommentaryTrack = MutableStateFlow(false)
    val hasCommentaryTrack: StateFlow<Boolean> = _hasCommentaryTrack.asStateFlow()

    fun getPlayer(): ExoPlayer {
        return videoPlayer ?: createVideoPlayer().also { videoPlayer = it }
    }

    private fun createVideoPlayer(): ExoPlayer {
        val player = ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            volume = if (_isOriginalAudioMuted.value) 0f else 1f

            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                    if (playing) {
                        startPositionPolling()
                        commentaryAudioPlayer?.play()
                    } else {
                        stopPositionPolling()
                        commentaryAudioPlayer?.pause()
                        _currentPosition.value = currentPosition.coerceAtLeast(0L)
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        _duration.value = duration.coerceAtLeast(0L)
                    } else if (playbackState == Player.STATE_ENDED) {
                        _isPlaying.value = false
                        stopPositionPolling()
                        commentaryAudioPlayer?.pause()
                        commentaryAudioPlayer?.seekTo(0L)
                    }
                }
            })
        }
        return player
    }

    private fun createAudioPlayer(): ExoPlayer {
        return ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            volume = 1.0f
        }
    }

    /**
     * Loads the video URI, optionally attaches a separate commentary audio track,
     * and enforces stripping/muting of the original audio.
     */
    fun setMedia(
        videoUri: Uri,
        commentaryAudioUri: Uri? = null,
        muteOriginalAudio: Boolean = true,
        playWhenReady: Boolean = false
    ) {
        val vPlayer = getPlayer()
        _isOriginalAudioMuted.value = muteOriginalAudio
        vPlayer.volume = if (muteOriginalAudio) 0f else 1f

        vPlayer.setMediaItem(MediaItem.fromUri(videoUri))
        vPlayer.playWhenReady = playWhenReady
        vPlayer.prepare()

        // Configure separate commentary soundtrack if provided
        if (commentaryAudioUri != null) {
            val aPlayer = commentaryAudioPlayer ?: createAudioPlayer().also { commentaryAudioPlayer = it }
            aPlayer.setMediaItem(MediaItem.fromUri(commentaryAudioUri))
            aPlayer.playWhenReady = playWhenReady
            aPlayer.prepare()
            _hasCommentaryTrack.value = true
        } else {
            commentaryAudioPlayer?.release()
            commentaryAudioPlayer = null
            _hasCommentaryTrack.value = false
        }
    }

    fun setMediaUri(uri: Uri, playWhenReady: Boolean = false) {
        setMedia(
            videoUri = uri,
            commentaryAudioUri = null,
            muteOriginalAudio = _isOriginalAudioMuted.value,
            playWhenReady = playWhenReady
        )
    }

    fun play() {
        val vPlayer = getPlayer()
        val currentMs = vPlayer.currentPosition
        commentaryAudioPlayer?.seekTo(currentMs)
        commentaryAudioPlayer?.play()
        vPlayer.play()
    }

    fun pause() {
        videoPlayer?.pause()
        commentaryAudioPlayer?.pause()
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceAtLeast(0L)
        videoPlayer?.seekTo(target)
        commentaryAudioPlayer?.seekTo(target)
        _currentPosition.value = target
    }

    fun setMuteOriginalAudio(mute: Boolean) {
        _isOriginalAudioMuted.value = mute
        videoPlayer?.volume = if (mute) 0f else 1f
    }

    fun setCommentaryVolume(volume: Float) {
        commentaryAudioPlayer?.volume = volume.coerceIn(0f, 1f)
    }

    private fun startPositionPolling() {
        positionPollJob?.cancel()
        positionPollJob = playerScope.launch {
            while (isActive && _isPlaying.value) {
                videoPlayer?.let { vp ->
                    val pos = vp.currentPosition
                    _currentPosition.value = pos

                    // Drift correction: keep commentary audio synchronized within 75ms of video frames
                    commentaryAudioPlayer?.let { ap ->
                        if (ap.isPlaying && abs(ap.currentPosition - pos) > 75L) {
                            ap.seekTo(pos)
                        }
                    }
                }
                delay(33L) // ~30 fps polling rate for responsive subtitle rendering
            }
        }
    }

    private fun stopPositionPolling() {
        positionPollJob?.cancel()
        positionPollJob = null
    }

    fun release() {
        stopPositionPolling()
        playerScope.cancel()

        videoPlayer?.release()
        videoPlayer = null

        commentaryAudioPlayer?.release()
        commentaryAudioPlayer = null

        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
        _hasCommentaryTrack.value = false
    }
}