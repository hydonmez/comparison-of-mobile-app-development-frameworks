package com.tez.nativekotlinperflabapp.ui.features.jsontest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Memory
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * The presentation layer for the JSON deserialization test.
 * Isolates UI recomposition from background CPU operations to ensure stable performance.
 */
@Composable
fun JsonTestScreen(
    viewModel: JsonTestViewModel = viewModel()
) {
    val context = LocalContext.current

    // --- State Observation ---
    val status by viewModel.status.collectAsStateWithLifecycle()
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val isReportReady by viewModel.isReportReady.collectAsStateWithLifecycle()
    val parsedCount by viewModel.parsedCount.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(25.dp)
    ) {

        // --- Header Section ---
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DataObject,
                contentDescription = "JSON Parsing",
                modifier = Modifier.size(50.dp),
                tint = Color(0xFF9C27B0)
            )

            Text(
                text = "10MB+ Data Deserialization",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // --- Status HUD ---
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
                    color = Color(0xFF34C759),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // --- Execution Trigger ---
        Button(
            // Explicitly passes Context to the ViewModel.
            onClick = { viewModel.startBenchmark(context) },
            enabled = !isRunning,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start JSON Parsing Benchmark", fontWeight = FontWeight.Bold)
        }

        // --- Telemetry Export ---
        if (isReportReady) {
            Button(
                onClick = { viewModel.exportResults(context) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
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