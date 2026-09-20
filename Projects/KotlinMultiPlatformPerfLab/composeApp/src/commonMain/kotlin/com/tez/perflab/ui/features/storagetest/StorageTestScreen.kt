package com.tez.perflab.ui.features.storagetest

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tez.perflab.managers.rememberPlatformContext

/**
 * A cross-platform declarative presentation layer for visualizing 
 * sustained sequential disk throughput on Android and iOS.
 */
@Composable
fun StorageTestScreen(
    viewModel: StorageTestViewModel = viewModel { StorageTestViewModel() },
    onNavigateBack: () -> Unit
) {
    val context = rememberPlatformContext()

    // ─── State Observation ───
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val isWriteCompleted by viewModel.isWriteCompleted.collectAsStateWithLifecycle()
    val isReportReady by viewModel.isReportReady.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // --- Navigation Header ---

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 16.dp, start = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { if (!isRunning) onNavigateBack() },
                enabled = !isRunning
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Navigate Back",
                    tint = if (isRunning) Color.Gray else MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Storage Benchmark",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(25.dp)
        ) {

            // MARK: - Benchmark Status HUD
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = "Storage Status",
                    modifier = Modifier.size(50.dp),
                    tint = Color(0xFFFF9500)
                )

                Text(
                    text = status,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // MARK: - Progress Visualization
            IsolatedProgressView(viewModel = viewModel)

            // MARK: - Execution Controls
            Column(
                verticalArrangement = Arrangement.spacedBy(15.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Step 1: Write Button
                Button(
                    onClick = { viewModel.runBenchmark(context, isWrite = true) },
                    enabled = !isRunning,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9500),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFFFF9500).copy(alpha = 0.5f),
                        disabledContentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Icon(Icons.Default.Create, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Step 1: Write 2GB Payload", fontWeight = FontWeight.Bold)
                }

                // Step 2: Read Button
                val isReadEnabled = !isRunning && isWriteCompleted
                Button(
                    onClick = { viewModel.runBenchmark(context, isWrite = false) },
                    enabled = isReadEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color(0xFFFF9500),
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor = Color.Gray
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isReadEnabled) Color(0xFFFF9500) else Color.Gray.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Step 2: Read 2GB Payload", fontWeight = FontWeight.Bold)
                }
            }

            // MARK: - Telemetry Export Pipeline
            if (isReportReady && !isRunning) {
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
                        onClick = { viewModel.shareResults(context) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF34C759),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Results")
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Isolated composable to prevent high-frequency UI updates from
 * redrawing the static layout tree.
 */
@Composable
private fun IsolatedProgressView(viewModel: StorageTestViewModel) {
    val currentProgress by viewModel.progress.collectAsStateWithLifecycle()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        LinearProgressIndicator(
            progress = { currentProgress.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp), // Set height to 8.dp for visual scaling
            color = Color(0xFFFF9500),
            trackColor = Color.LightGray.copy(alpha = 0.3f),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )

        Text(
            text = "${(currentProgress * 100).toInt()}%",
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.Gray
        )
    }
}