package com.tez.nativekotlinperflabapp.models

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A thread-safe Data Transfer Object (DTO) representing a hardware telemetry snapshot.
 */
data class PerformanceLog(
    val timestampMillis: Long,
    val cpuUsage: Double,

    /** Absolute physical memory consumption (MB) */
    val rawRAM: Double,

    /** Relative application memory overhead (Net Delta in MB) */
    val netRAM: Double,

    val batteryLevel: Double,
    val fps: Int,
    val thermalState: String
) {
    /**
     * Uses the timestamp as a unique identifier for efficient UI diffing.
     */
    val id: Long get() = timestampMillis

    /**
     * A lazily evaluated, human-readable time string for UI binding and data export.
     */
    val formattedTime: String
        get() = timeFormatter.format(
            Instant.ofEpochMilli(timestampMillis)
                .atZone(ZoneId.systemDefault())
        )

    companion object {
        /**
         * A thread-safe, statically allocated formatter reused across the application
         * to minimize memory allocations.
         */
        private val timeFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}