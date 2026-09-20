package com.tez.perflab.managers

import android.os.SystemClock

/**
 * Retrieves the monotonic system uptime in nanoseconds.
 * Guarantees a strictly increasing timeline, including deep sleep intervals.
 * Useful for high-precision hardware benchmarking and profiling.
 */
actual fun getSystemUptimeNanos(): Long {
    return SystemClock.elapsedRealtimeNanos()
}