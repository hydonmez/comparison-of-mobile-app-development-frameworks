package com.tez.perflab.ui.features.audiotest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tez.perflab.engines.AudioEngine
import com.tez.perflab.managers.ExportManager
import com.tez.perflab.managers.PerformanceManager
import com.tez.perflab.managers.PlatformContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AudioTestViewModel : ViewModel() {

    private val engine = AudioEngine
    private val perfManager = PerformanceManager

    private var currentContext: PlatformContext? = null
    private var telemetryStartJob: Job? = null

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _showShareSheet = MutableStateFlow(false)
    val showShareSheet: StateFlow<Boolean> = _showShareSheet.asStateFlow()

    private val _isAudioLoaded = MutableStateFlow(false)
    val isAudioLoaded: StateFlow<Boolean> = _isAudioLoaded.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val currentTime: StateFlow<Double> = engine.currentTime
    val totalDuration: StateFlow<Double> = engine.totalDuration
    val isPlaying: StateFlow<Boolean> = engine.isPlaying

    init {
        setupEOFObserver()
    }

    private fun setupEOFObserver() {
        viewModelScope.launch {
            engine.isPlaying.collect { playing ->
                if (!playing && _isTesting.value && engine.currentTime.value >= engine.totalDuration.value - 0.2) {
                    stopTest(isFinished = true)
                }
            }
        }
    }

    fun loadTestAudio(context: PlatformContext) {
        this.currentContext = context
        val fileName = "test_audio_high"

        viewModelScope.launch {
            val success = engine.loadAudio(context, fileName)
            _isAudioLoaded.value = success
            if (!success) {
                _errorMessage.value = "Resource Missing: '$fileName' could not be loaded."
            }
        }
    }

    fun startTest(context: PlatformContext) {
        this.currentContext = context
        if (!_isAudioLoaded.value || _isTesting.value) return

        if (currentTime.value >= totalDuration.value - 0.1) {
            engine.seek(0.0)
        }

        _showShareSheet.value = false
        engine.play()

        _isTesting.value = true

        telemetryStartJob = viewModelScope.launch(Dispatchers.Default) {
            delay(100)
            if (_isTesting.value) {
                perfManager.startMonitoring(context)
            }
        }
    }

    fun stopTest(isFinished: Boolean = false) {
        if (!_isTesting.value) return
        val context = currentContext ?: return

        telemetryStartJob?.cancel()
        telemetryStartJob = null

        engine.pause()
        _isTesting.value = false
        perfManager.stopMonitoring()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val testSuffix = if (isFinished) "Complete" else "Partial"
                ExportManager.generateAndShareCSV(
                    context = context,
                    logs = perfManager.currentLogs.value,
                    testName = "Audio_KMP_$testSuffix"
                )
                delay(500)
                _showShareSheet.value = true
            } catch (e: Exception) {
                _errorMessage.value = "CSV Export Exception: ${e.message}"
            }
        }
    }

    fun resetShareSheet() {
        _showShareSheet.value = false
    }

    fun seekAudio(to: Double) {
        if (!_isAudioLoaded.value) return
        engine.seek(to)
        if (_isTesting.value && totalDuration.value > 0 && to >= (totalDuration.value - 0.2)) {
            stopTest(isFinished = true)
        }
    }

    fun skip(by: Double) {
        if (!_isAudioLoaded.value) return
        engine.skip(by)
        if (_isTesting.value && totalDuration.value > 0 && currentTime.value >= (totalDuration.value - 0.2)) {
            stopTest(isFinished = true)
        }
    }

    fun formatTime(time: Double): String {
        if (time.isNaN() || time.isInfinite()) return "00:00"
        val minutes = (time / 60).toInt()
        val seconds = (time % 60).toInt()
        val minStr = if (minutes < 10) "0$minutes" else minutes.toString()
        val secStr = if (seconds < 10) "0$seconds" else seconds.toString()
        return "$minStr:$secStr"
    }

    override fun onCleared() {
        super.onCleared()
        telemetryStartJob?.cancel()
        engine.release()
        currentContext = null
    }
}