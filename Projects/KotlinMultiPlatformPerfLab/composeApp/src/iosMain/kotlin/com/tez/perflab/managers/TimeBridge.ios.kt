package com.tez.perflab.managers

import platform.QuartzCore.CACurrentMediaTime

/**
 * Returns the current system uptime in nanoseconds for high-precision telemetry.
 * 
 * Utilizes Apple's QuartzCore `CACurrentMediaTime()`, which provides a monotonic 
 * clock reference that remains unaffected by system time adjustments or NTP synchronization.
 * Ideal for rendering and latency benchmarks on Darwin-based systems.
 */
actual fun getSystemUptimeNanos(): Long {
    // Converts the absolute double-precision seconds to nanoseconds (Long)
    // to maintain alignment with the JVM's `System.nanoTime()`.
    return (CACurrentMediaTime() * 1_000_000_000.0).toLong()
}