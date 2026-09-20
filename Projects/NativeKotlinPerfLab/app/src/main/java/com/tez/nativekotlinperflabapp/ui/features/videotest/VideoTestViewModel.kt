package com.tez.nativekotlinperflabapp.ui.features.videotest

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tez.nativekotlinperflabapp.core.managers.ExportManager
import com.tez.nativekotlinperflabapp.core.managers.PerformanceManager
import com.tez.nativekotlinperflabapp.core.engines.VideoEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A centralized controller managing the lifecycle of high-frequency video decoding benchmarks.
 * Designed to strictly isolate Context references, preventing OS-level memory leaks.
 */
class VideoTestViewModel : ViewModel() {

    val engine = VideoEngine
    private val perfManager = PerformanceManager
    private val exportManager = ExportManager

    // Primitive flag for zero-overhead benchmark state tracking.
    private var isTestingActive = false

    // --- REACTIVE UI STATE ---

    private val _isFullScreen = MutableStateFlow(false)
    val isFullScreen: StateFlow<Boolean> = _isFullScreen.asStateFlow()

    private val _exportUri = MutableStateFlow<Uri?>(null)
    val exportUri: StateFlow<Uri?> = _exportUri.asStateFlow()

    init {
        setupBindings()
    }

    private fun setupBindings() {
        viewModelScope.launch {
            engine.hasEnded.collect { ended ->
                if (ended && isTestingActive) {
                    println("[Benchmark] Video EOF Detected -> Awaiting manual finalization or handled via UI layer.")
                    isTestingActive = false
                    _isFullScreen.value = false
                    engine.stop()
                    perfManager.stopMonitoring()
                }
            }
        }
    }

    /**
     * Prepares the media asset for hardware decoding.
     */
    fun prepareVideo(context: Context) {
        _exportUri.value = null

        val fileName = "test_video_1080p"
        val extension = "mp4"

        engine.prepareVideo(context, fileName, extension)
    }

    fun startTest(context: Context) {
        if (isTestingActive) return

        if (engine.player == null) {
            prepareVideo(context)
        }

        _exportUri.value = null
        _isFullScreen.value = true
        isTestingActive = true

        val appContext = context.applicationContext
        perfManager.startMonitoring(appContext)
        engine.playFromStart()
    }

    /**
     * Terminates hardware monitors and serializes the telemetry log buffer to disk.
     */
    fun stopTest(context: Context, isFinished: Boolean = false) {
        if (!isTestingActive && !isFinished) return

        isTestingActive = false
        _isFullScreen.value = false

        engine.stop()
        perfManager.stopMonitoring()

        val appContext = context.applicationContext

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val exportedFile = exportManager.generateCSV(
                    context = appContext,
                    logs = perfManager.currentLogs.value,
                    testName = if (isFinished) "Video_Native_Complete" else "Video_Native_Partial"
                )

                exportedFile?.let { file ->
                    val uri = FileProvider.getUriForFile(
                        appContext,
                        "${appContext.packageName}.provider",
                        file
                    )
                    withContext(Dispatchers.Main) {
                        _exportUri.value = uri
                    }
                }
            } catch (e: Exception) {
                println("Export Failed: ${e.message}")
            }
        }
    }

    fun shareResults(context: Context) {
        val uri = _exportUri.value ?: return
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Export Benchmark Data"))
    }

    override fun onCleared() {
        super.onCleared()
        engine.release()
    }
}