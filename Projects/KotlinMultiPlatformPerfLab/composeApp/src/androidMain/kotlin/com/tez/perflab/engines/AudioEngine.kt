package com.tez.perflab.engines

import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import com.tez.perflab.managers.AndroidPlatformContext
import com.tez.perflab.managers.PlatformContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Android-specific Media3 (ExoPlayer) implementation for audio playback.
 * Configured with custom buffer constraints and strict lifecycle management
 * for precise performance benchmarking.
 */
actual object NativeAudioPlayer {

    private var player: ExoPlayer? = null

    // Retains an explicit reference to the Player.Listener to enable proper deregistration on release.
    // This prevents listener accumulation across repeated loadAudio calls.
    private var playerListener: Player.Listener? = null

    @OptIn(UnstableApi::class)
    actual suspend fun loadAudio(context: PlatformContext, fileName: String): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->

            val androidContext = (context as AndroidPlatformContext).androidContext.applicationContext

            // Deregister any prior listener before releasing the player to prevent dangling callbacks.
            playerListener?.let { player?.removeListener(it) }
            playerListener = null
            player?.release()

            continuation.invokeOnCancellation {
                playerListener?.let { player?.removeListener(it) }
                playerListener = null
                player?.release()
                player = null
            }

            // Buffer configuration:
            // - minBuffer / maxBuffer (5000ms): Provides headroom for sustained decoding without continuous Disk I/O interrupts.
            // - bufferForPlayback (1000ms): Minimum fill threshold before playback initialization.
            // - bufferForPlaybackAfterRebuffer (2500ms): Elevated to break the rebuffer feedback loop, preventing
            //   artificial memory spikes caused by continuous decoder pipeline reallocation.
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs                      */ 5_000,
                    /* maxBufferMs                      */ 5_000,
                    /* bufferForPlaybackMs              */ 1_000,
                    /* bufferForPlaybackAfterRebufferMs */ 2_500
                )
                .build()

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()

            player = ExoPlayer.Builder(androidContext)
                .setLoadControl(loadControl)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .build()

            player?.setMediaItem(MediaItem.fromUri("file:///android_asset/$fileName.mp3"))

            // Forces processing of audio frames during seek operations.
            player?.setSeekParameters(SeekParameters.EXACT)

            playerListener = object : Player.Listener {
                private var isResumed = false

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            if (!isResumed && continuation.isActive) {
                                isResumed = true
                                continuation.resume(true)
                            }
                        }
                        Player.STATE_ENDED -> {
                            // Signals the KMP layer that the end of the file has been reached.
                            AudioEngine.onPlaybackEnded()
                        }
                        else -> {}
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (!isResumed && continuation.isActive) {
                        isResumed = true
                        continuation.resume(false)
                    }
                }
            }
            player?.addListener(playerListener!!)
            player?.prepare()
        }
    }

    actual fun play() { player?.play() }
    actual fun pause() { player?.pause() }
    actual fun seek(toSeconds: Double) { player?.seekTo((toSeconds * 1000).toLong()) }

    /**
     * Releases player resources.
     */
    actual fun release() {
        // Explicit listener deregistration must precede player.release() to prevent dangling callbacks.
        playerListener?.let { player?.removeListener(it) }
        playerListener = null
        player?.release()
        player = null
    }

    actual fun getDurationSeconds(): Double {
        val duration = player?.duration ?: C.TIME_UNSET
        return if (duration == C.TIME_UNSET) 0.0 else duration / 1000.0
    }

    actual fun getCurrentPositionSeconds(): Double {
        return (player?.currentPosition ?: 0L) / 1000.0
    }
}