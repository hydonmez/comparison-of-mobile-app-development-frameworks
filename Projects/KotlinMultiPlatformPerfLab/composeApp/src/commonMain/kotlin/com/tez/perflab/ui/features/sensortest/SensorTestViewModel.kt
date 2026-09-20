package com.tez.perflab.ui.features.sensortest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tez.perflab.managers.ExportManager
import com.tez.perflab.managers.PerformanceManager
import com.tez.perflab.managers.PlatformContext
import com.tez.perflab.managers.SensorTestManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * A lifecycle-aware orchestrator managing high-frequency (100Hz) sensor telemetry.
 */
class SensorTestViewModel : ViewModel() {

    // MARK: - Reactive UI State

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _status = MutableStateFlow("Sensors Ready")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _progress = MutableStateFlow(0.0)
    val progress: StateFlow<Double> = _progress.asStateFlow()

    private val _isReportReady = MutableStateFlow(false)
    val isReportReady: StateFlow<Boolean> = _isReportReady.asStateFlow()

    // ─── HOISTED TELEMETRY STATE ──────────────────────────────────────────────
    // StateFlow observables are hoisted from the hardware manager directly into the
    // ViewModel. This aligns memory management with native baselines,
    // ensuring identical Garbage Collection (GC) footprints during benchmarks.

    private val _accelData = MutableStateFlow(floatArrayOf(0f, 0f, 0f))
    val accelData: StateFlow<FloatArray> = _accelData.asStateFlow()

    private val _gyroData = MutableStateFlow(floatArrayOf(0f, 0f, 0f))
    val gyroData: StateFlow<FloatArray> = _gyroData.asStateFlow()

    private val _magnetData = MutableStateFlow(floatArrayOf(0f, 0f, 0f))
    val magnetData: StateFlow<FloatArray> = _magnetData.asStateFlow()

    private val _stepCount = MutableStateFlow(0)
    val stepCount: StateFlow<Int> = _stepCount.asStateFlow()

    // MARK: - Benchmark Execution State

    private val testDuration = 60.seconds
    private var timerJob: Job? = null

    fun startTest(context: PlatformContext) {
        if (_isRunning.value) return
        stopTest(isFinished = false)

        _isRunning.value = true
        _isReportReady.value = false
        _status.value = "Acquiring Telemetry (100Hz)..."
        _progress.value = 0.0

        PerformanceManager.startMonitoring(context)

        // Direct injection of state-mutating lambdas into the hardware engine.
        SensorTestManager.startSensors(
            context = context,
            onAccelUpdate = { _accelData.value = it },
            onGyroUpdate = { _gyroData.value = it },
            onMagnetUpdate = { _magnetData.value = it },
            onStepUpdate = { _stepCount.value = it }
        )

        // Drift-Free Isolated Progress Tracking
        timerJob = viewModelScope.launch(Dispatchers.Default) {
            val timeMark = TimeSource.Monotonic.markNow()

            while (isActive && timeMark.elapsedNow() < testDuration) {
                delay(100)
                val elapsed = timeMark.elapsedNow()
                _progress.value = (elapsed / testDuration).coerceIn(0.0, 1.0)
            }

            if (_isRunning.value) {
                stopTest(isFinished = true)
            }
        }
    }

    fun stopTest(isFinished: Boolean = false) {
        if (!_isRunning.value) return

        _isRunning.value = false
        _status.value = if (isFinished) "✅ Test Finalized" else "🛑 Terminated"

        // Hardware Teardown
        timerJob?.cancel()
        SensorTestManager.stopSensors()
        PerformanceManager.stopMonitoring()

        // Flush stale telemetry data to clear the UI graphs.
        _accelData.value = floatArrayOf(0f, 0f, 0f)
        _gyroData.value = floatArrayOf(0f, 0f, 0f)
        _magnetData.value = floatArrayOf(0f, 0f, 0f)
        _stepCount.value = 0

        if (isFinished) {
            _progress.value = 1.0
            _isReportReady.value = true
        }
    }

    fun shareResults(context: PlatformContext) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                ExportManager.generateAndShareCSV(
                    logs = PerformanceManager.currentLogs.value,
                    testName = "Sensor_KMP_Stress_100Hz",
                    context = context
                )
            } catch (e: Exception) {
                _status.value = "❌ Export Failed: ${e.message}"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        SensorTestManager.stopSensors()
        PerformanceManager.stopMonitoring()
    }
}