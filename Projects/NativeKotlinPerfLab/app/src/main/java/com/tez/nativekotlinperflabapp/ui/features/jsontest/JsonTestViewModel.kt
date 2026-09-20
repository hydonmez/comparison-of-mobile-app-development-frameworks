package com.tez.nativekotlinperflabapp.ui.features.jsontest

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tez.nativekotlinperflabapp.core.managers.ExportManager
import com.tez.nativekotlinperflabapp.core.managers.JsonTestManager
import com.tez.nativekotlinperflabapp.core.managers.PerformanceManager
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A lifecycle-aware view model designed to handle high-frequency JSON deserialization.
 * Uses background threads (Dispatchers.Default) to prevent blocking the Main Thread
 * during intensive parsing loops.
 */
class JsonTestViewModel : ViewModel() {

    private val performance = PerformanceManager

    // --- State Observables ---

    private val _status = MutableStateFlow("Ready")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _isReportReady = MutableStateFlow(false)
    val isReportReady: StateFlow<Boolean> = _isReportReady.asStateFlow()

    private val _parsedCount = MutableStateFlow(0)
    val parsedCount: StateFlow<Int> = _parsedCount.asStateFlow()

    private val iterations = 200

    // --- Execution Pipeline ---

    fun startBenchmark(context: Context) {
        if (_isRunning.value) return

        _isRunning.value = true
        _status.value = "Loading 10MB Payload into RAM..."
        _parsedCount.value = 0
        _isReportReady.value = false

        val appContext = context.applicationContext
        performance.startMonitoring(appContext)

        viewModelScope.launch(Dispatchers.Default) {
            try {
                // 1. Isolate disk I/O operations.
                JsonTestManager.preloadDataOnce(appContext)

                // 2. Warm up the runtime environment.
                JsonTestManager.runParseTest()

                _status.value = "Parsing Data Structure..."

                // 3. Execute parsing iterations.
                val startTime = SystemClock.elapsedRealtime()
                var localTotalItems = 0

                for (i in 1..iterations) {
                    val count = JsonTestManager.runParseTest()
                    localTotalItems += count

                    // 4. Throttle UI updates to maintain performance.
                    if (i % 10 == 0) {
                        val progress = ((i.toDouble() / iterations.toDouble()) * 100).toInt()
                        _status.value = "Processing: $progress%"
                    }
                }

                val durationSecs = (SystemClock.elapsedRealtime() - startTime) / 1000.0

                // 5. Finalize results.
                _parsedCount.value = localTotalItems

                // Formats the duration string efficiently.
                val formattedTime = ((durationSecs * 1000.0).toInt()) / 1000.0
                _status.value = "✅ Completed in $formattedTime sec"

                finishTest()

            } catch (e: Exception) {
                _status.value = "Error: ${e.message}"
                finishTest()
            }
        }
    }

    private fun finishTest() {
        performance.stopMonitoring()
        _isRunning.value = false
        _isReportReady.value = true
    }

    // --- Telemetry Export ---

    fun exportResults(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val csvFile = ExportManager.generateCSV(
                    context = context,
                    logs = performance.currentLogs.value,
                    testName = "JSON_Benchmark_Native_RamCached",
                    customSummary = "Total_Parsed_Items,${_parsedCount.value},,,,"
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
                    _status.value = "❌ Export Failed"
                }
            }
        }
    }

    // --- Lifecycle Management ---

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCleared() {
        super.onCleared()
        if (_isRunning.value) {
            performance.stopMonitoring()
        }

        // Clears the cached payload in a global scope to ensure memory is released
        // even if the ViewModel is cleared.
        GlobalScope.launch(Dispatchers.Default) {
            JsonTestManager.releaseMemory()
        }
    }
}