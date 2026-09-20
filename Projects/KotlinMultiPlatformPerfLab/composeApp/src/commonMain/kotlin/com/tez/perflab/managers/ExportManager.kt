package com.tez.perflab.managers

import com.tez.perflab.models.PerformanceLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Centralized engine for aggregating telemetry logs, performing statistical
 * calculations, and dispatching formatted CSV artifacts across target platforms.
 */
expect suspend fun saveCsvToCache(fileName: String, content: String, context: PlatformContext): String?
expect fun platformShareFile(context: PlatformContext, filePath: String)
expect fun getCurrentEpochSeconds(): Long
object ExportManager {

    /**
     * Constructs a structured CSV document from performance logs and triggers native OS sharing.
     * Offloaded to [Dispatchers.Default] to prevent UI thread blocking during string processing.
     */
    suspend fun generateAndShareCSV(
        logs: List<PerformanceLog>,
        testName: String,
        context: PlatformContext,
        customSummary: String? = null
    ) = withContext(Dispatchers.Default) {

        if (logs.isEmpty()) return@withContext

        val csvContent = buildString {
            // Standard CSV header for data analysis
            append("Timestamp,CPU(%),Raw_RAM(MB),Net_RAM(MB),Battery(%),FPS,Thermal_State\n")
            for (log in logs) {
                append(log.formattedTime).append(",")
                append(formatDouble(log.cpuUsage)).append(",")
                append(formatDouble(log.rawRAM)).append(",")
                append(formatDouble(log.netRAM)).append(",")
                append(formatDouble(log.batteryLevel, 1)).append(",")
                append(log.fps).append(",")
                append(log.thermalState).append("\n")
            }
            append("\n--- STATISTICAL SUMMARY ---\n")
            append(calculateSummary(logs))

            if (customSummary != null) {
                append("\nNote: ").append(customSummary).append("\n")
            }
        }

        val timestamp = getCurrentEpochSeconds()
        val fileName = "${testName}_${timestamp}.csv"

        val filePath = saveCsvToCache(fileName, csvContent, context)

        if (filePath != null) {
            /**
             * The native share sheet must be invoked on the Main Thread to satisfy OS windowing constraints.
             */
            withContext(Dispatchers.Main) {
                platformShareFile(context, filePath)
            }
        }
    }

    /**
     * Calculates descriptive statistics (Mean, StdDev, Min, Max) on telemetry datasets
     * to provide a high-level performance overview.
     */
    private fun calculateSummary(logs: List<PerformanceLog>): String {
        // Explicit type definitions to resolve interoperability inference ambiguity
        val cpu: List<Double> = logs.map { it.cpuUsage }
        val rawRam: List<Double> = logs.map { it.rawRAM }
        val netRam: List<Double> = logs.map { it.netRAM }
        val fps: List<Double> = logs.map { it.fps.toDouble() }

        fun avg(v: List<Double>): Double = if (v.isNotEmpty()) v.average() else 0.0

        /**
         * Calculates sample standard deviation to measure the variance of hardware metrics.
         */
        fun std(v: List<Double>): Double {
            if (v.size <= 1) return 0.0
            val mean = avg(v)
            val variance = v.sumOf { (it - mean).pow(2) } / (v.size - 1)
            return sqrt(variance)
        }

        return buildString {
            append("Metric,Average,StdDev,Min,Max\n")
            append("CPU(%),${formatDouble(avg(cpu))},${formatDouble(std(cpu))},${formatDouble(cpu.minOrNull() ?: 0.0)},${formatDouble(cpu.maxOrNull() ?: 0.0)}\n")
            append("Raw_RAM(MB),${formatDouble(avg(rawRam))},${formatDouble(std(rawRam))},${formatDouble(rawRam.minOrNull() ?: 0.0)},${formatDouble(rawRam.maxOrNull() ?: 0.0)}\n")
            append("Net_RAM(MB),${formatDouble(avg(netRam))},${formatDouble(std(netRam))},${formatDouble(netRam.minOrNull() ?: 0.0)},${formatDouble(netRam.maxOrNull() ?: 0.0)}\n")
            append("FPS,${formatDouble(avg(fps))},${formatDouble(std(fps))},${formatDouble(fps.minOrNull() ?: 0.0)},${formatDouble(fps.maxOrNull() ?: 0.0)}\n")
        }
    }

    /**
     * Produces a locale-invariant decimal string representation.
     * Guarantees the decimal separator is always a period (.), preventing
     * structural corruption of CSV files in regions where the comma (,) is the default.
     */
    private fun formatDouble(value: Double, decimals: Int = 2): String {
        if (value.isNaN() || value.isInfinite()) return "0.${"0".repeat(decimals)}"

        val factor = 10.0.pow(decimals)
        val isNegative = value < 0.0
        val absValue = kotlin.math.abs(value)
        val rounded = round(absValue * factor) / factor

        val intPart = rounded.toLong()
        val fracPart = round((rounded - intPart.toDouble()) * factor).toLong()
        val sign = if (isNegative) "-" else ""

        return "$sign$intPart.${fracPart.toString().padStart(decimals, '0').take(decimals)}"
    }
}