package com.tez.nativekotlinperflabapp.ui.features.audiotest

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tez.nativekotlinperflabapp.core.managers.PerformanceManager

/**
 * The user interface for the native audio test.
 * Uses native Compose ImageVectors instead of legacy Android drawables
 * to avoid unnecessary memory overhead during UI updates.
 */
@Composable
fun AudioTestScreen(
    viewModel: AudioTestViewModel = viewModel()
) {
    val context = LocalContext.current

    val isTesting by viewModel.isTesting.collectAsStateWithLifecycle()
    val showShareSheet by viewModel.showShareSheet.collectAsStateWithLifecycle()
    val exportUri by viewModel.exportUri.collectAsStateWithLifecycle()
    val isAudioLoaded by viewModel.isAudioLoaded.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    val totalDuration by viewModel.totalDuration.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadTestAudio(context)
    }

    // Handles the automatic share intent when the test completes or stops.
    LaunchedEffect(showShareSheet) {
        if (showShareSheet && exportUri != null) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, exportUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Export Benchmark Data"))
            viewModel.resetShareSheet() // Prevent infinite loop
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopTest(isFinished = false) }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(top = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(25.dp)
    ) {

        // 1. Media Artwork Placeholder
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(250.dp)
                .shadow(
                    elevation = 15.dp,
                    shape = RoundedCornerShape(20.dp),
                    spotColor = Color(0xFFFFC0CB)
                )
                .background(
                    brush = Brush.linearGradient(listOf(Color(0xFFFFA500), Color(0xFFFFC0CB))),
                    shape = RoundedCornerShape(20.dp)
                )
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Artwork Placeholder",
                modifier = Modifier.size(100.dp),
                tint = Color.White
            )
        }

        // 2. Title & Error Reporting
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (errorMessage != null) {
                Text(
                    text = "⚠️ Error: $errorMessage",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .background(Color.Red, RoundedCornerShape(8.dp))
                        .padding(16.dp)
                )
            } else {
                Text(
                    text = "Native Audio Benchmark",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Engine: Media3 (Optimized)",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(6.dp)
                )
            }
        }

        // 3. Playback Timeline & Scrubber
        Column(
            modifier = Modifier.padding(horizontal = 30.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val sliderPosition by viewModel.currentTime.collectAsStateWithLifecycle()

            // Safe fallback logic to prevent UI crashes if the hardware decoder
            // returns uninitialized timestamp values (Float.NaN) during startup.
            val safePosition = if (sliderPosition.isNaN() || sliderPosition.isInfinite()) 0f
            else sliderPosition.toFloat()

            val safeDuration = if (totalDuration.isNaN() || totalDuration.isInfinite() || totalDuration <= 0.0) 1f
            else totalDuration.toFloat()

            Slider(
                value = safePosition,
                onValueChange = { viewModel.seekAudio(it.toDouble()) },
                valueRange = 0f..safeDuration,
                enabled = isAudioLoaded,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFFA500),
                    activeTrackColor = Color(0xFFFFA500)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = viewModel.formatTime(sliderPosition),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = viewModel.formatTime(totalDuration),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // 4. Transport Controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.skip(-15.0) }, enabled = isAudioLoaded) {
                Icon(
                    imageVector = Icons.Default.FastRewind,
                    contentDescription = "Skip Back",
                    modifier = Modifier.size(40.dp),
                    tint = if (isAudioLoaded) MaterialTheme.colorScheme.primary else Color.Gray
                )
            }

            IconButton(
                onClick = { if (isPlaying) viewModel.stopTest(isFinished = false) else viewModel.startTest(context) },
                enabled = isAudioLoaded,
                modifier = Modifier.size(80.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(80.dp),
                    tint = if (isAudioLoaded) Color(0xFFFFA500) else Color.Gray
                )
            }

            IconButton(onClick = { viewModel.skip(15.0) }, enabled = isAudioLoaded) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = "Skip Forward",
                    modifier = Modifier.size(40.dp),
                    tint = if (isAudioLoaded) MaterialTheme.colorScheme.primary else Color.Gray
                )
            }
        }

        // 5. Hardware Telemetry Overlay
        TelemetryHUDLayer(isTesting = isTesting)

        Spacer(Modifier.weight(1f))
    }
}

/**
 * An isolated composable that directly observes telemetry streams.
 * Bypassing ViewModel allocations helps maintain smooth, lightweight updates.
 */
@Composable
private fun TelemetryHUDLayer(isTesting: Boolean) {
    val currentFPS by PerformanceManager.currentFPS.collectAsStateWithLifecycle()
    val thermalState by PerformanceManager.currentThermalState.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "FPS: $currentFPS",
                fontWeight = FontWeight.Bold,
                color = if (currentFPS < 50) Color.Red else Color.Green,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Thermal: $thermalState",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.weight(1f))

        if (isTesting) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .background(Color.Red.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                CircularProgressIndicator(
                    color = Color.Red,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "REC",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red
                )
            }
        }
    }
}