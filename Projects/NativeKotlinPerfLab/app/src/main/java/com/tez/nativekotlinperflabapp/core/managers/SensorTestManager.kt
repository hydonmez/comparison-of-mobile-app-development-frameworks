package com.tez.nativekotlinperflabapp.core.managers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock

/**
 * A high-frequency hardware telemetry manager.
 * Implemented as a thread-safe singleton to prevent overhead and ensure
 * stable background execution.
 */
object SensorTestManager : SensorEventListener {

    private var sensorManager: SensorManager? = null

    // --- SENSOR CALLBACKS ---
    private var onAccelUpdate: ((FloatArray) -> Unit)? = null
    private var onGyroUpdate: ((FloatArray) -> Unit)? = null
    private var onMagnetUpdate: ((FloatArray) -> Unit)? = null
    private var onStepUpdate: ((Int) -> Unit)? = null

    // --- THROTTLING REGISTERS ---
    private var lastAccelUpdate: Long = 0
    private var lastGyroUpdate: Long = 0
    private var lastMagnetUpdate: Long = 0
    private var initialStepCount: Int = -1

    private var isRegistered: Boolean = false

    private var sensorThread: HandlerThread? = null
    private var sensorHandler: Handler? = null

    /**
     * Initializes hardware sensors and binds the execution context to a background thread.
     */
    fun startSensors(
        context: Context,
        accelCallback: (FloatArray) -> Unit,
        gyroCallback: (FloatArray) -> Unit,
        magnetCallback: (FloatArray) -> Unit,
        stepCallback: (Int) -> Unit
    ) {
        // Safeguard against multiple registrations and thread orphaning.
        if (isRegistered) stopSensors()

        this.onAccelUpdate = accelCallback
        this.onGyroUpdate = gyroCallback
        this.onMagnetUpdate = magnetCallback
        this.onStepUpdate = stepCallback

        this.lastAccelUpdate = 0
        this.lastGyroUpdate = 0
        this.lastMagnetUpdate = 0
        this.initialStepCount = -1

        if (sensorManager == null) {
            sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        }

        // Use a background thread for sensor processing.
        sensorThread = HandlerThread("HardwareTelemetryThread").apply { start() }
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

    /**
     * Stops hardware listeners and clears callbacks to prevent memory leaks.
     */
    fun stopSensors() {
        sensorManager?.unregisterListener(this)

        sensorThread?.quitSafely()
        sensorThread = null
        sensorHandler = null

        isRegistered = false

        onAccelUpdate = null
        onGyroUpdate = null
        onMagnetUpdate = null
        onStepUpdate = null
        initialStepCount = -1
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        val now = SystemClock.elapsedRealtime()

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                // Limits emission frequency to ~16Hz (60ms intervals)
                if (now - lastAccelUpdate > 60) {
                    lastAccelUpdate = now
                    onAccelUpdate?.invoke(event.values.clone())
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                if (now - lastGyroUpdate > 60) {
                    lastGyroUpdate = now
                    onGyroUpdate?.invoke(event.values.clone())
                }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                if (now - lastMagnetUpdate > 60) {
                    lastMagnetUpdate = now
                    onMagnetUpdate?.invoke(event.values.clone())
                }
            }
            Sensor.TYPE_STEP_COUNTER -> {
                val totalSteps = event.values[0].toInt()
                if (initialStepCount == -1) {
                    initialStepCount = totalSteps
                }
                onStepUpdate?.invoke(totalSteps - initialStepCount)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Required interface implementation
    }
}