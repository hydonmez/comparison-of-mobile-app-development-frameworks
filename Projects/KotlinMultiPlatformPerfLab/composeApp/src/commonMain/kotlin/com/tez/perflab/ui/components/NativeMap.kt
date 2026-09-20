package com.tez.perflab.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tez.perflab.models.MapPoint
import com.tez.perflab.ui.features.maptest.MapTestViewModel

/**
 * A cross-platform contract delegating geospatial rendering to the underlying OS.
 *
 * The shared declarative UI layer strictly dictates the layout geometry (via `Modifier`) 
 * and the reactive telemetry state (`cameraTarget`, `points`). The actual pixel-pushing, 
 * vector decoding, and tile caching are safely offloaded to the optimized, 
 * hardware-accelerated native SDKs (Google Maps via `AndroidView` and Apple Maps via `UIKitView`).
 */
@Composable
expect fun NativeMap(
    modifier: Modifier,
    cameraTarget: MapTestViewModel.CameraTarget,
    points: List<MapPoint>
)