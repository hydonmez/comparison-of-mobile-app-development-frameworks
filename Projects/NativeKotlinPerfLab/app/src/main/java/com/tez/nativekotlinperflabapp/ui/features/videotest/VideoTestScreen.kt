package com.tez.nativekotlinperflabapp.ui.features.videotest

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import com.tez.nativekotlinperflabapp.core.managers.PerformanceManager

/**
 * A dedicated presentation layer that isolates hardware-accelerated decoding
 * from high-frequency UI updates.
 */
@Composable
fun VideoTestScreen(
    viewModel: VideoTestViewModel = viewModel()
) {
    val context = LocalContext.current

    // --- Lifecycle-Aware State Observation ---
    val isFullScreen by viewModel.isFullScreen.collectAsStateWithLifecycle()
    val exportUri by viewModel.exportUri.collectAsStateWithLifecycle()

    // Observes the hardware decoder's end-of-file (EOF) signal to terminate the test smoothly.
    val hasVideoEnded by viewModel.engine.hasEnded.collectAsStateWithLifecycle()

    // Safely inject the context to prepare the video asset.
    LaunchedEffect(Unit) {
        viewModel.prepareVideo(context)
    }

    // Automatically triggers finalization when decoding finishes.
    LaunchedEffect(hasVideoEnded) {
        if (hasVideoEnded && !isFullScreen) {
            viewModel.stopTest(context = context, isFinished = true)
        }
    }

    // --- Hardware Orientation & Immersive Mode Logic ---
    LaunchedEffect(isFullScreen) {
        val currentActivity = context as? Activity
        val currentWindow = currentActivity?.window

        if (isFullScreen && currentActivity != null && currentWindow != null) {
            // Forces landscape orientation during the benchmark for consistency.
            currentActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            WindowCompat.setDecorFitsSystemWindows(currentWindow, false)
            val controller = WindowInsetsControllerCompat(currentWindow, currentWindow.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else if (currentActivity != null && currentWindow != null) {
            // Restores portrait lock post-benchmark.
            currentActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            WindowCompat.setDecorFitsSystemWindows(currentWindow, true)
            val controller = WindowInsetsControllerCompat(currentWindow, currentWindow.decorView)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val currentActivity = context as? Activity
            currentActivity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            viewModel.stopTest(context = context, isFinished = false)
        }
    }

    // --- DECLARATIVE VIEW ROUTING ---
    if (isFullScreen) {
        ActiveVideoBenchmarkOverlay(
            viewModel = viewModel,
            onTerminate = { viewModel.stopTest(context = context, isFinished = false) }
        )
    } else {
        IdlePreparationUI(
            onStart = { viewModel.startTest(context) },
            isReportReady = exportUri != null,
            onExport = { viewModel.shareResults(context) }
        )
    }
}

/**
 * Encapsulates the native PlayerView to prevent parent layouts from invalidating
 * during active hardware decoding.
 */
@Composable
private fun ActiveVideoBenchmarkOverlay(
    viewModel: VideoTestViewModel,
    onTerminate: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = viewModel.engine.player
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Terminate Button
        Button(
            onClick = onTerminate,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.85f)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 50.dp, start = 20.dp)
        ) {
            Text("Terminate Benchmark", color = Color.White, fontSize = 12.sp)
        }

        // Telemetry HUD
        FPSIndicator(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 50.dp, end = 20.dp)
        )
    }
}

@Composable
fun FPSIndicator(modifier: Modifier = Modifier) {
    val currentFPS by PerformanceManager.currentFPS.collectAsStateWithLifecycle()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Text(
            text = "FPS: $currentFPS",
            color = if (currentFPS < 50) Color.Red else Color.Green,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun IdlePreparationUI(
    onStart: () -> Unit,
    isReportReady: Boolean,
    onExport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = Color(0xFF007AFF)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text("Full Screen Video Benchmark", fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "The video plays from start to finish while hardware telemetry is recorded. The screen will lock to landscape orientation.",
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        if (isReportReady) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text("Benchmark Report Ready", fontWeight = FontWeight.Bold)

                Button(
                    onClick = onExport,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.SystemUpdateAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Results (CSV)")
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(56.dp)
        ) {
            Text("START FULL SCREEN BENCHMARK", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}