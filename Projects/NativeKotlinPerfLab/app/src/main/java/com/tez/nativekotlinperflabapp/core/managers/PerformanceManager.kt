package com.tez.nativekotlinperflabapp.core.managers

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.view.Choreographer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.tez.nativekotlinperflabapp.models.PerformanceLog
import kotlin.math.max
import kotlin.math.round

// Defines the type of test being executed.
enum class BenchmarkType {
    MACRO, // Long-duration tests (1.0s interval, initial 2.0s warm-up discarded)
    MICRO  // Short-duration tests (0.25s interval, no warm-up filter)
}

/**
 * A singleton telemetry engine responsible for high-frequency hardware metric acquisition.
 */
object PerformanceManager {

    private val supervisorJob = SupervisorJob()

    // Uses the IO dispatcher to offload heavy operations from the main UI thread.
    private val workerScope = CoroutineScope(Dispatchers.IO + supervisorJob)
    private val mainScope = CoroutineScope(Dispatchers.Main + supervisorJob)

    private val _isMeasuring = MutableStateFlow(false)

    private val _currentLogs = MutableStateFlow<List<PerformanceLog>>(emptyList())
    val currentLogs: StateFlow<List<PerformanceLog>> = _currentLogs.asStateFlow()

    private val _currentFPS = MutableStateFlow(0)
    val currentFPS: StateFlow<Int> = _currentFPS.asStateFlow()

    private val _currentThermalState = MutableStateFlow("Nominal")
    val currentThermalState: StateFlow<String> = _currentThermalState.asStateFlow()

    private var baselineRAM: Double = 0.0

    private val logsBuffer = ArrayList<PerformanceLog>(2000)

    private var windowStartTimeNanos: Long = 0L
    private var testStartTimeNanos: Long = 0L // Tracks the total elapsed time
    private var frameCount: Int = 0

    // Stores the current test type
    private var currentTestType = BenchmarkType.MACRO

    @Volatile
    private var previousCpuTimeMs: Long = 0L

    private var appContext: Context? = null
    private var batteryManagerCache: BatteryManager? = null
    private var powerManagerCache: PowerManager? = null

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!_isMeasuring.value) return

            if (windowStartTimeNanos == 0L) {
                windowStartTimeNanos = frameTimeNanos
                testStartTimeNanos = frameTimeNanos
                Choreographer.getInstance().postFrameCallback(this)
                return
            }

            frameCount++

            val deltaNanos = frameTimeNanos - windowStartTimeNanos
            val deltaSeconds = deltaNanos / 1_000_000_000.0

            val totalElapsedNanos = frameTimeNanos - testStartTimeNanos
            val totalElapsedSeconds = totalElapsedNanos / 1_000_000_000.0

            // Dynamically determines thresholds based on the test type.
            val targetInterval = if (currentTestType == BenchmarkType.MICRO) 0.25 else 1.0
            val warmupThreshold = if (currentTestType == BenchmarkType.MICRO) 0.0 else 2.0

            if (deltaSeconds >= targetInterval) {
                val snapshotFPS = (frameCount / deltaSeconds).toInt()
                _currentFPS.value = snapshotFPS

                frameCount = 0
                windowStartTimeNanos = frameTimeNanos

                // Warm-up filter to ensure stable readings before capturing metrics.
                if (totalElapsedSeconds >= warmupThreshold) {
                    workerScope.launch {
                        captureMetricsInBackground(snapshotFPS = snapshotFPS, deltaSeconds = deltaSeconds)
                    }
                } else {
                    // Keeps the CPU reference updated during the warm-up phase.
                    previousCpuTimeMs = android.os.Process.getElapsedCpuTime()
                }
            }

            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun startMonitoring(context: Context, type: BenchmarkType = BenchmarkType.MACRO) {
        stopMonitoring()

        appContext = context.applicationContext
        _isMeasuring.value = true
        currentTestType = type

        _currentLogs.value = emptyList()
        _currentFPS.value = 0
        _currentThermalState.value = "Nominal"

        batteryManagerCache = appContext?.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        powerManagerCache = appContext?.getSystemService(Context.POWER_SERVICE) as? PowerManager

        workerScope.launch {
            logsBuffer.clear()
            baselineRAM = getOptimizedRAMUsage()
            previousCpuTimeMs = android.os.Process.getElapsedCpuTime()

            mainScope.launch {
                windowStartTimeNanos = 0L
                testStartTimeNanos = 0L
                frameCount = 0
                Choreographer.getInstance().postFrameCallback(frameCallback)
            }
        }
    }

    fun stopMonitoring() {
        _isMeasuring.value = false
        windowStartTimeNanos = 0L
        testStartTimeNanos = 0L
        frameCount = 0
        appContext = null
        batteryManagerCache = null
        powerManagerCache = null

        _currentLogs.value = logsBuffer.toList()

        mainScope.launch {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        }
    }

    private fun captureMetricsInBackground(snapshotFPS: Int, deltaSeconds: Double) {
        val rawRAM = getOptimizedRAMUsage()
        val netRAM = max(0.0, rawRAM - baselineRAM)

        val cpuUsage = getProcessCpuUsage(deltaSeconds)
        val battery = getBatteryLevel()
        val thermal = getThermalStateString()

        val log = PerformanceLog(
            timestampMillis = System.currentTimeMillis(),
            cpuUsage = cpuUsage,
            rawRAM = rawRAM,
            netRAM = netRAM,
            batteryLevel = battery,
            fps = snapshotFPS,
            thermalState = thermal
        )

        // Synchronized block to ensure data integrity during insertion.
        synchronized(logsBuffer) {
            logsBuffer.add(log)
        }
        _currentThermalState.value = thermal
    }

    // Calculates RAM usage in the background to prevent UI stuttering.
    private fun getOptimizedRAMUsage(): Double {
        return try {
            val memoryInfo = android.os.Debug.MemoryInfo()
            android.os.Debug.getMemoryInfo(memoryInfo)
            memoryInfo.totalPss / 1024.0
        } catch (e: Exception) {
            0.0
        }
    }

    private fun getProcessCpuUsage(deltaSeconds: Double): Double {
        return try {
            val currentCpuMs = android.os.Process.getElapsedCpuTime()
            val cpuDeltaMs = currentCpuMs - previousCpuTimeMs
            previousCpuTimeMs = currentCpuMs

            if (deltaSeconds > 0) {
                val uptimeDeltaMs = deltaSeconds * 1000.0
                val cpuPercentage = (cpuDeltaMs.toDouble() / uptimeDeltaMs) * 100.0
                round(cpuPercentage * 10.0) / 10.0
            } else 0.0
        } catch (e: Exception) { 0.0 }
    }

    private fun getBatteryLevel(): Double {
        val level = batteryManagerCache?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        return if (level >= 0) level.toDouble() else 0.0
    }

    private fun getThermalStateString(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (powerManagerCache?.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "Nominal"
                PowerManager.THERMAL_STATUS_LIGHT -> "Fair"
                PowerManager.THERMAL_STATUS_MODERATE, PowerManager.THERMAL_STATUS_SEVERE -> "Serious"
                PowerManager.THERMAL_STATUS_CRITICAL, PowerManager.THERMAL_STATUS_EMERGENCY -> "Critical"
                else -> "Nominal"
            }
        } else "N/A"
    }
}