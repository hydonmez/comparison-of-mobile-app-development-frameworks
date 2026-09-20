package com.tez.perflab.models

import kotlinx.serialization.Serializable

/**
 * A thread-safe Data Transfer Object (DTO) representing a discrete temporal snapshot
 * of hardware telemetry.
 */
@Serializable
data class PerformanceLog(
    val timestampMillis: Long,
    val cpuUsage: Double,
    val rawRAM: Double,
    val netRAM: Double,
    val batteryLevel: Double,
    val fps: Int,
    val thermalState: String
) {
    /**
     * Derives an identifier directly from the timestamp.
     * Eliminates UUID allocation overhead and mitigates Garbage Collection (GC) churn
     * during high-frequency telemetry sampling.
     */
    val id: Long get() = timestampMillis

    /**
     * Human-readable temporal representation for UI binding and CSV export.
     * Evaluated lazily to defer execution until required by the UI or I/O pipeline.
     */
    val formattedTime: String
        get() = formatTimestamp(timestampMillis)

    companion object {
        /**
         * Computes a formatted time string using mathematical operations.
         * This avoids the instantiation of heavy platform-specific date formatters
         * and minimizes memory allocations during rapid telemetry cycles.
         */
        private fun formatTimestamp(millis: Long): String {
            val totalSeconds = millis / 1000
            val hours = (totalSeconds / 3600) % 24
            val minutes = (totalSeconds / 60) % 60
            val seconds = totalSeconds % 60

            return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        }
    }
}