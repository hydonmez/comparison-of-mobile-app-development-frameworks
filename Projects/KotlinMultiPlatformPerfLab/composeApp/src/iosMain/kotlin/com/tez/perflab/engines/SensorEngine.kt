@file:OptIn(ExperimentalForeignApi::class)

package com.tez.perflab.engines

import com.tez.perflab.managers.PlatformContext
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreMotion.CMMotionManager
import platform.CoreMotion.CMPedometer
import platform.Foundation.NSDate
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSQualityOfServiceUserInitiated
import platform.QuartzCore.CACurrentMediaTime
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * A high-frequency hardware sensor telemetry manager.
 *
 * Uses direct C-Interop with Apple's Grand Central Dispatch (GCD) instead of 
 * Kotlin Coroutines (`Dispatchers.Main`) to bridge background sensor callbacks 
 * to the Main Thread. This reduces the allocation of transient Coroutine `Job` objects 
 * and decreases Kotlin/Native Garbage Collection (GC) overhead.
 */
actual object SensorEngine {

    private val motionManager = CMMotionManager()
    private val pedometer = CMPedometer()

    // Dedicated background operation queue configured to offload high-frequency hardware reads.
    // Restricted to serial execution (`maxConcurrentOperationCount = 1`) to guarantee thread-safe mutations.
    private val sensorQueue = NSOperationQueue().apply {
        name = "com.perflab.sensorQueue"
        maxConcurrentOperationCount = 1
        qualityOfService = NSQualityOfServiceUserInitiated
    }

    init {
        // High-frequency 100Hz (0.01s) sampling rate.
        motionManager.accelerometerUpdateInterval = 0.01
        motionManager.gyroUpdateInterval = 0.01
        motionManager.magnetometerUpdateInterval = 0.01
    }

    actual fun startSensors(
        context: PlatformContext,
        onAccelUpdate: (FloatArray) -> Unit,
        onGyroUpdate: (FloatArray) -> Unit,
        onMagnetUpdate: (FloatArray) -> Unit,
        onStepUpdate: (Int) -> Unit
    ) {

        // 1. ACCELEROMETER
        if (motionManager.isAccelerometerAvailable()) {
            var localLastAccelUpdate: Double = 0.0

            motionManager.startAccelerometerUpdatesToQueue(sensorQueue) { data, _ ->
                val now = CACurrentMediaTime()

                // Throttle visual updates to ~15Hz without touching the Main Thread.
                if (now - localLastAccelUpdate > 0.06) {
                    localLastAccelUpdate = now
                    data?.acceleration?.useContents {
                        val result = floatArrayOf(x.toFloat(), y.toFloat(), z.toFloat())

                        // Bypasses Kotlin Coroutine allocations by directly dispatching 
                        // the array to the iOS Main Queue using GCD.
                        dispatch_async(dispatch_get_main_queue()) {
                            onAccelUpdate(result)
                        }
                    }
                }
            }
        }

        // 2. GYROSCOPE
        if (motionManager.isGyroAvailable()) {
            var localLastGyroUpdate: Double = 0.0

            motionManager.startGyroUpdatesToQueue(sensorQueue) { data, _ ->
                val now = CACurrentMediaTime()
                if (now - localLastGyroUpdate > 0.06) {
                    localLastGyroUpdate = now
                    data?.rotationRate?.useContents {
                        val result = floatArrayOf(x.toFloat(), y.toFloat(), z.toFloat())

                        dispatch_async(dispatch_get_main_queue()) {
                            onGyroUpdate(result)
                        }
                    }
                }
            }
        }

        // 3. MAGNETOMETER
        if (motionManager.isMagnetometerAvailable()) {
            var localLastMagnetUpdate: Double = 0.0

            motionManager.startMagnetometerUpdatesToQueue(sensorQueue) { data, _ ->
                val now = CACurrentMediaTime()
                if (now - localLastMagnetUpdate > 0.06) {
                    localLastMagnetUpdate = now
                    data?.magneticField?.useContents {
                        val result = floatArrayOf(x.toFloat(), y.toFloat(), z.toFloat())

                        dispatch_async(dispatch_get_main_queue()) {
                            onMagnetUpdate(result)
                        }
                    }
                }
            }
        }

        // 4. PEDOMETER
        // Emits updates deterministically based on physical steps, resulting in a naturally 
        // low-frequency callback that doesn't require manual UI throttling.
        if (CMPedometer.isStepCountingAvailable()) {
            pedometer.startPedometerUpdatesFromDate(NSDate()) { data, _ ->
                data?.let {
                    val steps = it.numberOfSteps.intValue

                    dispatch_async(dispatch_get_main_queue()) {
                        onStepUpdate(steps)
                    }
                }
            }
        }
    }

    actual fun stopSensors() {
        motionManager.stopAccelerometerUpdates()
        motionManager.stopGyroUpdates()
        motionManager.stopMagnetometerUpdates()
        pedometer.stopPedometerUpdates()
    }
}