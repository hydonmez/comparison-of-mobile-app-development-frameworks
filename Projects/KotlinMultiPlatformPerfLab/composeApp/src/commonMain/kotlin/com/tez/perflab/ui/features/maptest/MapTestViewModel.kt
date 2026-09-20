package com.tez.perflab.ui.features.maptest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tez.perflab.generators.MapTestDataGenerator
import com.tez.perflab.managers.ExportManager
import com.tez.perflab.managers.PerformanceManager
import com.tez.perflab.managers.PlatformContext
import com.tez.perflab.models.MapPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A cross-platform engine responsible for managing geospatial rendering benchmarks.
 * Orchestrates a deterministic automated tour while strictly measuring hardware
 * telemetry across native map surfaces (Google Maps / Apple Maps).
 */
class MapTestViewModel : ViewModel() {

    // MARK: - Benchmark Constants
    val zoomOutLevel = 13f
    val zoomInLevel = 17.5f
    val defaultCenterLat = 41.0082
    val defaultCenterLon = 28.9784

    // MARK: - Reactive UI State

    data class CameraTarget(val latitude: Double, val longitude: Double, val zoom: Float)

    // Aligned the zoom level to 11.12f to match the native iOS implementation.
    private val _cameraTarget = MutableStateFlow(
        CameraTarget(defaultCenterLat, defaultCenterLon, 11.12f)
    )
    val cameraTarget: StateFlow<CameraTarget> = _cameraTarget.asStateFlow()

    private val _points = MutableStateFlow<List<MapPoint>>(emptyList())
    val points: StateFlow<List<MapPoint>> = _points.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _status = MutableStateFlow("Ready")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _isReportReady = MutableStateFlow(false)
    val isReportReady: StateFlow<Boolean> = _isReportReady.asStateFlow()

    private var testJob: Job? = null
    private val performance = PerformanceManager

    // MARK: - Benchmark Execution Pipeline

    fun startTest(context: PlatformContext) {
        stopTest(isFinished = false)

        _isRunning.value = true
        _isReportReady.value = false
        _status.value = "Initializing Geospatial Dataset..."

        // Uses Dispatchers.Main to mirror native main-thread execution behavior.
        viewModelScope.launch(Dispatchers.Main) {

            // Offloads data generation to the background (Dispatchers.Default).
            val newPoints = withContext(Dispatchers.Default) {
                MapTestDataGenerator.generatePoints(count = 20)
            }

            _points.value = newPoints
            _status.value = "Automated Tour in Progress..."

            performance.startMonitoring(context)

            startPinTour()
        }
    }

    private fun startPinTour() {
        testJob?.cancel()

        // The loop runs on Dispatchers.Main. 
        // Since delay() is non-blocking, the UI remains responsive and state updates reflect immediately.
        testJob = viewModelScope.launch(Dispatchers.Main) {
            delay(1000)

            for ((index, point) in _points.value.withIndex()) {
                if (!isActive || !_isRunning.value) break

                _status.value = "Target ${index + 1} /${_points.value.size} -> Panning ✈️"
                _cameraTarget.value = CameraTarget(point.latitude, point.longitude, zoomOutLevel)
                delay(1800)

                if (!isActive || !_isRunning.value) break

                _status.value = "Target ${index + 1} -> Inspecting Detail 🔍"
                _cameraTarget.value = CameraTarget(point.latitude, point.longitude, zoomInLevel)
                delay(1800)

                if (!isActive || !_isRunning.value) break

                if (index < _points.value.size - 1) {
                    _status.value = "Target ${index + 1} -> Ascending ⬆️"
                    _cameraTarget.value = CameraTarget(point.latitude, point.longitude, zoomOutLevel)
                    delay(1200)
                }
            }

            if (isActive && _isRunning.value) {
                _status.value = "Tour Finalized! Global Review 🌍"
                // Matches the native iOS zoom level of 11.2f
                _cameraTarget.value = CameraTarget(defaultCenterLat, defaultCenterLon, 11.2f)
                delay(3000)
                stopTest(isFinished = true)
            }
        }
    }

    // MARK: - Termination & Telemetry Export

    fun stopTest(isFinished: Boolean = false) {
        testJob?.cancel()
        testJob = null

        if (!_isRunning.value) return

        performance.stopMonitoring()
        _isRunning.value = false
        _status.value = if (isFinished) "Tour Completed ✅" else "Test Terminated 🛑"

        _isReportReady.value = isFinished
    }

    fun shareResults(context: PlatformContext) {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                // Offloads the export operation to a background thread
                withContext(Dispatchers.Default) {
                    ExportManager.generateAndShareCSV(
                        logs = performance.currentLogs.value,
                        testName = "Map_Optimized_Tour_KMP",
                        context = context,
                        customSummary = "Total_Points_Visited,${_points.value.size},,,,"
                    )
                }
            } catch (e: Exception) {
                _status.value = "Export Failed: ${e.message}"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (_isRunning.value) {
            performance.stopMonitoring()
        }
        testJob?.cancel()
    }
}