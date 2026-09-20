package com.tez.perflab.models

import kotlinx.serialization.Serializable

/**
 * A lightweight Data Transfer Object (DTO) representing a distinct geographical annotation.
 * Designed for cross-platform map benchmarking.
 */
@Serializable
data class MapPoint(
    /**
     * A sequential integer uniquely identifying the spatial coordinate.
     * Using an `Int` instead of a UUID avoids cryptographic allocation overhead,
     * ensuring the benchmark strictly measures the map rendering pipeline.
     */
    val id: Int,

    /**
     * The precise spatial latitude coordinate.
     * Flattened to primitive Doubles in `commonMain` to avoid the interop bridge overhead
     * of platform-specific geospatial classes until necessary at the UI layer.
     */
    val latitude: Double,

    /**
     * The precise spatial longitude coordinate.
     */
    val longitude: Double,

    /**
     * A descriptive label associated with the geographic coordinate.
     */
    val title: String,

    /**
     * Default zoom level used to synchronize map scales across platforms.
     */
    val zoomLevel: Float = 14f
)