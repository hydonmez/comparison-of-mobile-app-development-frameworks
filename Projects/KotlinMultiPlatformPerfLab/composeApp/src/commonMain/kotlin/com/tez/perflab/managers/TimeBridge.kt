package com.tez.perflab.managers

/**
 * Bypasses the Kotlin Standard Library to fetch absolute hardware-level nanoseconds.
 *
 * Standard time APIs are often susceptible to NTP network syncs or user time-zone changes.
 * This bridge guarantees exact mathematical alignment with:
 * - Native iOS: `ProcessInfo.processInfo.systemUptime`
 * - Native Android: `SystemClock.elapsedRealtimeNanos()`
 *
 * This ensures that benchmark durations are strictly monotonic and immune to external clock drifts.
 */
expect fun getSystemUptimeNanos(): Long