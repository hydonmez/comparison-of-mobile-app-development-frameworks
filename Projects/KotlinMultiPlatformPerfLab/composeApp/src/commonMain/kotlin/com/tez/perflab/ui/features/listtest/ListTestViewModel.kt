package com.tez.perflab.ui.features.listtest

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tez.perflab.managers.ExportManager
import com.tez.perflab.managers.ListTestManager
import com.tez.perflab.managers.PerformanceManager
import com.tez.perflab.managers.PlatformContext
import com.tez.perflab.managers.loadBenchmarkBytes
import com.tez.perflab.models.Product
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*

/**
 * A cross-platform ViewModel designed to orchestrate automated scrolling benchmarks.
 * Evaluates the hardware rendering performance of Compose Multiplatform's LazyGrid.
 */
class ListTestViewModel : ViewModel() {

    // Uses mutableStateListOf to prevent heavy list reallocations and Garbage Collection (GC) overhead during pagination.
    val products = mutableStateListOf<Product>()

    // MARK: - Reactive UI State

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _status = MutableStateFlow("Ready")
    val status: StateFlow<String> = _status.asStateFlow()

    // Defers the export action until the benchmark concludes.
    private val _isReportReady = MutableStateFlow(false)
    val isReportReady: StateFlow<Boolean> = _isReportReady.asStateFlow()

    // Uses a SharedFlow with DROP_OLDEST to discard stale scroll commands
    // if the UI rendering pipeline is temporarily saturated, ensuring smooth updates.
    private val _scrollCommand = MutableSharedFlow<ScrollCommand>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val scrollCommand = _scrollCommand.asSharedFlow()

    // MARK: - Engine Execution State

    private var testJob: Job? = null
    private val pageSize = 20
    private val targetCount = 300

    fun startTest(context: PlatformContext) {
        stopTest(isFinished = false)

        products.clear()
        _isRunning.value = true
        _status.value = "Preparing Dataset..."
        _isReportReady.value = false

        PerformanceManager.startMonitoring(context)

        // Shifted from Dispatchers.Main to Dispatchers.Default. The heavy automated
        // scrolling loop runs in the background, freeing the Main Thread exclusively
        // for UI rendering and preventing micro-stutters.
        testJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                // Checks if the in-memory cache is populated. If empty, triggers the I/O read.
                if (ListTestManager.fetchPage(1).isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _status.value = "Reading JSON from Disk..."
                    }

                    val jsonBytes = loadBenchmarkBytes("mock_products.json")
                    ListTestManager.loadJSON(jsonBytes)
                }

                // Resets the cursor for a consistent starting point.
                ListTestManager.resetCursor()

                loadMoreData()

                // Simulates realistic network/I/O latency prior to the scroll cascade.
                delay(420)
                runAutoScrollScenario()

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _status.value = "Initialization Failed: ${e.message}"
                    _isRunning.value = false
                    stopTest(isFinished = false)
                }
            }
        }
    }

    private suspend fun loadMoreData() {
        if (_isLoadingMore.value) return
        _isLoadingMore.value = true

        delay(100)

        // Offloads payload fetching to the current background pool (Dispatchers.Default).
        val newItems = ListTestManager.fetchPage(pageSize)

        // Thread-safe mutation on Dispatchers.Main prevents ConcurrentModificationExceptions.
        withContext(Dispatchers.Main) {
            products.addAll(newItems)
        }
        _isLoadingMore.value = false
    }

    private suspend fun runAutoScrollScenario() {
        var index = 0
        while (index < (targetCount - 5)) {
            // Explicitly querying the current coroutine context to check cancellation status.
            if (!currentCoroutineContext().isActive || !_isRunning.value) break

            val currentProductsSize = products.size

            // Step increment for scrolling (+2 steps per tick).
            val next = minOf(index + 2, currentProductsSize - 1)

            if (next > index) {
                index = next
                _status.value = "Scrolling Down... ($index/$currentProductsSize)"
                _scrollCommand.tryEmit(ScrollCommand(targetIndex = index))

                // Scroll delay: 800ms base + 16ms to align with a standard 60Hz display refresh cycle.
                delay(800L + 16L)
            } else {
                delay(500L)
            }

            // Triggers silent pagination when the cursor breaches the bottom 10-item threshold.
            if (!_isLoadingMore.value && currentProductsSize < targetCount && index >= currentProductsSize - 10) {
                loadMoreData()
            }
        }

        // --- Benchmark Finalization & Return Animations ---
        if (currentCoroutineContext().isActive && _isRunning.value) {
            val lastIndex = products.size - 1
            if (lastIndex >= 0) {
                _status.value = "Target Achieved"
                _scrollCommand.tryEmit(ScrollCommand(targetIndex = lastIndex))
                delay(1500L + 16L)
            }
        }

        if (currentCoroutineContext().isActive && _isRunning.value) {
            _status.value = "Returning to Top..."
            _scrollCommand.tryEmit(ScrollCommand(targetIndex = 0))
            delay(2500L + 16L)
            stopTest(isFinished = true)
        }
    }

    fun stopTest(isFinished: Boolean) {
        testJob?.cancel()
        testJob = null
        if (!_isRunning.value) return

        PerformanceManager.stopMonitoring()
        _isRunning.value = false
        _status.value = if (isFinished) "Test Finalized" else "Terminated"

        if (isFinished) {
            _isReportReady.value = true
        }
    }

    /**
     * Triggers the asynchronous generation and sharing of the hardware telemetry CSV.
     */
    fun shareResults(context: PlatformContext) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ExportManager.generateAndShareCSV(
                    logs = PerformanceManager.currentLogs.value,
                    testName = "Ecommerce_Fluent_Test_KMP",
                    context = context
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _status.value = "Export Failed: ${e.message}"
                }
            }
        }
    }
}

/**
 * Encapsulates scrolling instructions.
 * Easing and duration properties are abstracted away,
 * as Compose handles the animation physics internally.
 */
data class ScrollCommand(val targetIndex: Int)