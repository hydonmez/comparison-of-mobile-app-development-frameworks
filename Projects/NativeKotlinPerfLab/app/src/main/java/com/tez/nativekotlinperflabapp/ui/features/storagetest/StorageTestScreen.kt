package com.tez.nativekotlinperflabapp.ui.features.storagetest

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * A dedicated presentation layer for orchestrating heavy I/O storage benchmarks.
 * Optimized with decoupled Context parameters to prevent Main Thread slowdowns
 * during large file write cycles.
 */
@Composable
fun StorageTestScreen(
    viewModel: StorageTestViewModel = viewModel()
) {
    val context = LocalContext.current

    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val isWriteCompleted by viewModel.isWriteCompleted.collectAsStateWithLifecycle()
    val isReportReady by viewModel.isReportReady.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(25.dp)
    ) {

        // --- Benchmark Status HUD ---
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                modifier = Modifier.size(50.dp),
                tint = Color(0xFFFF9500)
            )

            Text(
                text = status,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // --- Progress Visualization ---
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            val currentProgress by viewModel.progress.collectAsStateWithLifecycle()

            LinearProgressIndicator(
                progress = { currentProgress.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = Color(0xFFFF9500),
                trackColor = Color.LightGray.copy(alpha = 0.5f)
            )

            Text(
                text = "${(currentProgress * 100).toInt()}%",
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // --- Execution Controls ---
        Column(
            verticalArrangement = Arrangement.spacedBy(15.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Button(
                onClick = { viewModel.runBenchmark(context, isWrite = true) },
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9500)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                Icon(Icons.Default.Create, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Step 1: Write 2GB Payload", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { viewModel.runBenchmark(context, isWrite = false) },
                enabled = !isRunning && isWriteCompleted,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFFFF9500),
                    disabledContainerColor = Color.Transparent
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (!isRunning && isWriteCompleted) Color(0xFFFF9500) else Color.LightGray
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Step 2: Read 2GB Payload", fontWeight = FontWeight.Bold)
            }
        }

        // --- Telemetry Export ---
        if (isReportReady && !isRunning) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .background(Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text("Benchmark Report Ready", fontWeight = FontWeight.Bold)

                Button(
                    onClick = { viewModel.exportResults(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.SystemUpdateAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Results")
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}