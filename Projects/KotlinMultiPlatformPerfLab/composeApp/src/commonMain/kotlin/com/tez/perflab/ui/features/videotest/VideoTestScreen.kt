package com.tez.perflab.ui.features.videotest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tez.perflab.managers.PerformanceManager
import com.tez.perflab.managers.lockScreenOrientation
import com.tez.perflab.managers.rememberPlatformContext
import com.tez.perflab.ui.components.NativeVideoPlayer

/**
 * A highly optimized Compose Multiplatform UI layer for hardware-accelerated
 * video decoding benchmarks. Implements dynamic view routing to isolate the
 * high-frequency, full-screen rendering surface from the idle preparation state.
 */
@Composable
fun VideoTestScreen(
    viewModel: VideoTestViewModel = viewModel { VideoTestViewModel() },
    onNavigateBack: () -> Unit
) {
    val context = rememberPlatformContext()

    // Utilizing lifecycle-aware collection to prevent ghost emissions during active telemetry
    val isFullScreen by viewModel.isFullScreen.collectAsStateWithLifecycle()
    val isReportReady by viewModel.isReportReady.collectAsStateWithLifecycle()

    // LIFECYCLE MANAGEMENT: Asynchronous Decoder Initialization
    LaunchedEffect(Unit) {
        viewModel.prepareVideo(context)
    }

    // Delegates display matrix management to the native platform implementations
    // to ensure stable conditions during the benchmarking sequence.
    LaunchedEffect(isFullScreen) {
        lockScreenOrientation(context, isLandscape = isFullScreen)
    }

    // LIFECYCLE MANAGEMENT: Execution Firewall & State Restoration
    DisposableEffect(Unit) {
        onDispose {
            // Failsafe restoration of the system UI and orientation on unexpected exit
            lockScreenOrientation(context, isLandscape = false)
            viewModel.stopTest(isFinished = false)
        }
    }

    // DECLARATIVE VIEW ROUTING: Dynamic state-based surface switching
    if (isFullScreen) {
        ActiveVideoBenchmarkOverlay(viewModel)
    } else {
        IdlePreparationUI(
            onStart = { viewModel.startTest(context) },
            onNavigateBack = onNavigateBack,
            isReportReady = isReportReady,
            onExport = { viewModel.shareResults(context) }
        )
    }
}

/**
 * Provides a dedicated, high-priority rendering context for the native video pipeline
 * while strictly separating telemetry overlays from the underlying hardware surface.
 */
@Composable
private fun ActiveVideoBenchmarkOverlay(viewModel: VideoTestViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Binds the platform-specific playback instance (ExoPlayer/AVPlayer)
        // directly to the native rendering view.
        NativeVideoPlayer(
            player = viewModel.engine.getPlayer(),
            modifier = Modifier.fillMaxSize()
        )

        // CONTROL OVERLAY: Direct manual termination trigger
        Button(
            onClick = { viewModel.stopTest(isFinished = false) },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.85f)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 50.dp, start = 20.dp)
        ) {
            Text("Terminate Benchmark", color = Color.White, fontSize = 12.sp)
        }

        FPSIndicator(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 50.dp, end = 20.dp)
        )
    }
}

/**
 * Visualizes VSYNC-synchronized frame frequency provided by the [PerformanceManager].
 */
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

/**
 * Initial configuration state for benchmark preparation and post-test data export.
 */
@Composable
private fun IdlePreparationUI(
    onStart: () -> Unit,
    onNavigateBack: () -> Unit,
    isReportReady: Boolean,
    onExport: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 16.dp, start = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Navigate Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Video Benchmark",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

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
                text = "The video plays from start to finish while hardware telemetry is recorded. The display will be locked to landscape orientation for high-fidelity decoding.",
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Conditional Data Persistence Access
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
}