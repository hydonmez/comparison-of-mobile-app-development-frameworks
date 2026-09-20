package com.tez.perflab.ui.features.sensortest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tez.perflab.managers.rememberPlatformContext
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.pow
import kotlin.math.round

/**
 * Orchestrates the visualization of high-frequency (100Hz) sensor telemetry.
 *
 * CPU Optimizations:
 * 1. SCOPED RECOMPOSITION: Pushed `StateFlow` observation down to the leaf components
 * (`SensorInfoCard`, `PedometerCard`). This prevents top-level UI recomposition,
 * saving the CPU from redrawing static elements (buttons, headers) 16 times a second.
 * 2. DEFERRED READS: Bound the progress indicator using a lambda `progressState.value`
 * instead of a property delegate (`by`) to bypass parent layout invalidations.
 * 3. ALLOCATION-FREE MATH: Replaced heavy `String.split()` operations in
 * `Float.format()` with pure modulo arithmetic, eliminating GC churn and CPU jitter.
 */
@Composable
fun SensorTestScreen(
    viewModel: SensorTestViewModel = viewModel { SensorTestViewModel() },
    onNavigateBack: () -> Unit
) {
    val context = rememberPlatformContext()
    val scrollState = rememberScrollState()

    // Read only low-frequency state at the top level
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val isReportReady by viewModel.isReportReady.collectAsStateWithLifecycle()

    // Read state object directly to defer value extraction inside the lambda
    val progressState = viewModel.progress.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopTest(isFinished = false)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(25.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                text = "Sensor Benchmark",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(15.dp)) {
            Text(
                text = status,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (isRunning) Color(0xFFFF9500) else Color.Gray
            )

            if (isRunning) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    LinearProgressIndicator(
                        // Deferred Read: Prevents the parent Column from recomposing
                        progress = { progressState.value.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFFFF9500),
                        trackColor = Color.LightGray
                    )
                }
            }
        }

        // --- Telemetry Cards (State Hoisted) ---
        Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
            SensorInfoCard(Icons.Default.OpenInFull, "Accelerometer (G-Force)", viewModel.accelData, Color(0xFF007AFF))
            SensorInfoCard(Icons.Default.Sync, "Gyroscope (Rad/s)", viewModel.gyroData, Color(0xFF34C759))
            SensorInfoCard(Icons.Default.Explore, "Magnetometer (µT)", viewModel.magnetData, Color(0xFFFF3B30))
            PedometerCard(viewModel.stepCount)
        }

        // --- Execution Controls ---
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    if (isRunning) viewModel.stopTest(isFinished = false) else viewModel.startTest(context)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) Color.Red else Color(0xFFFF9500))
            ) {
                Icon(if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(if (isRunning) "Stop Benchmark" else "Start Sensor Test", fontWeight = FontWeight.Bold)
            }

            if (isReportReady && !isRunning) {
                Button(
                    onClick = { viewModel.shareResults(context) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)), // Visual consistency
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.SystemUpdateAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Export Results (CSV)", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── Sub-Composables (Recomposition Boundaries) ──────────────────────────────

/**
 * Observes the StateFlow internally. High-frequency signals only redraw
 * this specific block, saving CPU cycles on static elements.
 */
@Composable
fun SensorInfoCard(icon: ImageVector, title: String, dataFlow: StateFlow<FloatArray>, color: Color) {
    val data by dataFlow.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxWidth().background(Color(0xFFF2F2F7), RoundedCornerShape(12.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            SensorValueText("X", if (data.isNotEmpty()) data[0] else 0f, Modifier.weight(1f))
            SensorValueText("Y", if (data.size > 1) data[1] else 0f, Modifier.weight(1f))
            SensorValueText("Z", if (data.size > 2) data[2] else 0f, Modifier.weight(1f))
        }
    }
}

@Composable
fun PedometerCard(stepFlow: StateFlow<Int>) {
    val stepCount by stepFlow.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier.fillMaxWidth().background(Color(0xFFF2F2F7), RoundedCornerShape(12.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Pedometer (Step Count)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5856D6))
            Text("$stepCount", fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
        Spacer(modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null, modifier = Modifier.size(32.dp), tint = Color(0xFF5856D6))
    }
}

@Composable
fun SensorValueText(label: String, value: Float, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = Color.Gray)
        Text(
            text = value.format(3),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

// ─── Allocation-Free Extensions ───────────────────────────────────────────────

/**
 * Eliminates `String.split()` and its underlying Regex/ArrayList allocations.
 * Uses pure modulo arithmetic to extract fractional digits, ensuring UI string
 * generation remains O(1) in memory allocation during continuous 100Hz hardware hooks.
 */
fun Float.format(digits: Int): String {
    val multiplier = 10.0.pow(digits).toInt()
    val rounded = round(this * multiplier).toInt()
    val integerPart = rounded / multiplier
    val fractionalPart = kotlin.math.abs(rounded % multiplier)
    val fracStr = fractionalPart.toString().padStart(digits, '0')
    return "$integerPart.$fracStr"
}