package com.tez.perflab.engines

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.tez.perflab.managers.AndroidPlatformContext
import com.tez.perflab.managers.PlatformContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android-specific Media3 (ExoPlayer) wrapper for the video benchmarking suite.
 *
 * Buffer parameters are configured to suppress speculative pre-caching, ensuring
 * memory measurements reflect active hardware decoding. The rebuffer threshold is set
 * above the playback threshold to prevent continuous rebuffer cycling and avoid
 * artificial memory spikes.
 */
actual class PlatformMediaPlayer(val exoPlayer: ExoPlayer)

actual object VideoEngine {

    private var _player: ExoPlayer? = null
    private var _platformPlayer: PlatformMediaPlayer? = null

    private val _hasEnded = MutableStateFlow(false)
    actual val hasEnded: StateFlow<Boolean> = _hasEnded.asStateFlow()

    private var currentMediaUri: String? = null

    // Retains an explicit reference to the Player.Listener to enable proper deregistration 
    // on release, preventing listener accumulation across repeated prepareVideo calls.
    private var playerListener: Player.Listener? = null

    actual fun getPlayer(): PlatformMediaPlayer? = _platformPlayer

    @OptIn(UnstableApi::class)
    actual fun prepareVideo(context: PlatformContext, fileName: String, extension: String) {
        val assetPath = "file:///android_asset/$fileName.$extension"

        if (_player != null && currentMediaUri == assetPath) return
        if (_player != null) release()

        val androidContext = (context as AndroidPlatformContext).androidContext.applicationContext

        // Buffer configuration:
        // - minBuffer / maxBuffer (5000ms): Constrains speculative pre-caching while providing headroom for hardware decoding.
        // - bufferForPlayback (1000ms): Minimum fill level before initial playback begins.
        // - bufferForPlaybackAfterRebuffer (2500ms): Elevated above the playback threshold to break the rebuffer feedback loop, 
        //   preventing artificial memory spikes caused by continuous decoder reallocation.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs                      */ 5_000,
                /* maxBufferMs                      */ 5_000,
                /* bufferForPlaybackMs              */ 1_000,
                /* bufferForPlaybackAfterRebufferMs */ 2_500
            )
            .build()

        val exoPlayerInstance = ExoPlayer.Builder(androidContext)
            .setLoadControl(loadControl)
            .build().apply {
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                playWhenReady = false
                repeatMode = Player.REPEAT_MODE_OFF
            }

        _player = exoPlayerInstance
        _platformPlayer = PlatformMediaPlayer(exoPlayerInstance)
        currentMediaUri = assetPath

        Log.d("PerfLab_Video", "Loading assets: $assetPath")

        exoPlayerInstance.setMediaItem(MediaItem.fromUri(assetPath))

        playerListener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY     -> Log.d("PerfLab_Video", "Decoder ready")
                    Player.STATE_ENDED     -> {
                        Log.d("PerfLab_Video", "Playback completed")
                        _hasEnded.value = true
                    }
                    Player.STATE_BUFFERING -> Log.d("PerfLab_Video", "Buffering")
                    else -> {}
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("PerfLab_Video", "Decoder error: ${error.message}", error)
                _hasEnded.value = true
            }
        }
        exoPlayerInstance.addListener(playerListener!!)

        exoPlayerInstance.prepare()
    }

    actual fun playFromStart() {
        _hasEnded.value = false
        _player?.let {
            it.seekTo(0)
            it.play()
            Log.d("PerfLab_Video", "Playback started")
        }
    }

    actual fun stop() {
        _player?.pause()
        Log.d("PerfLab_Video", "Playback paused")
    }

    actual fun release() {
        // Explicit listener deregistration must precede player.release() to prevent dangling callbacks.
        playerListener?.let { _player?.removeListener(it) }
        playerListener = null

        _player?.release()
        _player = null
        _platformPlayer = null
        currentMediaUri = null
        _hasEnded.value = false

        Log.d("PerfLab_Video", "Player released")
    }
}