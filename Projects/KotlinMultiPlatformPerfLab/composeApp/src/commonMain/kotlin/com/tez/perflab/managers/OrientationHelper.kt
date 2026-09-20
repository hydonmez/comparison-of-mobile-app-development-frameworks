package com.tez.perflab.managers

/**
 * Delegates hardware screen orientation locks to the native OS implementations.
 *
 * This bridge is essential for benchmarking stability. By locking the orientation
 * before a test sequence, we prevent OS-level configuration changes (Activity
 * recreation or Layout recalculations) from introducing artificial CPU/RAM spikes
 * during data acquisition.
 */
expect fun lockScreenOrientation(context: PlatformContext, isLandscape: Boolean)