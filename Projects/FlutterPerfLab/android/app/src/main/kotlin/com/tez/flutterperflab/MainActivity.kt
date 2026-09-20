package com.tez.flutterperflab

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.round

// Native Telemetry Bridge
// Serves as the hardware bridge for the Flutter engine.
// Extracts CPU throughput, RAM (PSS), Battery capacity, and Thermal states directly from the Android OS.
class MainActivity: FlutterActivity() {
    private val CHANNEL = "com.benchmark.hardware"

    @Volatile
    private var previousCpuTimeMs: Long = 0L

    // Background scope for heavy OS APIs
    private val ioScope = CoroutineScope(Dispatchers.IO)

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            if (call.method == "getHardwareMetrics") {
                val deltaSeconds = call.argument<Double>("deltaSeconds") ?: 1.0

                // Offload measurements to the IO thread to prevent blocking the Flutter UI thread.
                ioScope.launch {
                    val battery = getBatteryLevel()
                    val cpu = getCpuUsage(deltaSeconds)
                    val thermal = getThermalState()
                    val ram = getAccurateRamUsage() // Accurate RAM measurement bypassing the cache

                    // MethodChannel results must be returned on the Main Thread.
                    withContext(Dispatchers.Main) {
                        result.success(mapOf(
                            "battery" to battery,
                            "cpu" to cpu,
                            "thermal" to thermal,
                            "ram" to ram
                        ))
                    }
                }
            } else {
                result.notImplemented()
            }
        }
    }

    private fun getBatteryLevel(): Double {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        return if (level >= 0) level.toDouble() else 0.0
    }

    private fun getCpuUsage(deltaSeconds: Double): Double {
        return try {
            val currentCpuMs = android.os.Process.getElapsedCpuTime()

            // Prime the system on the first read and return 0.0.
            // The warm-up phase handles these initial seconds.
            if (previousCpuTimeMs == 0L) {
                previousCpuTimeMs = currentCpuMs
                return 0.0
            }

            val cpuDeltaMs = currentCpuMs - previousCpuTimeMs
            previousCpuTimeMs = currentCpuMs

            val uptimeDeltaMs = deltaSeconds * 1000.0
            if (uptimeDeltaMs > 0) {
                val cpuPercentage = (cpuDeltaMs.toDouble() / uptimeDeltaMs) * 100.0
                round(cpuPercentage * 10.0) / 10.0
            } else {
                0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }

    // Accurate native RAM extraction.
    // ActivityManager cache was bypassed to ensure accuracy.
    // Using Debug.getMemoryInfo in the background prevents UI stutter.
    private fun getAccurateRamUsage(): Double {
        return try {
            val memoryInfo = android.os.Debug.MemoryInfo()
            android.os.Debug.getMemoryInfo(memoryInfo)
            val pssMb = memoryInfo.totalPss / 1024.0
            round(pssMb * 10.0) / 10.0
        } catch (e: Exception) {
            0.0
        }
    }

    private fun getThermalState(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            when (powerManager?.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "Nominal"
                PowerManager.THERMAL_STATUS_LIGHT -> "Fair"
                PowerManager.THERMAL_STATUS_MODERATE,
                PowerManager.THERMAL_STATUS_SEVERE -> "Serious"
                PowerManager.THERMAL_STATUS_CRITICAL,
                PowerManager.THERMAL_STATUS_EMERGENCY -> "Critical"
                else -> "Nominal"
            }
        } else {
            "N/A"
        }
    } 
}