package com.tez.perflab.engines

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.tez.perflab.managers.AndroidPlatformContext
import com.tez.perflab.managers.PlatformContext

/**
 * Android-specific implementation of the hardware sensor engine.
 *
 * - Threading: Uses a standard background [HandlerThread].
 * - Throttling: Uses a 60ms telemetry emission gate to filter redundant hardware interrupts.
 */
actual object SensorEngine : SensorEventListener {

    private var sensorManager: SensorManager? = null

    // Stateless callbacks to prevent garbage collection overhead.
    private var accelCallback: ((FloatArray) -> Unit)? = null
    private var gyroCallback: ((FloatArray) -> Unit)? = null
    private var magnetCallback: ((FloatArray) -> Unit)? = null
    private var stepCallback: ((Int) -> Unit)? = null

    // Throttling state variables
    private var lastAccelUpdate: Long = 0
    private var lastGyroUpdate: Long = 0
    private var lastMagnetUpdate: Long = 0
    private var initialStepCount: Int = -1

    private var isRegistered: Boolean = false

    // Background looper to handle hardware interrupts.
    private var sensorThread: HandlerThread? = null
    private var sensorHandler: Handler? = null

    actual fun startSensors(
        context: PlatformContext,
        onAccelUpdate: (FloatArray) -> Unit,
        onGyroUpdate: (FloatArray) -> Unit,
        onMagnetUpdate: (FloatArray) -> Unit,
        onStepUpdate: (Int) -> Unit
    ) {
        if (isRegistered) stopSensors()

        this.accelCallback = onAccelUpdate
        this.gyroCallback = onGyroUpdate
        this.magnetCallback = onMagnetUpdate
        this.stepCallback = onStepUpdate

        this.lastAccelUpdate = 0
        this.lastGyroUpdate = 0
        this.lastMagnetUpdate = 0
        this.initialStepCount = -1

        if (sensorManager == null) {
            val androidContext = (context as AndroidPlatformContext).androidContext.applicationContext
            sensorManager = androidContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        }

        // Initialized with standard background priority.
        sensorThread = HandlerThread("SensorBenchmarkThread").apply { start() }
        sensorHandler = Handler(sensorThread!!.looper)

        val sm = sensorManager ?: return

        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST, sensorHandler)
        }
        sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST, sensorHandler)
        }
        sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST, sensorHandler)
        }
        sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL, sensorHandler)
        }

        isRegistered = true
    }

    actual fun stopSensors() {
        sensorManager?.unregisterListener(this)

        // Graceful termination of the background thread to prevent memory leaks.
        sensorThread?.quitSafely()
        sensorThread = null
        sensorHandler = null

        isRegistered = false
        accelCallback = null
        gyroCallback = null
        magnetCallback = null
        stepCallback = null
        initialStepCount = -1
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        // Monotonic clock utilization guarantees precision regardless of OS time syncs.
        val now = SystemClock.elapsedRealtime()

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                // Throttles updates to ~16Hz (60ms).
                if (now - lastAccelUpdate > 60) {
                    lastAccelUpdate = now
                    accelCallback?.invoke(event.values.clone())
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                if (now - lastGyroUpdate > 60) {
                    lastGyroUpdate = now
                    gyroCallback?.invoke(event.values.clone())
                }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                if (now - lastMagnetUpdate > 60) {
                    lastMagnetUpdate = now
                    magnetCallback?.invoke(event.values.clone())
                }
            }
            Sensor.TYPE_STEP_COUNTER -> {
                val totalSteps = event.values[0].toInt()
                if (initialStepCount == -1) {
                    initialStepCount = totalSteps
                }
                stepCallback?.invoke(totalSteps - initialStepCount)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Required interface implementation; intentionally blank.
    }
}