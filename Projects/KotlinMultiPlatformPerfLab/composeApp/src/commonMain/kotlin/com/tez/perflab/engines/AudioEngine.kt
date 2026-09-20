package com.tez.perflab.engines

import com.tez.perflab.managers.PlatformContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.min

/**
 * Platform-agnostic interface for audio playback.
 * Low-level decoding and buffering are delegated to native implementations (AVFoundation / Media3).
 */
expect object NativeAudioPlayer {
    suspend fun loadAudio(context: PlatformContext, fileName: String): Boolean
    fun play()
    fun pause()
    fun seek(toSeconds: Double)
    fun release()
    fun getDurationSeconds(): Double
    fun getCurrentPositionSeconds(): Double
}

/**
 * Audio orchestration engine managing playback state and UI synchronization via StateFlow.
 */
object AudioEngine {

    private val supervisorJob = SupervisorJob()

    // Ensures all StateFlow emissions occur on the Main thread for safe UI updates.
    private val scope = CoroutineScope(Dispatchers.Main + supervisorJob)
    private var timeObserverJob: Job? = null

    private val _currentTime = MutableStateFlow(0.0)
    val currentTime: StateFlow<Double> = _currentTime.asStateFlow()

    private val _totalDuration = MutableStateFlow(0.0)
    val totalDuration: StateFlow<Double> = _totalDuration.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    /**
     * Loads the audio asset and updates the total track duration.
     */
    suspend fun loadAudio(context: PlatformContext, fileName: String): Boolean {
        stopObservation()
        _isPlaying.value = false
        _currentTime.value = 0.0

        val isLoaded = NativeAudioPlayer.loadAudio(context, fileName)
        if (isLoaded) {
            _totalDuration.value = NativeAudioPlayer.getDurationSeconds()
        } else {
            _totalDuration.value = 0.0
        }
        return isLoaded
    }

    fun play() {
        val duration = _totalDuration.value
        val current = _currentTime.value

        // Rewind to start if playback is near the end.
        if (duration > 0 && current >= duration - 0.5) {
            seek(0.0)
        }

        NativeAudioPlayer.play()
        _isPlaying.value = true
        startObservation()
    }

    fun pause() {
        NativeAudioPlayer.pause()
        _isPlaying.value = false
        stopObservation()
    }

    fun seek(toSeconds: Double) {
        val safeTime = min(max(toSeconds, 0.0), _totalDuration.value)
        NativeAudioPlayer.seek(safeTime)
        _currentTime.value = safeTime
    }

    fun skip(bySeconds: Double) {
        seek(_currentTime.value + bySeconds)
    }

    fun release() {
        pause()
        NativeAudioPlayer.release()
        _currentTime.value = 0.0
        _totalDuration.value = 0.0
    }

    /**
     * Callback invoked by native engines when playback reaches the end of the file.
     */
    fun onPlaybackEnded() {
        _isPlaying.value = false
        stopObservation()
        _currentTime.value = _totalDuration.value
    }

    /**
     * Periodically polls the current playback position.
     */
    private fun startObservation() {
        timeObserverJob?.cancel()
        timeObserverJob = scope.launch {
            while (isActive && _isPlaying.value) {
                _currentTime.value = NativeAudioPlayer.getCurrentPositionSeconds()
                delay(100) // 10Hz refresh rate for UI updates
            }
        }
    }

    private fun stopObservation() {
        timeObserverJob?.cancel()
        timeObserverJob = null
    }
}