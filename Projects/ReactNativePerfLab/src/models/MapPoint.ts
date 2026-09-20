/**
 * Spatial Annotation DTO
 * A lightweight, statically typed data model representing a geographic annotation.
 * Kept flat to minimize serialization overhead when passing large datasets 
 * across the bridge.
 */
export interface MapPoint {
    /**
     * A deterministic integer uniquely identifying the coordinate.
     * Using a number instead of a string UUID minimizes memory allocation 
     * and ensures highly efficient O(1) diffing when rendering marker clusters.
     */
    readonly id: number;

    /**
     * The precise spatial coordinates for rendering on the map surface.
     */
    readonly coordinate: LatLng;

    /**
     * A localized, descriptive label associated with the geographic coordinate.
     */
    readonly title: string;
}

/**
 * A lightweight coordinate representation.
 */
export interface LatLng {
    readonly latitude: number;
    readonly longitude: number;
}