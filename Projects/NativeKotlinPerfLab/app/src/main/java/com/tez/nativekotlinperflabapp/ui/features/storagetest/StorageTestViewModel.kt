package com.tez.nativekotlinperflabapp.ui.features.storagetest

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tez.nativekotlinperflabapp.core.engines.StorageEngine
import com.tez.nativekotlinperflabapp.core.managers.BenchmarkType
import com.tez.nativekotlinperflabapp.core.managers.ExportManager
import com.tez.nativekotlinperflabapp.core.managers.PerformanceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile

/**
 * An orchestrator that manages heavy I/O storage benchmarks with strict thread isolation.
 * Uses a pure ViewModel to prevent Context-related memory leaks.
 */
class StorageTestViewModel : ViewModel() {

    private val performance = PerformanceManager

    // --- Reactive UI State ---

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

    @Volatile
    private var lastTestName = ""

    // --- Core Logic Implementation ---

    fun runBenchmark(context: Context, isWrite: Boolean) {
        if (_isRunning.value) return

        val appContext = context.applicationContext
        _isRunning.value = true
        _isReportReady.value = false
        _progress.value = 0.0
        _status.value = if (isWrite) "Writing 2GB Fixed Payload..." else "Reading 2GB Fixed Payload..."

        // Initiate high-resolution telemetry.
        performance.startMonitoring(appContext, BenchmarkType.MICRO)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (isWrite) {
                    StorageEngine.writeData(context = appContext, megabytes = 2048) { p: Double ->
                        throttleProgressUpdate(p)
                    }
                } else {
                    StorageEngine.readData(context = appContext) { p: Double ->
                        throttleProgressUpdate(p)
                    }
                }

                // Explicitly reading the checksum enforces CPU execution of the byte-level operations,
                // preventing compiler optimizations from bypassing the logic.
                val finalChecksum = StorageEngine.securityChecksum
                Log.i("StorageBenchmark", "Hardware synchronization complete. Deterministic checksum: $finalChecksum")

                withContext(Dispatchers.Main) {
                    finishTest(isWrite)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _status.value = "Error: ${e.localizedMessage}"
                    _isRunning.value = false
                    performance.stopMonitoring()
                }
            }
        }
    }

    /**
     * Throttles state updates to 1% increments. Emitting StateFlow changes for every byte
     * during a 2GB stream would cause severe UI stuttering and frame drops.
     */
    private fun throttleProgressUpdate(p: Double) {
        if (p - _progress.value >= 0.01 || p >= 1.0) {
            _progress.value = p
        }
    }

    private fun finishTest(isWrite: Boolean) {
        performance.stopMonitoring()
        lastTestName = if (isWrite) "native_Storage_Write_2GB" else "native_Storage_Read_2GB"

        _isRunning.value = false
        _status.value = if (isWrite) "Write Completed ✅" else "Read Completed ✅"

        if (isWrite) {
            _isWriteCompleted.value = true
        }

        _isReportReady.value = true
    }

    // --- Telemetry Serialization ---

    fun exportResults(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val csvFile = ExportManager.generateCSV(
                    context = context,
                    logs = performance.currentLogs.value,
                    testName = lastTestName
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
                        context.startActivity(Intent.createChooser(shareIntent, "Export Storage Telemetry"))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _status.value = "Export Failed: ${e.message}"
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (_isRunning.value) performance.stopMonitoring()
    }
}