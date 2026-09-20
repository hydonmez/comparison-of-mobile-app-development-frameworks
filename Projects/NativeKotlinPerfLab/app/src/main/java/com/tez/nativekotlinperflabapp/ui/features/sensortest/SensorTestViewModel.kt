package com.tez.nativekotlinperflabapp.ui.features.sensortest

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tez.nativekotlinperflabapp.core.managers.ExportManager
import com.tez.nativekotlinperflabapp.core.managers.PerformanceManager
import com.tez.nativekotlinperflabapp.core.managers.SensorTestManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource

/**
 * A lifecycle-aware orchestrator managing high-frequency (100Hz) sensor telemetry.
 */
class SensorTestViewModel : ViewModel() {

    // MARK: - UI State Observables

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _status = MutableStateFlow("Sensors Ready")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _isReportReady = MutableStateFlow(false)
    val isReportReady: StateFlow<Boolean> = _isReportReady.asStateFlow()

    private val _progress = MutableStateFlow(0.0)
    val progress: StateFlow<Double> = _progress.asStateFlow()

    // --- HOISTED TELEMETRY STATE ---

    private val _accelData = MutableStateFlow(floatArrayOf(0f, 0f, 0f))
    val accelData: StateFlow<FloatArray> = _accelData.asStateFlow()

    private val _gyroData = MutableStateFlow(floatArrayOf(0f, 0f, 0f))
    val gyroData: StateFlow<FloatArray> = _gyroData.asStateFlow()

    private val _magnetData = MutableStateFlow(floatArrayOf(0f, 0f, 0f))
    val magnetData: StateFlow<FloatArray> = _magnetData.asStateFlow()

    private val _stepCount = MutableStateFlow(0)
    val stepCount: StateFlow<Int> = _stepCount.asStateFlow()

    private val performance = PerformanceManager

    private val testDuration = 60.0
    private var timerJob: Job? = null

    fun startTest(context: Context) {
        if (_isRunning.value) return

        val appContext = context.applicationContext

        _isRunning.value = true
        _status.value = "Acquiring Telemetry (100Hz)..."
        _isReportReady.value = false
        _progress.value = 0.0

        performance.startMonitoring(appContext)

        SensorTestManager.startSensors(
            context = appContext,
            accelCallback = { data -> _accelData.value = data },
            gyroCallback = { data -> _gyroData.value = data },
            magnetCallback = { data -> _magnetData.value = data },
            stepCallback = { count -> _stepCount.value = count }
        )

        // Handles isolated progress tracking
        timerJob = viewModelScope.launch(Dispatchers.Default) {
            val timeSource = TimeSource.Monotonic
            val mark = timeSource.markNow()

            while (isActive) {
                delay(100)
                val elapsedSeconds = mark.elapsedNow().inWholeMilliseconds / 1000.0
                _progress.value = (elapsedSeconds / testDuration).coerceAtMost(1.0)

                if (elapsedSeconds >= testDuration) {
                    stopTest(isFinished = true)
                    break
                }
            }
        }
    }

    fun stopTest(isFinished: Boolean = false) {
        if (!_isRunning.value) return

        _isRunning.value = false
        _status.value = if (isFinished) "✅ Test Finalized" else "🛑 Terminated"

        timerJob?.cancel()
        SensorTestManager.stopSensors()
        performance.stopMonitoring()

        // Flush stale telemetry data
        _accelData.value = floatArrayOf(0f, 0f, 0f)
        _gyroData.value = floatArrayOf(0f, 0f, 0f)
        _magnetData.value = floatArrayOf(0f, 0f, 0f)
        _stepCount.value = 0

        if (isFinished) {
            _progress.value = 1.0
            _isReportReady.value = true
        } else {
            _isReportReady.value = false
            _progress.value = 0.0
        }
    }

    /**
     * Defers CSV generation to an explicit user intent to prevent UI freezing.
     */
    fun exportResults(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val csvFile = ExportManager.generateCSV(
                    context = context,
                    logs = performance.currentLogs.value,
                    testName = "Sensor_Native_Stress_100Hz"
                )
                csvFile?.let { file ->
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file
                    )
                    withContext(Dispatchers.Main) {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Export Results (CSV)"))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _status.value = "❌ Export Error: ${e.message}"
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        SensorTestManager.stopSensors()
        if (_isRunning.value) performance.stopMonitoring()
        timerJob?.cancel()
    }
}