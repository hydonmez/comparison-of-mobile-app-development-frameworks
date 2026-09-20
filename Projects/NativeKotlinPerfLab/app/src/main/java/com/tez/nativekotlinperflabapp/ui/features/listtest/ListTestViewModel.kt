package com.tez.nativekotlinperflabapp.ui.features.listtest

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tez.nativekotlinperflabapp.core.managers.ExportManager
import com.tez.nativekotlinperflabapp.core.managers.ListTestManager
import com.tez.nativekotlinperflabapp.core.managers.PerformanceManager
import com.tez.nativekotlinperflabapp.models.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

data class ScrollCommand(
    val targetIndex: Int,
    val durationMs: Int,
    val easing: Easing
)

// Standard easing curves for automated scrolling animations.
val SwiftEaseOut = CubicBezierEasing(0.0f, 0.0f, 0.58f, 1.0f)
val SwiftEaseInOut = CubicBezierEasing(0.42f, 0.0f, 0.58f, 1.0f)

/**
 * An orchestrator that manages automated scrolling scenarios and pagination logic.
 */
class ListTestViewModel(application: Application) : AndroidViewModel(application) {

    // In-place UI state mutation prevents heavy list reallocations.
    val products = mutableStateListOf<Product>()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _status = MutableStateFlow("Ready")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _exportUri = MutableStateFlow<Uri?>(null)
    val exportUri: StateFlow<Uri?> = _exportUri.asStateFlow()

    // Enforces a drop-oldest policy to prevent memory saturation if the UI pipeline
    // falls behind the command emission rate.
    private val _scrollCommand = MutableSharedFlow<ScrollCommand>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val scrollCommand = _scrollCommand.asSharedFlow()

    private val performance = PerformanceManager
    private var testJob: Job? = null

    private val pageSize = 20
    private val targetCount = 300

    fun startTest() {
        stopTest(isFinished = false)

        val context = getApplication<Application>()
        ListTestManager.init(context)

        products.clear()
        _isRunning.value = true
        _status.value = "Preparing Dataset..."
        _exportUri.value = null

        performance.startMonitoring(context)

        // The orchestration loop runs entirely on a background thread pool,
        // keeping the Main Thread strictly dedicated to UI rendering.
        testJob = viewModelScope.launch(Dispatchers.Default) {
            ListTestManager.resetCursor()

            loadMoreDataSuspend()
            delay(420)
            runAutoScrollScenario()
        }
    }

    private suspend fun loadMoreDataSuspend() {
        if (_isLoadingMore.value) return
        _isLoadingMore.value = true

        // Simulate latency for realism
        delay(100)

        val newItems = ListTestManager.fetchPage(pageSize)

        if (newItems.isEmpty()) {
            withContext(Dispatchers.Main) {
                _status.value = "ERROR: Payload Empty"
                _isRunning.value = false
                _isLoadingMore.value = false
            }
            return
        }

        // Synchronize state mutation back to the Main Thread safely.
        withContext(Dispatchers.Main) {
            products.addAll(newItems)
        }
        _isLoadingMore.value = false
    }

    private suspend fun runAutoScrollScenario() {
        var index = 0

        while (index < (targetCount - 5)) {
            if (!viewModelScope.isActive || !_isRunning.value) break

            val currentCount = products.size
            val next = min(index + 2, currentCount - 1)

            if (next > index) {
                index = next
                _status.value = "Scrolling Down... ($index/${currentCount})"
                _scrollCommand.tryEmit(ScrollCommand(index, 800, LinearEasing))

                // Travel duration + hardware buffer delay
                delay(800L + 16L)
            } else {
                delay(500L)
            }

            if (!_isLoadingMore.value && currentCount < targetCount && index >= currentCount - 10) {
                loadMoreDataSuspend()
            }
        }

        if (!viewModelScope.isActive || !_isRunning.value) return

        if (products.isNotEmpty()) {
            _status.value = "Target Achieved"
            _scrollCommand.tryEmit(ScrollCommand(products.size - 1, 1500, SwiftEaseOut))
            delay(1500L + 16L)
        }

        if (products.isNotEmpty() && viewModelScope.isActive && _isRunning.value) {
            _status.value = "Returning to Top..."
            _scrollCommand.tryEmit(ScrollCommand(0, 2500, SwiftEaseInOut))
            delay(2500L + 16L)
        }

        stopTest(isFinished = true)
    }

    fun stopTest(isFinished: Boolean) {
        testJob?.cancel()
        testJob = null

        if (!_isRunning.value) return

        performance.stopMonitoring()
        _isRunning.value = false
        _status.value = if (isFinished) "Test Finalized" else "Terminated"

        if (isFinished) {
            val context = getApplication<Application>()
            viewModelScope.launch(Dispatchers.IO) {
                val csvFile = ExportManager.generateCSV(
                    context = context,
                    logs = performance.currentLogs.value,
                    testName = "Ecommerce_Native_Test"
                )

                csvFile?.let { file ->
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file
                    )
                    _exportUri.value = uri
                }
            }
        } else {
            _exportUri.value = null
        }
    }

    fun exportResults(context: Context) {
        exportUri.value?.let { uri ->
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Export Results"))
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (_isRunning.value) performance.stopMonitoring()
        testJob?.cancel()
    }
}