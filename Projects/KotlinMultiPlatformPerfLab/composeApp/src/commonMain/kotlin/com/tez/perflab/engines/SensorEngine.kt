package com.tez.perflab.engines

import com.tez.perflab.managers.PlatformContext

/**
 * Platform-agnostic interface for hardware sensors.
 * Delegated to OS-specific implementations (CoreMotion / SensorManager).
 */
expect object SensorEngine {

    /**
     * Starts sensor data collection.
     * Uses direct callbacks to minimize object allocation overhead across native boundaries.
     */
    fun startSensors(
        context: PlatformContext,
        onAccelUpdate: (FloatArray) -> Unit,
        onGyroUpdate: (FloatArray) -> Unit,
        onMagnetUpdate: (FloatArray) -> Unit,
        onStepUpdate: (Int) -> Unit
    )

    /**
     * Terminates all active hardware sensor streams.
     */
    fun stopSensors()
}