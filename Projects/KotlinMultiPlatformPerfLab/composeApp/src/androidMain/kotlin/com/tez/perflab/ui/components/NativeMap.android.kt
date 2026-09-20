package com.tez.perflab.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.tez.perflab.models.MapPoint
import com.tez.perflab.ui.features.maptest.MapTestViewModel

/**
 * Platform-specific Google Maps implementation for Android using Compose Multiplatform.
 * Interactive UI elements are disabled to focus purely on rendering and camera animations.
 */
@Composable
actual fun NativeMap(
    modifier: Modifier,
    cameraTarget: MapTestViewModel.CameraTarget,
    points: List<MapPoint>
) {
    val targetPosition = remember(cameraTarget) {
        LatLng(cameraTarget.latitude, cameraTarget.longitude)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(targetPosition, cameraTarget.zoom)
    }

    LaunchedEffect(cameraTarget) {
        val newPos = CameraPosition.fromLatLngZoom(targetPosition, cameraTarget.zoom)
        cameraPositionState.animate(
            update = CameraUpdateFactory.newCameraPosition(newPos)
        )
    }

    val mapProperties = remember {
        MapProperties(
            isBuildingEnabled = false,
            isIndoorEnabled = false,
            isTrafficEnabled = false,
            mapType = MapType.NORMAL
        )
    }

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

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = mapProperties,
        uiSettings = uiSettings
    ) {
        points.forEach { point ->
            // Uses Compose key tracking to prevent redundant marker recompositions.
            key(point.latitude, point.longitude) {
                val markerState = rememberMarkerState(position = LatLng(point.latitude, point.longitude))
                Marker(
                    state = markerState,
                    title = point.title
                )
            }
        }
    }
}