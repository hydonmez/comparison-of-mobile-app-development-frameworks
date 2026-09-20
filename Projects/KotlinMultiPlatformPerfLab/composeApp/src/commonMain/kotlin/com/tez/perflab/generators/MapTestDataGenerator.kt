package com.tez.perflab.generators

import com.tez.perflab.models.MapPoint
import kotlin.math.PI
import kotlin.math.sin

/**
 * A stateless, thread-safe namespace for generating deterministic spatial datasets.
 * Creates standardized baselines for testing rendering performance and spatial
 * indexing across mapping engines (e.g., MapKit vs. Google Maps).
 *
 * Executes synchronously to eliminate the CPU overhead of coroutine context
 * switching, ensuring accurate performance measurements.
 */
object MapTestDataGenerator {

    /**
     * Generates a spatially distributed array of map annotations using trigonometric distribution.
     * Executes synchronously to prioritize mathematical throughput over thread dispatching latency.
     *
     * @param count The total number of map points to generate. Defaults to 20.
     * @return A pre-allocated list of initialized MapPoint entities.
     */
    fun generatePoints(count: Int = 20): List<MapPoint> {

        // Base coordinates anchored to Istanbul for localized spatial rendering tests.
        val centerLat = 41.0082
        val centerLon = 28.9784
        val spread = 0.05

        // Default zoom level to synchronize map scales across platforms.
        val defaultZoom = 14f

        // Explicitly instantiating a fixed-size List circumvents the CPU overhead
        // of dynamic array resizing, isolating the benchmark to mathematical throughput.
        return List(count) { i ->
            val progress = i.toDouble() / count.toDouble()

            // Calculates linear latitude progression and sine-wave based longitude oscillation.
            val latOffset = (progress - 0.5) * spread * 2.0
            val lonOffset = sin(progress * PI * 4.0) * spread

            MapPoint(
                // Uses integer IDs to eliminate UUID generation overhead during benchmarking.
                id = i,
                latitude = centerLat + latOffset,
                longitude = centerLon + lonOffset,
                title = "Pin ${i + 1}",
                zoomLevel = defaultZoom
            )
        }
    }
}