package com.tez.perflab.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tez.perflab.managers.LaunchPerformanceManager
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The primary entry point and routing hub for the benchmark suite.
 * Implements recomposition isolation to ensure high-frequency telemetry updates
 * do not invalidate the static navigation tree.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Benchmark Lab (KMP)", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->

        // Uses a scrollable Column instead of LazyColumn to minimize layout
        // initialization overhead during the critical Time To Interactive (TTI) phase.
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF2F2F7))
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            LaunchTelemetryDashboard()
            TestSuiteRows(navController)
        }
    }
}

/**
 * Displays real-time application launch metrics.
 * Uses localized state collection to minimize UI thread overhead.
 */
@Composable
private fun LaunchTelemetryDashboard() {
    val coldStartMs by LaunchPerformanceManager.totalColdStartMs.collectAsState()
    val osDurationMs by LaunchPerformanceManager.osDurationMs.collectAsState()
    val softwareDurationMs by LaunchPerformanceManager.softwareDurationMs.collectAsState()
    val hotStartMs by LaunchPerformanceManager.hotStartMs.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Speed, contentDescription = "Latency", tint = Color(0xFF5E5CE6))
            Text("System Latency Distribution", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        // --- COLD START TELEMETRY COMPONENT ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF32ADE6).copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = Color(0xFF32ADE6), modifier = Modifier.size(14.dp))
                    Text("Cold Start", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                }
                Text("Post-Zygote / Darwin Initialization", fontSize = 10.sp, color = Color.Gray)
            }

            Text(
                text = "${coldStartMs.formatTo1Decimal()} ms",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF5E5CE6)))
                    Text("OS Overhead: ${osDurationMs.formatTo1Decimal()} ms", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFFF2D55)))
                    Text("UI Rendering: ${softwareDurationMs.formatTo1Decimal()} ms", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                }
            }
        }

        // --- HOT START TELEMETRY COMPONENT ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFF9500).copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Restore, contentDescription = null, tint = Color(0xFFFF9500), modifier = Modifier.size(14.dp))
                    Text("Hot Start", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                }
                Text("State Restoration from RAM", fontSize = 10.sp, color = Color.Gray)
            }

            val hotStartValue = (hotStartMs as? Number)?.toDouble() ?: 0.0
            Text(
                text = if (hotStartValue > 0) "${hotStartMs.formatTo1Decimal()} ms" else "-- ms",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                color = if (hotStartValue > 0) MaterialTheme.colorScheme.onSurface else Color.Gray.copy(alpha = 0.4f)
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (hotStartValue > 0) {
                    Icon(Icons.Default.Memory, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(14.dp))
                    Text("Verified: Cached Process", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34C759))
                } else {
                    Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    Text("Awaiting Foreground Transition...", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
                }
            }
        }
    }
}

/**
 * Static routing rows for benchmark suites.
 */
@Composable
private fun TestSuiteRows(navController: NavController) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Active Benchmarking Suites",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 4.dp, top = 10.dp)
        )

        TestRowView("Video Playback (1080p/60fps)", Icons.Default.OndemandVideo, Color(0xFFFF3B30)) { navController.navigate("video_test") }
        TestRowView("Audio Playback (LPCM/FLAC)", Icons.Default.GraphicEq, Color(0xFFFF2D55)) { navController.navigate("audio_test") }
        TestRowView("I/O Storage Throughput", Icons.Default.Storage, Color.Gray) { navController.navigate("storage_test") }
        TestRowView("JSON Deserialization (10MB+)", Icons.Default.DataObject, Color(0xFF00B0FF)) { navController.navigate("json_test") }
        TestRowView("Map Surface Rendering", Icons.Default.Map, Color(0xFF34C759)) { navController.navigate("map_test") }
        TestRowView("Virtual List Performance", Icons.Default.ShoppingCart, Color(0xFF007AFF)) { navController.navigate("list_test") }
        TestRowView("Hardware Sensor Fusion", Icons.Default.Sensors, Color(0xFFFF9500)) { navController.navigate("sensor_test") }
    }
}

/**
 * Stateless UI primitive for navigation rows.
 */
@Composable
fun TestRowView(title: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(modifier = Modifier.size(32.dp).background(color, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.weight(1f))
        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray)
    }
}

/**
 * Formats a numerical input to exactly one decimal place without utilizing heavy
 * platform-specific formatters. Helps avoid string allocation churn during high-frequency updates.
 */
private fun Any?.formatTo1Decimal(): String {
    val number = (this as? Number)?.toDouble() ?: 0.0
    val integerPart = number.toInt()
    val decimalPart = ((number - integerPart) * 10).roundToInt()
    return "$integerPart.${abs(decimalPart)}"
}