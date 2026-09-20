package com.tez.nativekotlinperflabapp.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tez.nativekotlinperflabapp.core.managers.LaunchPerformanceManager
import com.tez.nativekotlinperflabapp.ui.features.videotest.VideoTestScreen
import com.tez.nativekotlinperflabapp.ui.features.audiotest.AudioTestScreen
import com.tez.nativekotlinperflabapp.ui.features.jsontest.JsonTestScreen
import com.tez.nativekotlinperflabapp.ui.features.listtest.ListTestScreen
import com.tez.nativekotlinperflabapp.ui.features.maptest.MapTestScreen
import com.tez.nativekotlinperflabapp.ui.features.sensortest.SensorTestScreen
import com.tez.nativekotlinperflabapp.ui.features.storagetest.StorageTestScreen

/**
 * The primary entry point and UI orchestrator of the benchmarking application.
 * Responsibilities include terminating the launch telemetry chronometers (Cold/Hot Starts),
 * establishing process-level lifecycle observation, and bootstrapping the declarative
 * Compose navigation graph.
 */
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            android.util.Log.w(
                "LaunchPerformance",
                "ACTIVITY_RECOGNITION permission denied. Step counter will return zero."
            )
        }
    }

    // [MEMORY LEAK PREVENTION]
    // Retains an explicit reference to the registered observer to enable deterministic
    // deregistration in onDestroy. Without this, each Activity recreation (e.g., orientation
    // change, system-initiated restart) appends a new observer to the ProcessLifecycleOwner,
    // causing hot start events to be reported multiple times per transition.
    private val processLifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START  -> LaunchPerformanceManager.appIsWakingUp()
            Lifecycle.Event.ON_RESUME -> LaunchPerformanceManager.hotStartDetected()
            else -> {}
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // [TELEMETRY ZERO-POINT]
        // Halting the OS-level duration clock the exact millisecond the Application process
        // successfully hands over control to the primary Activity lifecycle.
        LaunchPerformanceManager.osReady()

        super.onCreate(savedInstanceState)

        checkAndRequestSensorPermissions()

        // Hooks into the global process lifecycle to accurately measure Hot Start transition latency.
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)

        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {

                    // In Jetpack Compose, 'LaunchedEffect(Unit)' is strictly executed *after* the
                    // initial composition and layout passes are successfully committed to the GPU.
                    LaunchedEffect(Unit) {
                        LaunchPerformanceManager.reportBenchmark()
                    }

                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "dashboard") {
                        composable("dashboard") { MainDashboardScreen(navController) }
                        composable("video_test")   { VideoTestScreen() }
                        composable("audio_test")   { AudioTestScreen() }
                        composable("storage_test") { StorageTestScreen() }
                        composable("json_test")    { JsonTestScreen() }
                        composable("map_test")     { MapTestScreen() }
                        composable("list_test")    { ListTestScreen() }
                        composable("sensor_test")  { SensorTestScreen() }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Deregister the process lifecycle observer to prevent accumulation across Activity recreations.
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processLifecycleObserver)
    }

    private fun checkAndRequestSensorPermissions() {
        requestPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
    }
}