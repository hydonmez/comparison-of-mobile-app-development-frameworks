package com.tez.nativekotlinperflabapp.core.engines

import android.content.Context
import android.net.Uri
import android.util.Log
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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

/**
 * [EXOPLAYER ENGINE]
 * Coroutine-bound audio playback engine backed by Media3 (ExoPlayer).
 * Architected for strict deterministic benchmarking, ensuring 1:1 parity in memory footprint
 * management and hardware-accelerated playback initialization across platforms.
 */
object AudioEngine {

    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + supervisorJob)

    private var player: ExoPlayer? = null
    private var timeObserverJob: Job? = null

    /**
     * Explicit reference to the active playback listener.
     * Mandated for deterministic deregistration to prevent zombie callbacks and memory leaks
     * during high-frequency benchmark iterations.
     */
    private var playerListener: Player.Listener? = null

    private val _currentTime = MutableStateFlow(0.0)
    val currentTime: StateFlow<Double> = _currentTime.asStateFlow()

    private val _totalDuration = MutableStateFlow(0.0)
    val totalDuration: StateFlow<Double> = _totalDuration.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    @OptIn(UnstableApi::class)
    suspend fun loadAudio(context: Context, uri: Uri): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->

            // Extracts the application context to prevent Activity memory leaks within the singleton.
            val appContext = context.applicationContext

            // [RESOURCE TEARDOWN PHASE]
            // Purge active observers and listeners before re-allocation.
            removeTimeObserver()

            playerListener?.let { player?.removeListener(it) }
            playerListener = null
            player?.release()

            continuation.invokeOnCancellation {
                playerListener?.let { player?.removeListener(it) }
                playerListener = null
                player?.release()
                player = null
            }

            // Buffer strategy engineered to enforce predictable RAM constraints.
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(5000, 5000, 1000, 2_500)
                .build()

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()

            player = ExoPlayer.Builder(appContext)
                .setLoadControl(loadControl)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .build()

            player?.setMediaItem(MediaItem.fromUri(uri))

            // Enforces absolute seek precision to eliminate decoder heuristic advantages.
            player?.setSeekParameters(SeekParameters.EXACT)

            playerListener = object : Player.Listener {
                private var isResumed = false

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            val durationMs = player?.duration ?: C.TIME_UNSET
                            _totalDuration.value = if (durationMs == C.TIME_UNSET) 0.0 else durationMs / 1000.0

                            // Synchronization guard to prevent IllegalStateException upon multiple ready triggers.
                            if (!isResumed && continuation.isActive) {
                                isResumed = true
                                continuation.resume(true)
                            }
                        }
                        Player.STATE_ENDED -> {
                            _isPlaying.value = false
                            removeTimeObserver()
                        }
                        else -> {}
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e("AudioEngine", "Asset Loading Failed: ${error.localizedMessage}")
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

    fun play() {
        val duration = _totalDuration.value
        val current = _currentTime.value

        if (duration > 0 && current >= duration - 0.5) {
            seek(0.0)
        }

        player?.play()
        _isPlaying.value = true
        addTimeObserver()
    }

    fun pause() {
        player?.pause()
        _isPlaying.value = false
        removeTimeObserver()
    }

    fun seek(toSeconds: Double) {
        val timeMs = (toSeconds * 1000).toLong()
        player?.seekTo(timeMs)
        _currentTime.value = toSeconds
    }

    fun skip(bySeconds: Double) {
        val newTime = _currentTime.value + bySeconds
        seek(min(max(newTime, 0.0), _totalDuration.value))
    }

    private fun addTimeObserver() {
        timeObserverJob?.cancel()
        timeObserverJob = scope.launch {
            while (isActive && _isPlaying.value) {
                player?.let {
                    _currentTime.value = it.currentPosition / 1000.0
                }
                delay(100)
            }
        }
    }

    private fun removeTimeObserver() {
        timeObserverJob?.cancel()
        timeObserverJob = null
    }

    /**
     * [DETERMINISTIC MEMORY PURGE]
     * Explicitly flushes hardware buffers and invalidates all observers.
     * Listener deregistration must precede the player release to sever the message bus
     * and prevent dangling references within the GC.
     */
    fun release() {
        pause()
        playerListener?.let { player?.removeListener(it) }
        playerListener = null
        player?.release()
        player = null
        _currentTime.value = 0.0
        _totalDuration.value = 0.0
    }
}