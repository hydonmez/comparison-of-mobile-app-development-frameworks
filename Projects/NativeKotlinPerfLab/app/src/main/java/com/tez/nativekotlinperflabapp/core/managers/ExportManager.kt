package com.tez.nativekotlinperflabapp.core.managers

import android.content.Context
import com.tez.nativekotlinperflabapp.models.PerformanceLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A singleton utility for aggregating and exporting telemetry data.
 * Formats performance logs into structured CSV files using the US Locale
 * to ensure cross-platform delimiter integrity.
 */
object ExportManager {

    /**
     * Generates a CSV file from a collection of performance logs.
     * Uses Dispatchers.IO to prevent UI thread blocking during disk writes,
     * and implements direct file streaming to avoid Out-Of-Memory (OOM) exceptions.
     */
    suspend fun generateCSV(
        context: Context,
        logs: List<PerformanceLog>,
        testName: String,
        customSummary: String? = null
    ): File? = withContext(Dispatchers.IO) {

        if (logs.isEmpty()) return@withContext null

        val timestamp = System.currentTimeMillis() / 1000
        val fileName = "${testName}_${timestamp}.csv"
        val file = File(context.applicationContext.cacheDir, fileName)

        return@withContext try {
            file.bufferedWriter().use { writer ->
                writer.append("Timestamp,CPU(%),Raw_RAM(MB),Net_RAM(MB),Battery(%),FPS,Thermal_State\n")

                for (log in logs) {
                    writer.append(log.formattedTime).append(",")
                    writer.append(formatDouble(log.cpuUsage)).append(",")
                    writer.append(formatDouble(log.rawRAM)).append(",")
                    writer.append(formatDouble(log.netRAM)).append(",")
                    writer.append(formatDouble(log.batteryLevel, 1)).append(",")
                    writer.append(log.fps.toString()).append(",")
                    writer.append(log.thermalState).append("\n")
                }

                writer.append("\n--- SUMMARY ---\n")
                writer.append(summaryRow(logs))

                if (customSummary != null) {
                    writer.append("\n").append(customSummary).append("\n")
                }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun summaryRow(logs: List<PerformanceLog>): String {
        val cpu = logs.map { it.cpuUsage }
        val rawRam = logs.map { it.rawRAM }
        val netRam = logs.map { it.netRAM }
        val fps = logs.map { it.fps.toDouble() }

        fun avg(v: List<Double>): Double = if (v.isNotEmpty()) v.average() else 0.0

        /**
         * Calculates the Sample Standard Deviation using Bessel's correction (N-1).
         * Includes a guard clause to prevent division-by-zero when only a single frame is logged.
         */
        fun std(v: List<Double>): Double {
            if (v.size <= 1) return 0.0
            val mean = avg(v)
            val variance = v.sumOf { (it - mean).pow(2) } / (v.size - 1)
            return sqrt(variance)
        }

        fun minMax(v: List<Double>): Pair<Double, Double> =
            Pair(v.minOrNull() ?: 0.0, v.maxOrNull() ?: 0.0)

        val cpuMM = minMax(cpu)
        val rawRamMM = minMax(rawRam)
        val netRamMM = minMax(netRam)
        val fpsMM = minMax(fps)

        return buildString {
            append("Metric,Average,StdDev,Min,Max\n")
            append("CPU(%),${formatDouble(avg(cpu))},${formatDouble(std(cpu))},${formatDouble(cpuMM.first)},${formatDouble(cpuMM.second)}\n")
            append("Raw_RAM(MB),${formatDouble(avg(rawRam))},${formatDouble(std(rawRam))},${formatDouble(rawRamMM.first)},${formatDouble(rawRamMM.second)}\n")
            append("Net_RAM(MB),${formatDouble(avg(netRam))},${formatDouble(std(netRam))},${formatDouble(netRamMM.first)},${formatDouble(netRamMM.second)}\n")
            append("FPS,${formatDouble(avg(fps))},${formatDouble(std(fps))},${formatDouble(fpsMM.first)},${formatDouble(fpsMM.second)}\n")
        }
    }

    private fun formatDouble(value: Double, decimals: Int = 2): String {
        return String.format(Locale.US, "%.${decimals}f", value)
    }
}