package com.tez.perflab.managers

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Choreographer
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import kotlin.math.round

/**
 * Android implementation of the hardware profiler.
 * Extracts absolute CPU throughput, RAM (PSS), Battery capacity, and Thermal states
 * directly from the Android OS.
 */
actual object HardwareProfiler {

    private var windowStartTimeNanos: Long = 0L
    private var testStartTimeNanos: Long = 0L
    private var frameCount = 0
    private var targetInterval = 1.0
    private var tickCallback: ((Int, Double, Double) -> Unit)? = null

    @Volatile private var previousCpuTimeMs: Long = 0L

    private var batteryManagerCache: BatteryManager? = null
    private var powerManagerCache: PowerManager? = null

    // High-precision frame timing synchronization via Choreographer
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
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

            if (deltaSeconds >= targetInterval) {
                val fps = (frameCount / deltaSeconds).toInt()
                tickCallback?.invoke(fps, deltaSeconds, totalElapsedSeconds)

                frameCount = 0
                windowStartTimeNanos = frameTimeNanos
            }

            tickCallback?.let {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    @AnyThread
    actual fun startFpsTracker(context: PlatformContext, type: BenchmarkType, onTick: (Int, Double, Double) -> Unit) {
        tickCallback = onTick
        targetInterval = if (type == BenchmarkType.MICRO) 0.25 else 1.0

        windowStartTimeNanos = 0L
        testStartTimeNanos = 0L
        frameCount = 0

        val nativeContext = (context as? AndroidPlatformContext)?.androidContext?.applicationContext
        if (nativeContext != null) {
            batteryManagerCache = nativeContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            powerManagerCache = nativeContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        }

        Handler(Looper.getMainLooper()).post {
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    @AnyThread
    actual fun stopFpsTracker() {
        tickCallback = null
        batteryManagerCache = null
        powerManagerCache = null

        Handler(Looper.getMainLooper()).post {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        }
    }

    /**
     * Bypasses ActivityManager PSS caching by enforcing direct Debug.MemoryInfo polling.
     * Must be executed on a background thread to prevent UI freezing.
     */
    @WorkerThread
    actual fun getRamUsage(): Double {
        return try {
            val info = Debug.MemoryInfo()
            Debug.getMemoryInfo(info)
            info.totalPss / 1024.0
        } catch (e: Exception) { 0.0 }
    }

    @AnyThread
    actual fun getCpuUsage(deltaSeconds: Double): Double {
        return try {
            val currentCpuMs = android.os.Process.getElapsedCpuTime()

            if (previousCpuTimeMs == 0L) {
                previousCpuTimeMs = currentCpuMs
                return 0.0
            }

            val cpuDeltaMs = currentCpuMs - previousCpuTimeMs
            previousCpuTimeMs = currentCpuMs

            if (deltaSeconds > 0) {
                val uptimeDeltaMs = deltaSeconds * 1000.0
                val cpuPercentage = (cpuDeltaMs.toDouble() / uptimeDeltaMs) * 100.0
                round(cpuPercentage * 10.0) / 10.0
            } else 0.0
        } catch (e: Exception) { 0.0 }
    }

    @AnyThread
    actual fun primeCpu() {
        previousCpuTimeMs = android.os.Process.getElapsedCpuTime()
    }

    @AnyThread
    actual fun getBatteryLevel(): Double {
        val level = batteryManagerCache?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        return if (level >= 0) level.toDouble() else 0.0
    }

    @AnyThread
    actual fun getThermalState(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (powerManagerCache?.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE      -> "Nominal"
                PowerManager.THERMAL_STATUS_LIGHT     -> "Fair"
                PowerManager.THERMAL_STATUS_MODERATE  -> "Serious"
                PowerManager.THERMAL_STATUS_SEVERE    -> "Serious"
                PowerManager.THERMAL_STATUS_CRITICAL  -> "Critical"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "Critical"
                else                                  -> "Nominal"
            }
        } else "N/A"
    }
}