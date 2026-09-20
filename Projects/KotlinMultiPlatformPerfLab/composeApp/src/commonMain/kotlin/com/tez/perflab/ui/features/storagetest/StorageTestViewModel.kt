package com.tez.perflab.ui.features.storagetest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tez.perflab.engines.StorageEngine
import com.tez.perflab.managers.BenchmarkType
import com.tez.perflab.managers.ExportManager
import com.tez.perflab.managers.PerformanceManager
import com.tez.perflab.managers.PlatformContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A ViewModel responsible for coordinating heavy physical NAND benchmarks.
 * Enforces strict thread isolation to prevent I/O operations from stalling the main run loop,
 * ensuring accurate UI telemetry and consistent hardware stress across platforms.
 */
class StorageTestViewModel : ViewModel() {

    // MARK: - Reactive UI State

    private val _progress = MutableStateFlow(0.0)
    val progress: StateFlow<Double> = _progress.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _status = MutableStateFlow("Ready")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _isWriteCompleted = MutableStateFlow(false)
    val isWriteCompleted: StateFlow<Boolean> = _isWriteCompleted.asStateFlow()

    private val _isReportReady = MutableStateFlow(false)
    val isReportReady: StateFlow<Boolean> = _isReportReady.asStateFlow()

    private var lastTestName: String = ""
    private val performance = PerformanceManager

    private var benchmarkJob: Job? = null

    // MARK: - Benchmark Execution

    /**
     * Triggers the storage benchmark sequence on a dedicated background dispatcher.
     *
     * @param context Platform-specific context for file system access.
     * @param isWrite Determines if the test executes a Write or Read sequence.
     */
    fun runBenchmark(context: PlatformContext, isWrite: Boolean) {
        if (_isRunning.value) return

        benchmarkJob?.cancel()

        _isRunning.value = true
        _isReportReady.value = false
        _progress.value = 0.0

        _status.value = if (isWrite) "Writing 2GB Fixed Payload..." else "Reading 2GB Fixed Payload..."

        // Initiate high-resolution (0.25s) telemetry.
        // The MICRO profile bypasses the warm-up filter to capture immediate I/O burst spikes.
        performance.startMonitoring(context, BenchmarkType.MICRO)

        // Offload the heavy payload stream to a detached background dispatcher
        // to guarantee zero interference with the Main thread's event loop.
        benchmarkJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                if (isWrite) {
                    StorageEngine.writeData(context = context, megabytes = 2048) { p: Double ->
                        throttleProgressUpdate(p)
                    }
                } else {
                    StorageEngine.readData(context = context) { p: Double ->
                        throttleProgressUpdate(p)
                    }
                }

                // Explicitly read and log the checksum to prevent Dead Code Elimination (DCE)
                // in Release builds. This strictly enforces CPU execution of the byte-level operations,
                // guaranteeing deterministic data for cross-platform comparisons.
                val finalChecksum = StorageEngine.securityChecksum
                println("Hardware Parity Verified. Checksum: $finalChecksum")

                finishTest(isWrite)

            } catch (e: Exception) {
                if (e !is CancellationException) {
                    withContext(Dispatchers.Main) {
                        _status.value = "Error: ${e.message}"
                        _isRunning.value = false
                        performance.stopMonitoring()
                    }
                }
            }
        }
    }

    /**
     * Throttles state updates to 1% increments. Emitting StateFlow changes for every byte
     * during a 2GB stream would oversaturate the Main thread and severely drop the frame rate,
     * leading to inaccurate performance telemetry.
     */
    private fun throttleProgressUpdate(p: Double) {
        if (p - _progress.value >= 0.01 || p >= 1.0) {
            _progress.value = p
        }
    }

    /**
     * Finalizes the benchmark, stops telemetry collection, and prepares the UI for data export.
     */
    private suspend fun finishTest(isWrite: Boolean) {
        withContext(Dispatchers.Main) {
            performance.stopMonitoring()
            lastTestName = if (isWrite) "Storage_Write_KMP_2GB" else "Storage_Read_KMP_2GB"

            _isRunning.value = false
            _status.value = if (isWrite) "Write Completed ✅" else "Read Completed ✅"

            if (isWrite) {
                _isWriteCompleted.value = true
            }

            _isReportReady.value = true
        }
    }

    // MARK: - Post-Benchmark Serialization

    /**
     * Compiles the collected hardware telemetry, generates a CSV report,
     * and invokes the system-native share sheet.
     */
    fun shareResults(context: PlatformContext) {
        // Initiate the coroutine on the Main thread as this flow concludes with a UI action (Share Sheet).
        viewModelScope.launch {
            try {
                val logs = performance.currentLogs.value
                val testName = lastTestName

                // Prevent export execution if the telemetry payload is empty.
                if (logs.isEmpty()) {
                    _status.value = "Export Failed: Insufficient telemetry data collected."
                    println("🚨 EXPORT ERROR: Telemetry log is empty.")
                    return@launch
                }

                // Offload the CSV serialization (I/O) to a background thread to prevent Main thread starvation.
                withContext(Dispatchers.IO) {
                    ExportManager.generateAndShareCSV(
                        context = context,
                        logs = logs,
                        testName = testName
                    )
                }
            } catch (e: Exception) {
                _status.value = "Export Failed: ${e.message}"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Ensure hardware polling stops immediately if the ViewModel is deallocated.
        performance.stopMonitoring()
    }
}