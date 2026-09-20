package com.reactnativeperflab

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

/**
 * Hardware Telemetry Module (Android)
 * An asynchronous Native Module exposing OS-level hardware metrics to the JS runtime.
 * Utilizes React Native's Promise architecture to ensure heavy kernel-level
 * queries (like android.os.Debug.getMemoryInfo) do not block the JS thread.
 */
class HardwareTelemetryModule(private val reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    private var previousCpuTimeMs: Long = 0L

    // The name exposed to JavaScript: NativeModules.HardwareTelemetryModule
    override fun getName(): String {
        return "HardwareTelemetryModule"
    }

    @ReactMethod
    fun getOptimizedRAMUsage(promise: Promise) {
        try {
            val memoryInfo = android.os.Debug.MemoryInfo()
            android.os.Debug.getMemoryInfo(memoryInfo)
            val ramMB = memoryInfo.totalPss / 1024.0
            promise.resolve(ramMB)
        } catch (e: Exception) {
            promise.resolve(0.0) // Fallback to prevent Bridge crashes
        }
    }

    @ReactMethod
    fun syncCpuBaseline(promise: Promise) {
        previousCpuTimeMs = android.os.Process.getElapsedCpuTime()
        promise.resolve(null)
    }

    @ReactMethod
    fun getProcessCpuUsage(deltaSeconds: Double, promise: Promise) {
        try {
            val currentCpuMs = android.os.Process.getElapsedCpuTime()
            val cpuDeltaMs = currentCpuMs - previousCpuTimeMs
            previousCpuTimeMs = currentCpuMs

            if (deltaSeconds > 0) {
                val uptimeDeltaMs = deltaSeconds * 1000.0
                val cpuPercentage = (cpuDeltaMs.toDouble() / uptimeDeltaMs) * 100.0
                // Match baseline precision
                promise.resolve(Math.round(cpuPercentage * 10.0) / 10.0)
            } else {
                promise.resolve(0.0)
            }
        } catch (e: Exception) {
            promise.resolve(0.0)
        }
    }

    @ReactMethod
    fun getBatteryLevel(promise: Promise) {
        val batteryManager = reactContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        promise.resolve(if (level >= 0) level.toDouble() else 0.0)
    }

    @ReactMethod
    fun getThermalStateString(promise: Promise) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = reactContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val state = when (powerManager?.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "Nominal"
                PowerManager.THERMAL_STATUS_LIGHT -> "Fair"
                PowerManager.THERMAL_STATUS_MODERATE, PowerManager.THERMAL_STATUS_SEVERE -> "Serious"
                PowerManager.THERMAL_STATUS_CRITICAL, PowerManager.THERMAL_STATUS_EMERGENCY -> "Critical"
                else -> "Nominal"
            }
            promise.resolve(state)
        } else {
            promise.resolve("N/A")
        }
    }
}