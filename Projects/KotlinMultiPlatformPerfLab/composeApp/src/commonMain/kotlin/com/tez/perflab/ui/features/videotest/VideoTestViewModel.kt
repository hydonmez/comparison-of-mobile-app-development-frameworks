package com.tez.perflab.ui.features.videotest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tez.perflab.engines.VideoEngine
import com.tez.perflab.managers.ExportManager
import com.tez.perflab.managers.PerformanceManager
import com.tez.perflab.managers.PlatformContext
import com.tez.perflab.managers.lockScreenOrientation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A cross-platform, lifecycle-aware controller managing full-screen video playback benchmarks.
 * Centralizes orientation lockdowns, hardware telemetry initiation, and deterministic
 * serialization into a single unified layer.
 */
class VideoTestViewModel : ViewModel() {

    val engine = VideoEngine
    private val perfManager = PerformanceManager

    // Caches context strictly to execute background CSV persistence, restore OS-level
    // screen orientation locks, and invoke native Share Intent APIs.
    private var currentContext: PlatformContext? = null

    // Utilizing primitive types for internal execution tracking to minimize state allocation overhead.
    private var isTestingActive = false

    // Retains the execution state for accurate telemetry serialization nomenclature.
    private var lastTestSuffix = "Partial"

    // MARK: - Reactive UI State

    private val _isFullScreen = MutableStateFlow(false)
    val isFullScreen: StateFlow<Boolean> = _isFullScreen.asStateFlow()

    private val _isReportReady = MutableStateFlow(false)
    val isReportReady: StateFlow<Boolean> = _isReportReady.asStateFlow()

    init {
        setupBindings()
    }

    private fun setupBindings() {
        viewModelScope.launch {
            engine.hasEnded.collect { ended ->
                if (ended && isTestingActive) {
                    println("[Telemetry] Video EOF Detected -> Gracefully terminating the active benchmark suite.")
                    stopTest(isFinished = true)
                }
            }
        }
    }

    fun prepareVideo(context: PlatformContext) {
        this.currentContext = context
        _isReportReady.value = false
        engine.prepareVideo(context, "test_video_1080p", "mp4")
    }

    fun startTest(context: PlatformContext) {
        this.currentContext = context
        if (engine.getPlayer() == null) {
            prepareVideo(context)
        }

        _isReportReady.value = false

        // 1. Hardware Override: Lock to Landscape
        lockScreenOrientation(context, isLandscape = true)

        _isFullScreen.value = true
        isTestingActive = true

        // 2. Initiate Hardware Telemetry & Playback
        perfManager.startMonitoring(context)
        engine.playFromStart()
    }

    fun stopTest(isFinished: Boolean = false) {
        if (!isTestingActive) return
        val context = currentContext ?: return

        isTestingActive = false
        _isFullScreen.value = false

        // 1. Restore OS UX Constraints
        lockScreenOrientation(context, isLandscape = false)

        // 2. Terminate Hardware Decoders and Telemetry Trackers
        engine.stop()
        perfManager.stopMonitoring()

        // Release the context reference immediately post-execution
        // to prevent retention leaks prior to the export phase.
        currentContext = null

        // Caches the terminal state identifier for subsequent I/O operations.
        lastTestSuffix = if (isFinished) "Complete" else "Partial"

        _isReportReady.value = true
    }

    fun shareResults(context: PlatformContext) {
        // Delegating file serialization to the I/O thread pool to prevent
        // CPU-bound thread starvation during heavy disk writes.
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ExportManager.generateAndShareCSV(
                    logs = perfManager.currentLogs.value,
                    testName = "Video_FullScreen_KMP_$lastTestSuffix",
                    context = context
                )
            } catch (e: Exception) {
                println("[Export Error] ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        engine.release()
        currentContext?.let { lockScreenOrientation(it, isLandscape = false) }
        currentContext = null
    }
}