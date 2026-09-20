package com.tez.perflab.managers

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.concurrent.Volatile

/**
 * A thread-safe diagnostic orchestrator designed to measure application launch latency.
 * Isolates OS-level bootstrapping overhead from software-level UI rendering pipelines.
 *
 * All mutable timestamp fields are declared [@Volatile] to guarantee cross-thread 
 * visibility on the JVM. This prevents CPU register caching and ensures that 
 * [reportBenchmark] observes the exact high-precision timestamps written during 
 * the initial bootstrapping phase.
 */
object LaunchPerformanceManager {

    private val _totalColdStartMs = MutableStateFlow(0.0)
    val totalColdStartMs: StateFlow<Double> = _totalColdStartMs.asStateFlow()

    private val _osDurationMs = MutableStateFlow(0.0)
    val osDurationMs: StateFlow<Double> = _osDurationMs.asStateFlow()

    private val _softwareDurationMs = MutableStateFlow(0.0)
    val softwareDurationMs: StateFlow<Double> = _softwareDurationMs.asStateFlow()

    private val _hotStartMs = MutableStateFlow(0.0)
    val hotStartMs: StateFlow<Double> = _hotStartMs.asStateFlow()

    // Internal Timestamps (Hardware Precision)
    // Volatile ensures that these values are read directly from RAM, not CPU cache.
    @Volatile private var startTimeNanos: Long? = null
    @Volatile private var osReadyTimeNanos: Long? = null
    @Volatile private var hotStartWakeTimeNanos: Long? = null

    @Volatile
    private var isColdStartReported = false

    // MARK: - Cold Start Tracking

    /**
     * Captured at the earliest possible entry point (e.g., Application.onCreate or Static Init).
     */
    fun appStarted() {
        startTimeNanos = getSystemUptimeNanos()
    }

    /**
     * Captured when the OS-level bootstrapping (Activity/Scene setup) is finalized.
     */
    fun osReady() {
        osReadyTimeNanos = getSystemUptimeNanos()
    }

    /**
     * Computes the final launch telemetry. Invoked after the first frame is rendered.
     */
    fun reportBenchmark() {
        if (isColdStartReported) return
        val start = startTimeNanos ?: return
        val osReady = osReadyTimeNanos ?: return

        val now = getSystemUptimeNanos()

        // Granular Latency Computation
        _osDurationMs.value = (osReady - start) / 1_000_000.0
        _softwareDurationMs.value = (now - osReady) / 1_000_000.0
        _totalColdStartMs.value = (now - start) / 1_000_000.0

        isColdStartReported = true

        // Purge timestamps to prevent stale calculations in future test cycles.
        startTimeNanos = null
        osReadyTimeNanos = null
    }

    // MARK: - Hot Start Tracking

    fun appIsWakingUp() {
        hotStartWakeTimeNanos = getSystemUptimeNanos()
    }

    fun hotStartDetected() {
        // Hot start is only valid if a cold start has already been established.
        if (!isColdStartReported) {
            hotStartWakeTimeNanos = null
            return
        }

        val wakeTime = hotStartWakeTimeNanos ?: return
        val now = getSystemUptimeNanos()

        _hotStartMs.value = (now - wakeTime) / 1_000_000.0
        hotStartWakeTimeNanos = null
    }
}