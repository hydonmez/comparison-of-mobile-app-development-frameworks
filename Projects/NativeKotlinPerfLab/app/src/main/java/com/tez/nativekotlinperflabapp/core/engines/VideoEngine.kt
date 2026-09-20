package com.tez.nativekotlinperflabapp.core.engines

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A hardware-accelerated video playback engine using Media3 (ExoPlayer).
 * Loads video files directly from the assets folder for optimized initialization.
 */
object VideoEngine {

    private var _player: ExoPlayer? = null
    val player: ExoPlayer? get() = _player

    private val _hasEnded = MutableStateFlow(false)
    val hasEnded: StateFlow<Boolean> = _hasEnded.asStateFlow()

    private var currentAssetPath: String? = null

    // Keeps a reference to the active Player.Listener to ensure
    // proper deregistration and prevent memory leaks.
    private var playerListener: Player.Listener? = null

    @OptIn(UnstableApi::class)
    fun prepareVideo(context: Context, fileName: String, extension: String) {
        val assetPath = "file:///android_asset/$fileName.$extension"

        if (_player != null && currentAssetPath == assetPath) return
        if (_player != null) release()

        val appContext = context.applicationContext

        // Configures buffer settings to ensure smooth playback and efficient memory usage.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs                      */ 5_000,
                /* maxBufferMs                      */ 5_000,
                /* bufferForPlaybackMs              */ 1_000,
                /* bufferForPlaybackAfterRebufferMs */ 2_500
            )
            .build()

        _player = ExoPlayer.Builder(appContext)
            .setLoadControl(loadControl)
            .build().apply {
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                playWhenReady = false
                repeatMode = Player.REPEAT_MODE_OFF
            }

        currentAssetPath = assetPath
        _player?.setMediaItem(MediaItem.fromUri(assetPath))

        // Registers the listener before prepare() to catch all early initialization states.
        playerListener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> Log.d("PerfLab_Video", "Native Decoder ready")
                    Player.STATE_ENDED -> {
                        Log.d("PerfLab_Video", "Native Playback completed")
                        _hasEnded.value = true
                    }
                    Player.STATE_BUFFERING -> Log.d("PerfLab_Video", "Native Buffering")
                    else -> {}
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("PerfLab_Video", "Decoder error: ${error.message}", error)
                _hasEnded.value = true
            }
        }
        _player?.addListener(playerListener!!)

        _player?.prepare()
    }

    fun playFromStart() {
        _hasEnded.value = false
        _player?.seekTo(0)
        _player?.play()
        Log.d("PerfLab_Video", "Native Playback started")
    }

    fun stop() {
        _player?.pause()
        Log.d("PerfLab_Video", "Native Playback paused")
    }

    fun release() {
        // Removes the listener before releasing the player to prevent dangling references.
        playerListener?.let { _player?.removeListener(it) }
        playerListener = null

        _player?.release()
        _player = null
        currentAssetPath = null
        _hasEnded.value = false

        Log.d("PerfLab_Video", "Native Player released")
    }
}