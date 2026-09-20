package com.tez.perflab.managers

/**
 * Cross-platform hardware telemetry interface.
 * Delegates OS-specific metric acquisition to their respective native implementations.
 */
expect object HardwareProfiler {
    fun startFpsTracker(context: PlatformContext, type: BenchmarkType, onTick: (fps: Int, deltaSeconds: Double, totalElapsedSeconds: Double) -> Unit)
    fun stopFpsTracker()
    fun getRamUsage(): Double
    fun getCpuUsage(deltaSeconds: Double): Double

    /**
     * Captures the initial CPU baseline to ensure accurate delta calculations.
     */
    fun primeCpu()
    fun getBatteryLevel(): Double
    fun getThermalState(): String
}