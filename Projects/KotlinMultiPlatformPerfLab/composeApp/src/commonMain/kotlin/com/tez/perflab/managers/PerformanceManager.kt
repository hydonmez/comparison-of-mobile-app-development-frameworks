package com.tez.perflab.managers

import com.tez.perflab.models.PerformanceLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

/**
 * Defines the telemetry resolution and initialization heuristics.
 * MACRO: 1.0s intervals with a 2.0s warm-up filter for prolonged algorithmic tasks.
 * MICRO: 0.25s intervals with no warm-up filter for volatile I/O bound tasks.
 */
enum class BenchmarkType {
    MACRO,
    MICRO
}

object PerformanceManager {

    private val supervisorJob = SupervisorJob()

    // Enforces sequential execution on a background thread pool,
    // guaranteeing thread safety while preventing UI thread starvation.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val workerScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1) + supervisorJob)

    private val _isMeasuring = MutableStateFlow(false)
    val isMeasuring: StateFlow<Boolean> = _isMeasuring.asStateFlow()

    private val _currentLogs = MutableStateFlow<List<PerformanceLog>>(emptyList())
    val currentLogs: StateFlow<List<PerformanceLog>> = _currentLogs.asStateFlow()

    private val _currentFPS = MutableStateFlow(0)
    val currentFPS: StateFlow<Int> = _currentFPS.asStateFlow()

    private val _currentThermalState = MutableStateFlow("Nominal")
    val currentThermalState: StateFlow<String> = _currentThermalState.asStateFlow()

    private var baselineRAM: Double = 0.0
    private val logsBuffer = ArrayList<PerformanceLog>(2000)

    fun startMonitoring(context: PlatformContext, type: BenchmarkType = BenchmarkType.MACRO) {
        stopMonitoring()

        _isMeasuring.value = true
        _currentLogs.value = emptyList()
        _currentFPS.value = 0
        _currentThermalState.value = "Nominal"

        workerScope.launch {
            logsBuffer.clear()
            baselineRAM = HardwareProfiler.getRamUsage()
            HardwareProfiler.primeCpu()

            withContext(Dispatchers.Main) {
                // The onTick callback runs on the Main Thread since it is triggered by OS frame loops.
                HardwareProfiler.startFpsTracker(context, type) { fps, deltaSec, totalElapsedSec ->
                    if (!_isMeasuring.value) return@startFpsTracker

                    _currentFPS.value = fps

                    // Dynamic thresholding based on the benchmark profile
                    val warmupThreshold = if (type == BenchmarkType.MICRO) 0.0 else 2.0

                    if (totalElapsedSec >= warmupThreshold) {

                        // Safely read battery level on the Main Thread, as it may depend on UIKit on iOS.
                        val currentBatteryLevel = HardwareProfiler.getBatteryLevel()
                        val currentThermalState = HardwareProfiler.getThermalState()

                        workerScope.launch {
                            // Dispatch heavy kernel reads (RAM, CPU) and list insertions 
                            // to the background thread to avoid frame drops.
                            captureMetrics(
                                snapshotFPS = fps,
                                deltaSeconds = deltaSec,
                                totalElapsedSeconds = totalElapsedSec,
                                batteryLevel = currentBatteryLevel,
                                thermalState = currentThermalState
                            )
                        }
                    } else {
                        // Continuously prime the CPU temporal baseline during the warm-up phase
                        HardwareProfiler.primeCpu()
                    }
                }
            }
        }
    }

    fun stopMonitoring() {
        _isMeasuring.value = false
        HardwareProfiler.stopFpsTracker()
        _currentLogs.value = logsBuffer.toList()
    }

    private fun captureMetrics(
        snapshotFPS: Int,
        deltaSeconds: Double,
        totalElapsedSeconds: Double,
        batteryLevel: Double,
        thermalState: String
    ) {
        // This method executes within workerScope on a background thread.
        val rawRAM = HardwareProfiler.getRamUsage()
        val netRAM = max(0.0, rawRAM - baselineRAM)
        val cpuUsage = HardwareProfiler.getCpuUsage(deltaSeconds)

        // Calculate the relative millisecond value for cross-platform safety.
        val timestampMs = (totalElapsedSeconds * 1000).toLong()

        val log = PerformanceLog(
            timestampMillis = timestampMs,
            cpuUsage = cpuUsage,
            rawRAM = rawRAM,
            netRAM = netRAM,
            batteryLevel = batteryLevel,
            fps = snapshotFPS,
            thermalState = thermalState
        )

        logsBuffer.add(log)
        _currentThermalState.value = thermalState
    }
}