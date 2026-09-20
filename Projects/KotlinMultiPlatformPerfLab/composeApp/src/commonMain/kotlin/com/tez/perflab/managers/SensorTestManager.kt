package com.tez.perflab.managers

import com.tez.perflab.engines.SensorEngine

/**
 * A lightweight cross-platform orchestrator for hardware sensors.
 *
 * Refactored to eliminate the intermediate StateFlow layer, ensuring strict
 * benchmarking accuracy with native implementations. By acting purely as a
 * stateless pass-through delegator, it prevents redundant object allocations
 * and shifts lifecycle-aware memory management entirely to the ViewModel layer.
 */
object SensorTestManager {

    /**
     * Bridges pure lambda callbacks from the UI layer directly to the OS-specific engines.
     */
    fun startSensors(
        context: PlatformContext,
        onAccelUpdate: (FloatArray) -> Unit,
        onGyroUpdate: (FloatArray) -> Unit,
        onMagnetUpdate: (FloatArray) -> Unit,
        onStepUpdate: (Int) -> Unit
    ) {
        SensorEngine.startSensors(
            context = context,
            onAccelUpdate = onAccelUpdate,
            onGyroUpdate = onGyroUpdate,
            onMagnetUpdate = onMagnetUpdate,
            onStepUpdate = onStepUpdate
        )
    }

    fun stopSensors() {
        SensorEngine.stopSensors()
    }
}