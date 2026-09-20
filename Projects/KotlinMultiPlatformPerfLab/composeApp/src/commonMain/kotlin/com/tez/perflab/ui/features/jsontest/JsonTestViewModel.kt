package com.tez.perflab.ui.features.jsontest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tez.perflab.managers.ExportManager
import com.tez.perflab.managers.JsonTestManager
import com.tez.perflab.managers.PerformanceManager
import com.tez.perflab.managers.PlatformContext
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/**
 * A cross-platform orchestrator designed to benchmark high-frequency JSON deserialization.
 * Decoupled from the UI layer to ensure maximum CPU allocation to the parser.
 */
class JsonTestViewModel : ViewModel() {

    private val performance = PerformanceManager

    // MARK: - Reactive UI State

    private val _status = MutableStateFlow("Ready")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _parsedCount = MutableStateFlow(0)
    val parsedCount: StateFlow<Int> = _parsedCount.asStateFlow()

    // Defers the export action until the benchmark concludes.
    private val _isReportReady = MutableStateFlow(false)
    val isReportReady: StateFlow<Boolean> = _isReportReady.asStateFlow()

    // Consistent loop constraint across platforms to ensure an equal computational load.
    private val iterations = 200

    fun startBenchmark(context: PlatformContext) {
        if (_isRunning.value) return

        _isRunning.value = true
        _status.value = "Loading 10MB Payload into RAM..."
        _parsedCount.value = 0
        _isReportReady.value = false

        // Initiates high-frequency hardware telemetry (CPU/RAM polling)
        performance.startMonitoring(context)

        // Offload heavy computational parsing to the Dispatchers.Default (CPU-optimized thread pool)
        viewModelScope.launch(Dispatchers.Default) {
            try {
                // 1. DISK I/O ISOLATION: Preloads string data into memory to prevent I/O latency
                // from affecting the CPU benchmark.
                JsonTestManager.preloadDataOnce()

                // 2. JIT / ART WARM-UP
                // Forces the JVM/ART engine to compile the parsing bytecode into native machine code
                // before the monotonic clock starts, ensuring a fair comparison.
                JsonTestManager.runParseTest()

                _status.value = "Parsing Data Structure..."

                // 3. EXECUTION
                // Utilizing Kotlin's Monotonic TimeSource guarantees immunity against OS time syncs.
                val timeMark = TimeSource.Monotonic.markNow()
                var localTotalItems = 0

                // High-frequency synchronous parsing loop
                for (i in 1..iterations) {
                    val count = JsonTestManager.runParseTest()
                    localTotalItems += count

                    // 4. UI THROTTLING
                    // Mutating StateFlow is thread-safe. Emitting state updates only every 10th
                    // iteration prevents excessive UI updates without consuming CPU cycles.
                    if (i % 10 == 0) {
                        val progress = ((i.toDouble() / iterations.toDouble()) * 100).toInt()
                        _status.value = "Processing: $progress%"
                    }
                }

                // Calculate execution duration in absolute seconds
                val durationSecs = timeMark.elapsedNow().inWholeMilliseconds / 1000.0

                // 5. FINALIZATION
                _parsedCount.value = localTotalItems

                // Allocation-free string templating (Bypasses heavy JVM String.format)
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

    /**
     * Triggers the asynchronous generation and sharing of the hardware telemetry CSV.
     */
    fun shareResults(context: PlatformContext) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                ExportManager.generateAndShareCSV(
                    logs = performance.currentLogs.value,
                    testName = "JSON_Benchmark_KMP",
                    customSummary = "Total_Parsed_Items,${_parsedCount.value},,,,",
                    context = context
                )
            } catch (e: Exception) {
                _status.value = "Export Failed: ${e.message}"
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCleared() {
        super.onCleared()
        if (_isRunning.value) {
            performance.stopMonitoring()
        }

        // MEMORY CLEANUP
        // Since releaseMemory suspends, and viewModelScope is immediately cancelled upon teardown,
        // memory deallocation is dispatched to an independent scope to guarantee RAM is freed.
        GlobalScope.launch(Dispatchers.Default) {
            JsonTestManager.releaseMemory()
        }
    }
}