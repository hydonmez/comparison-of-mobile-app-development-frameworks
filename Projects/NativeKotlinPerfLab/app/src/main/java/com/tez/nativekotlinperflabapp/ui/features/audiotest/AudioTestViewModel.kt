package com.tez.nativekotlinperflabapp.ui.features.audiotest

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tez.nativekotlinperflabapp.core.engines.AudioEngine
import com.tez.nativekotlinperflabapp.core.managers.ExportManager
import com.tez.nativekotlinperflabapp.core.managers.PerformanceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * View model orchestrating the audio test logic and playback engine interactions.
 */
class AudioTestViewModel : ViewModel() {

    private val engine = AudioEngine
    private val perfManager = PerformanceManager
    private val exportManager = ExportManager

    private var currentContext: Context? = null
    private var telemetryStartJob: Job? = null

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _showShareSheet = MutableStateFlow(false)
    val showShareSheet: StateFlow<Boolean> = _showShareSheet.asStateFlow()

    private val _exportUri = MutableStateFlow<Uri?>(null)
    val exportUri: StateFlow<Uri?> = _exportUri.asStateFlow()

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
                // Automatically stops the test when playback naturally reaches the end.
                if (!playing && _isTesting.value && engine.currentTime.value >= engine.totalDuration.value - 0.2) {
                    stopTest(isFinished = true)
                }
            }
        }
    }

    fun loadTestAudio(context: Context) {
        this.currentContext = context.applicationContext
        val resourceName = "test_audio_high.mp3"
        val assetUri = "asset:///$resourceName".toUri()

        viewModelScope.launch {
            val success = engine.loadAudio(context, assetUri)
            _isAudioLoaded.value = success
            if (!success) {
                _errorMessage.value = "Resource Initialization Error: '$resourceName' missing."
            }
        }
    }

    fun startTest(context: Context) {
        this.currentContext = context.applicationContext
        if (!_isAudioLoaded.value || _isTesting.value) return

        if (currentTime.value >= totalDuration.value - 0.1) {
            engine.seek(0.0)
        }

        _showShareSheet.value = false
        _exportUri.value = null
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
            val exportedFile = exportManager.generateCSV(
                context = context,
                logs = perfManager.currentLogs.value,
                testName = if (isFinished) "Audio_Native_Complete" else "Audio_Native_Partial"
            )

            exportedFile?.let { file ->
                _exportUri.value = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )
                delay(500)
                _showShareSheet.value = true
            } ?: run {
                _errorMessage.value = "I/O Failure: Telemetry serialization failed."
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

    /**
     * Formats the timestamp using raw string interpolation instead of java.util.Formatter
     * to avoid unnecessary memory allocations during rapid UI updates.
     */
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