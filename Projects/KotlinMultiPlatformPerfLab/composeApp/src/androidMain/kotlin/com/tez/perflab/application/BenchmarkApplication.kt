package com.tez.perflab.application

import android.app.Application
import com.tez.perflab.managers.ApplicationContextHolder
import com.tez.perflab.managers.LaunchPerformanceManager

/**
 * The absolute temporal zero-point for Cold Start telemetry.
 * Initializes global singletons and synchronizes launch metrics to guarantee
 * timing parity with the iOS application initialization lifecycle.
 */
class BenchmarkApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initializes the global context registry to safely bridge Android components
        // into the KMP domain without leaking Activities.
        ApplicationContextHolder.init(this)

        // Marks the baseline timestamp for hardware cold-start measurements.
        LaunchPerformanceManager.appStarted()
    }
}