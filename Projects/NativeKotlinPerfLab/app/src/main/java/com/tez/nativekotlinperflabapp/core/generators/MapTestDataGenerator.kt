package com.tez.nativekotlinperflabapp.core.generators

import com.google.android.gms.maps.model.LatLng
import com.tez.nativekotlinperflabapp.models.MapPoint
import kotlin.math.PI
import kotlin.math.sin

/**
 * A thread-safe data generator for creating spatial datasets.
 * Runs synchronously because the mathematical generation is fast enough
 * that context switching would introduce unnecessary overhead.
 */
object MapTestDataGenerator {

    /**
     * Generates a spatially distributed list of map points.
     * Uses a sine wave distribution to create a realistic spread.
     *
     * @param count The total number of map points to generate. Defaults to 20.
     * @return A List of initialized MapPoint entities.
     */
    fun generatePoints(count: Int = 20): List<MapPoint> {

        // Base coordinates anchored to Istanbul.
        val centerLat = 41.0082
        val centerLon = 28.9784
        val spread = 0.05

        // Pre-allocates the exact memory required to avoid the overhead
        // of dynamic array resizing.
        return List(count) { i ->
            val progress = i.toDouble() / count.toDouble()

            // Calculate linear latitude progression and sine-wave based longitude oscillation.
            val latOffset = (progress - 0.5) * spread * 2.0
            val lonOffset = sin(progress * PI * 4.0) * spread

            MapPoint(
                id = i, // Assigns a unique integer ID
                coordinate = LatLng(
                    centerLat + latOffset,
                    centerLon + lonOffset
                ),
                title = "Pin ${i + 1}"
            )
        }
    }
}