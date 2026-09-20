package com.tez.perflab.ui.features.jsontest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Memory
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tez.perflab.managers.rememberPlatformContext

/**
 * A cross-platform presentation layer for evaluating JSON deserialization performance.
 * Decoupled from the synchronous parsing loop to prevent UI jank and ensure
 * consistent CPU allocation to the benchmark engine.
 */
@Composable
fun JsonTestScreen(
    viewModel: JsonTestViewModel = viewModel { JsonTestViewModel() },
    onNavigateBack: () -> Unit
) {
    val context = rememberPlatformContext()

    // ─── State Observation ─────────────────────────────────────────────────────
    val status by viewModel.status.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val parsedCount by viewModel.parsedCount.collectAsState()

    // Represents the readiness of the CSV report
    val isReportReady by viewModel.isReportReady.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // --- CROSS-PLATFORM NAVIGATION HEADER ---
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
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(25.dp)
        ) {

            // ─── Header Section ──────────────────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DataObject,
                    contentDescription = "JSON Parsing",
                    modifier = Modifier.size(50.dp),
                    tint = Color(0xFF9C27B0) // Purple for visual consistency
                )

                Text(
                    text = "10MB+ Data Deserialization",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // ─── Status HUD ──────────────────────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(15.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = status,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp)
                )

                if (parsedCount > 0) {
                    Text(
                        text = "$parsedCount GitHub events processed",
                        fontSize = 12.sp,
                        color = Color(0xFF34C759), // Success Green
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // ─── Benchmark Execution Trigger ─────────────────────────────────────────
            Button(
                onClick = { viewModel.startBenchmark(context) },
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                // Removed the CircularProgressIndicator for a clean, static look.
                Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start JSON Parsing Benchmark", fontWeight = FontWeight.Bold)
            }

            // ─── Telemetry Export ────────────────────────────────────────────────────
            // The export button is displayed only when the report is ready.
            if (isReportReady) {
                Button(
                    onClick = { viewModel.shareResults(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)), // iOS Blue
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    Icon(Icons.Default.SystemUpdateAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Results (CSV)", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}