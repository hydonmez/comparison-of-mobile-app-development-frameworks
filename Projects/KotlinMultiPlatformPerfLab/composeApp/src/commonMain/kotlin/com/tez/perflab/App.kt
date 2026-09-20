package com.tez.perflab

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tez.perflab.managers.LaunchPerformanceManager
import com.tez.perflab.ui.features.audiotest.AudioTestScreen
import com.tez.perflab.ui.features.jsontest.JsonTestScreen
import com.tez.perflab.ui.features.maptest.MapTestScreen
import com.tez.perflab.ui.features.storagetest.StorageTestScreen
import com.tez.perflab.ui.features.videotest.VideoTestScreen
import com.tez.perflab.ui.features.listtest.ListTestScreen
import com.tez.perflab.ui.features.sensortest.SensorTestScreen
import com.tez.perflab.ui.screens.MainDashboardScreen

/**
 * The primary declarative UI orchestrator and navigation hub for the benchmarking suite.
 *
 * The root composable is kept stateless. By delegating all reactive states to the
 * leaf nodes (individual screens), full-tree recompositions are prevented,
 * ensuring maximum CPU availability for the underlying benchmarks.
 */
@Composable
fun App() {
    MaterialTheme {
        Surface(color = MaterialTheme.colorScheme.background) {

            // LaunchedEffect(Unit) executes only after the initial Composition,
            // Layout, and Drawing phases have flushed to the screen.
            // This captures an accurate "Time to Interactive" (TTI) timestamp.
            LaunchedEffect(Unit) {
                LaunchPerformanceManager.reportBenchmark()
            }

            // Static Navigation Graph Initialization
            val navController = rememberNavController()

            NavHost(navController = navController, startDestination = "dashboard") {
                // Dashboard is the root; it doesn't need a back button.
                composable("dashboard") { MainDashboardScreen(navController) }

                // Benchmark leaf nodes receive a pop-backstack lambda for cross-platform navigation consistency.
                composable("video_test") { VideoTestScreen(onNavigateBack = { navController.popBackStack() }) }
                composable("audio_test") { AudioTestScreen(onNavigateBack = { navController.popBackStack() }) }
                composable("storage_test") { StorageTestScreen(onNavigateBack = { navController.popBackStack() }) }
                composable("json_test") { JsonTestScreen(onNavigateBack = { navController.popBackStack() }) }
                composable("map_test") { MapTestScreen(onNavigateBack = { navController.popBackStack() }) }
                composable("list_test") { ListTestScreen(onNavigateBack = { navController.popBackStack() }) }
                composable("sensor_test") { SensorTestScreen(onNavigateBack = { navController.popBackStack() }) }
            }
        }
    }
}