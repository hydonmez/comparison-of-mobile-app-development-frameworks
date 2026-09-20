package com.tez.perflab.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.tez.perflab.models.MapPoint
import com.tez.perflab.ui.features.maptest.MapTestViewModel
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.*
import kotlin.math.pow

/**
 * iOS-specific implementation of the native map rendering surface.
 * 
 * This component bridges the Compose Multiplatform UI tree to Apple's MapKit (MKMapView).
 * It translates logarithmic camera coordinates into Apple's geographic span matrix, 
 * ensuring consistent visual framing across platforms during telemetry collection.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun NativeMap(
    modifier: Modifier,
    cameraTarget: MapTestViewModel.CameraTarget,
    points: List<MapPoint>
) {
    // Uses remember for a non-observable value storage to prevent recomposition loops.
    val lastState = remember { object {
        var pointsHash: Int = 0
        var lastLat: Double = 0.0
        var lastLon: Double = 0.0
        var lastZoom: Float = 0f
    } }

    UIKitView(
        modifier = modifier,
        factory = {
            MKMapView().apply {
                mapType = MKMapTypeStandard
                showsCompass = false
                zoomEnabled = false
                scrollEnabled = false
                pitchEnabled = false
                rotateEnabled = false

                // Enables points of interest to match Android's default Google Maps behavior.
                // This ensures the iOS GPU rasterizes a similar level of vector complexity.
                showsPointsOfInterest = true

                // Matches Android's `isBuildingEnabled = false` (Flat 2D rendering)
                showsBuildings = false
            }
        },
        update = { mapView ->
            // 1. Annotation Updates
            val currentHash = points.hashCode()
            if (currentHash != lastState.pointsHash) {
                val existing = mapView.annotations.filterIsInstance<MKPointAnnotation>()
                mapView.removeAnnotations(existing)

                val newAnnotations = points.map { point ->
                    MKPointAnnotation().apply {
                        setCoordinate(CLLocationCoordinate2DMake(point.latitude, point.longitude))
                    }
                }
                mapView.addAnnotations(newAnnotations)
                lastState.pointsHash = currentHash
            }

            // 2. Camera Region Updates
            // Region is only updated if the camera target has actually moved
            // to avoid CPU/GPU jitter during high-frequency telemetry polling.
            if (lastState.lastLat != cameraTarget.latitude ||
                lastState.lastLon != cameraTarget.longitude ||
                lastState.lastZoom != cameraTarget.zoom) {

                val spanDelta = 360.0 / 2.0.pow(cameraTarget.zoom.toDouble())
                val center = CLLocationCoordinate2DMake(cameraTarget.latitude, cameraTarget.longitude)
                val region = MKCoordinateRegionMake(center, MKCoordinateSpanMake(spanDelta, spanDelta))

                mapView.setRegion(region, animated = true)

                lastState.lastLat = cameraTarget.latitude
                lastState.lastLon = cameraTarget.longitude
                lastState.lastZoom = cameraTarget.zoom
            }
        }
    )
}