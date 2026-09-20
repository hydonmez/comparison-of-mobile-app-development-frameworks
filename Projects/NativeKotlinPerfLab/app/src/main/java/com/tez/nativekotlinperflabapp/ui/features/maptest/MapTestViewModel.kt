package com.tez.nativekotlinperflabapp.ui.features.maptest

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.tez.nativekotlinperflabapp.core.generators.MapTestDataGenerator
import com.tez.nativekotlinperflabapp.core.managers.ExportManager
import com.tez.nativekotlinperflabapp.core.managers.PerformanceManager
import com.tez.nativekotlinperflabapp.models.MapPoint
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
 * Orchestrator that automates complex camera maneuvers across a dataset
 * to measure rendering performance.
 */
class MapTestViewModel : ViewModel() {

    private val zoomOutLevel = 13f
    private val zoomInLevel = 17.5f
    private val defaultCenter = LatLng(41.0082, 28.9784)

    private val _cameraPosition = MutableStateFlow(
        CameraPosition.fromLatLngZoom(defaultCenter, 11.12f)
    )
    val cameraPosition: StateFlow<CameraPosition> = _cameraPosition.asStateFlow()

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

    /**
     * Initiates the automated geospatial sequence.
     */
    fun startTest(context: Context) {
        stopTest(isFinished = false)

        _isRunning.value = true
        _status.value = "Initializing Geospatial Dataset..."
        _isReportReady.value = false

        val appContext = context.applicationContext

        viewModelScope.launch(Dispatchers.Main) {

            // Generates data on a background thread to keep the main thread responsive.
            val newPoints = withContext(Dispatchers.Default) {
                MapTestDataGenerator.generatePoints(count = 20)
            }

            _points.value = newPoints
            _status.value = "Automated Tour in Progress..."

            performance.startMonitoring(appContext)
            startPinTour()
        }
    }

    /**
     * Executes a systematic multi-stage animation sequence.
     */
    private fun startPinTour() {
        testJob?.cancel()

        testJob = viewModelScope.launch(Dispatchers.Main) {
            delay(1000)

            for ((index, point) in _points.value.withIndex()) {
                if (!isActive || !_isRunning.value) break

                _status.value = "Target ${index + 1} / ${_points.value.size} -> Panning ✈️"

                _cameraPosition.value = CameraPosition.Builder()
                    .target(point.coordinate)
                    .zoom(zoomOutLevel)
                    .build()

                delay(1800)

                if (!isActive || !_isRunning.value) break

                _status.value = "Target ${index + 1} -> Inspecting Detail 🔍"
                _cameraPosition.value = CameraPosition.Builder()
                    .target(point.coordinate)
                    .zoom(zoomInLevel)
                    .build()

                delay(1800)

                if (!isActive || !_isRunning.value) break

                if (index < _points.value.size - 1) {
                    _status.value = "Target ${index + 1} -> Ascending ⬆️"
                    _cameraPosition.value = CameraPosition.Builder()
                        .target(point.coordinate)
                        .zoom(zoomOutLevel)
                        .build()

                    delay(1200)
                }
            }

            if (isActive && _isRunning.value) {
                _status.value = "Tour Finalized! Global Review 🌍"

                _cameraPosition.value = CameraPosition.Builder()
                    .target(defaultCenter)
                    .zoom(11.2f)
                    .build()

                delay(3000)
                stopTest(isFinished = true)
            }
        }
    }

    fun stopTest(isFinished: Boolean = false) {
        testJob?.cancel()
        testJob = null

        if (!_isRunning.value) return

        performance.stopMonitoring()
        _isRunning.value = false
        _status.value = if (isFinished) "Tour Completed ✅" else "Test Terminated 🛑"

        _isReportReady.value = isFinished
    }

    /**
     * Defers CSV generation and export to user intent.
     * Offloads disk I/O to a background thread to prevent UI freezing.
     */
    fun exportResults(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val summary = "Total_Points_Visited,${_points.value.size},,,,"
                val csvFile = ExportManager.generateCSV(
                    context = context,
                    logs = performance.currentLogs.value,
                    testName = "Map_Native_Optimized_Tour",
                    customSummary = summary
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
                    _status.value = "❌ Export Failed: ${e.message}"
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (_isRunning.value) performance.stopMonitoring()
        testJob?.cancel()
    }
}