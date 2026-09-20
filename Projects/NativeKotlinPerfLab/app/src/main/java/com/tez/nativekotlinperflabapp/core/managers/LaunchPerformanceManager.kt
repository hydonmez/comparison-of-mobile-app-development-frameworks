package com.tez.nativekotlinperflabapp.core.managers

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A thread-safe tracker for measuring application launch latency.
 * Tracks Cold Start and Hot Start performance, separating OS-level startup time
 * from UI rendering time.
 *
 * Uses [@Volatile] to ensure state changes are immediately visible across different threads.
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

    @Volatile private var startTimeNanos: Long? = null
    @Volatile private var osReadyTimeNanos: Long? = null
    @Volatile private var hotStartWakeTimeNanos: Long? = null

    @Volatile
    private var isColdStartReported = false

    fun appStarted() {
        startTimeNanos = SystemClock.elapsedRealtimeNanos()
    }

    fun osReady() {
        osReadyTimeNanos = SystemClock.elapsedRealtimeNanos()
    }

    fun reportBenchmark() {
        if (isColdStartReported) return
        val start = startTimeNanos ?: return
        val osReady = osReadyTimeNanos ?: return

        val now = SystemClock.elapsedRealtimeNanos()

        _osDurationMs.value = (osReady - start) / 1_000_000.0
        _softwareDurationMs.value = (now - osReady) / 1_000_000.0
        _totalColdStartMs.value = (now - start) / 1_000_000.0

        isColdStartReported = true

        startTimeNanos = null
        osReadyTimeNanos = null
    }

    fun appIsWakingUp() {
        hotStartWakeTimeNanos = SystemClock.elapsedRealtimeNanos()
    }

    fun hotStartDetected() {
        if (!isColdStartReported) {
            hotStartWakeTimeNanos = null
            return
        }

        val wakeTime = hotStartWakeTimeNanos ?: return
        val now = SystemClock.elapsedRealtimeNanos()

        _hotStartMs.value = (now - wakeTime) / 1_000_000.0
        hotStartWakeTimeNanos = null
    }
}