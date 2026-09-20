package com.tez.perflab.application

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.tez.perflab.App
import com.tez.perflab.managers.LaunchPerformanceManager

/**
 * The primary entry point for the UI rendering pipeline.
 * Orchestrates lifecycle-aware telemetry acquisition for Cold and Hot Start benchmarking.
 */
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Log.w("LaunchPerformance", "ACTIVITY_RECOGNITION permission denied.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Records the precise timestamp when the OS hands over control to the Activity framework.
        LaunchPerformanceManager.osReady()

        super.onCreate(savedInstanceState)

        // Initiates hardware sensor permission resolution essential for the telemetry engines.
        requestPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)

        // Hooks into the global process lifecycle to accurately measure Hot Start transition latency.
        ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> LaunchPerformanceManager.appIsWakingUp()
                Lifecycle.Event.ON_RESUME -> LaunchPerformanceManager.hotStartDetected()
                else -> {}
            }
        })

        setContent {
            App()
        }
    }
}