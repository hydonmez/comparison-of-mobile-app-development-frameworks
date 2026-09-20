package com.tez.nativekotlinperflabapp.models

import com.google.android.gms.maps.model.LatLng

/**
 * A lightweight, thread-safe Data Transfer Object (DTO) representing a geographical map point.
 */
data class MapPoint(

    /**
     * A unique identifier required by Jetpack Compose for efficient list diffing
     * and stable rendering.
     */
    val id: Int,

    /**
     * The precise spatial coordinates (latitude and longitude) of the map point.
     */
    val coordinate: LatLng,

    /**
     * A localized, descriptive label associated with the geographic coordinate.
     */
    val title: String
)