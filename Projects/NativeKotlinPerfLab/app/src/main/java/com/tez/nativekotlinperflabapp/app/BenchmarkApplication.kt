package com.tez.nativekotlinperflabapp.app

import android.app.Application
import com.tez.nativekotlinperflabapp.core.managers.LaunchPerformanceManager

/**
  The custom Application class acting as the root entry point of the entire application lifecycle.
 */
class BenchmarkApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Establishes the absolute temporal zero-point for Cold Start telemetry.
        // capturing the earliest possible user-space execution timestamp
        // before any Activity or View routing overhead occurs.
        LaunchPerformanceManager.appStarted()
    }
}