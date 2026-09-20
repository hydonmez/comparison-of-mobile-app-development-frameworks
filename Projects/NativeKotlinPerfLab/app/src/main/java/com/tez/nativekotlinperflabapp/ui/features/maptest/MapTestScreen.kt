package com.tez.nativekotlinperflabapp.ui.features.maptest

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.maps.android.compose.*

/**
 * The presentation layer for geospatial rendering tests.
 * Isolates UI state collection from high-frequency GPU rendering tasks.
 */
@Composable
fun MapTestScreen(
    viewModel: MapTestViewModel = viewModel()
) {
    val context = LocalContext.current

    // Lifecycle-aware state observation
    val status by viewModel.status.collectAsStateWithLifecycle()
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val isReportReady by viewModel.isReportReady.collectAsStateWithLifecycle()
    val points by viewModel.points.collectAsStateWithLifecycle()
    val targetCameraPosition by viewModel.cameraPosition.collectAsStateWithLifecycle()

    val cameraPositionState = rememberCameraPositionState {
        position = targetCameraPosition
    }

    // Hardware-accelerated camera animation
    LaunchedEffect(targetCameraPosition) {
        cameraPositionState.animate(
            update = CameraUpdateFactory.newCameraPosition(targetCameraPosition),
            durationMs = 1500
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopTest(isFinished = false)
        }
    }

    // Configures map properties to maintain consistent rendering conditions.
    val mapProperties = remember {
        MapProperties(
            isBuildingEnabled = false,
            isIndoorEnabled = false,
            isTrafficEnabled = false,
            mapType = MapType.NORMAL
        )
    }

    // Disables manual interactions during the automated test.
    val uiSettings = remember {
        MapUiSettings(
            scrollGesturesEnabled = false,
            zoomGesturesEnabled = false,
            tiltGesturesEnabled = false,
            rotationGesturesEnabled = false,
            compassEnabled = false,
            myLocationButtonEnabled = false
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {

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

        Box(modifier = Modifier.weight(1f)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = mapProperties,
                uiSettings = uiSettings
            ) {
                points.forEach { point ->
                    // Uses spatial keys to prevent unnecessary recomposition.
                    key(point.coordinate.latitude, point.coordinate.longitude) {
                        val markerState = rememberMarkerState(position = point.coordinate)
                        Marker(
                            state = markerState,
                            title = point.title
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            Button(
                onClick = { viewModel.startTest(context) },
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
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

            if (isReportReady && !isRunning) {
                Button(
                    onClick = { viewModel.exportResults(context) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
                    shape = RoundedCornerShape(12.dp),
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