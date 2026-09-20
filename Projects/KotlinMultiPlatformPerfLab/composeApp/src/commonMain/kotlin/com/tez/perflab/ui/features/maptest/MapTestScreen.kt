package com.tez.perflab.ui.features.maptest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tez.perflab.managers.rememberPlatformContext
import com.tez.perflab.ui.components.NativeMap

/**
 * A cross-platform Jetpack Compose interface orchestrating the native map
 * rendering bridge (Google Maps / Apple Maps). Engineered to isolate telemetry
 * updates from the high-frequency GPU rendering cycles of the map surface.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapTestScreen(
    viewModel: MapTestViewModel = viewModel { MapTestViewModel() },
    onNavigateBack: () -> Unit
) {
    val context = rememberPlatformContext()

    // ─── State Observation ───
    val status by viewModel.status.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val points by viewModel.points.collectAsState()
    val cameraTarget by viewModel.cameraTarget.collectAsState()
    val isReportReady by viewModel.isReportReady.collectAsState()

    // ─── Lifecycle Management ───
    // Mandates synchronous teardown of the telemetry loops upon unmounting.
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopTest(isFinished = false)
        }
    }

    // UI Orchestration via Scaffold
    // Replaces the custom Row with a standardized Scaffold and TopAppBar,
    // effectively eliminating the layout rendering gaps caused by indiscriminate safeDrawing insets.
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Map Performance Benchmark",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->

        // Contextual padding handles Top/Bottom safe areas automatically,
        // eliminating the empty UI gaps.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {

            // ─── Telemetry Status Bar ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = status,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // ─── Native Rendering Bridge ───
            // Delegates actual pixel-pushing to the OS-level mapping SDKs.
            Box(modifier = Modifier.weight(1f)) {
                NativeMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraTarget = cameraTarget,
                    points = points
                )
            }

            // ─── Control Interface ───
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    // Restricts safe area padding exclusively to the bottom gesture navigation bar
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp)
            ) {

                // Primary Execution Trigger
                Button(
                    onClick = { viewModel.startTest(context) },
                    enabled = !isRunning,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)), // Visual consistency
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.AirplanemodeActive else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRunning) "Benchmark in Progress..." else "Start Automated Tour",
                        fontWeight = FontWeight.Bold
                    )
                }

                // Manual CSV export trigger
                if (isReportReady) {
                    Button(
                        onClick = { viewModel.shareResults(context) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)), // Visual consistency
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdateAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Export Telemetry (CSV)",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}