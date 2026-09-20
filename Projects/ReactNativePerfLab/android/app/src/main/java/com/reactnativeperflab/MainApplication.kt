package com.reactnativeperflab

import android.app.Application
import android.os.SystemClock
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost

class MainApplication : Application(), ReactApplication {

    companion object {
        private var bootMillis: Double = 0.0
        private var bootNanos: Long = 0
        
        // Holds the exact decimal timestamp of the OS boot.
        var osStartEpochMs: Double = 0.0
        
        /**
         * Interpolates the integer-based Unix Epoch with hardware-level monotonic nanoseconds.
         * This bypasses the millisecond truncation of System.currentTimeMillis(), allowing 
         * React Native to receive precise, floating-point timestamps.
         */
        fun getHighResEpochMs(): Double {
            val currentNanos = SystemClock.elapsedRealtimeNanos()
            val elapsedMs = (currentNanos - bootNanos) / 1_000_000.0
            return bootMillis + elapsedMs
        }
    }

    override val reactHost: ReactHost by lazy {
        getDefaultReactHost(
            context = applicationContext,
            packageList = PackageList(this).packages.apply {
                add(LaunchPerformancePackage())
                add(HardwareTelemetryPackage())
                add(NativeStorageBenchmarkPackage())
            }
        )
    }

    override fun onCreate() {
        // Process Initialization & Clock Calibration
        bootMillis = System.currentTimeMillis().toDouble()
        bootNanos = SystemClock.elapsedRealtimeNanos()
        
        osStartEpochMs = getHighResEpochMs()
        
        super.onCreate()
        loadReactNative(this)
    }
}