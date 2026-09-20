import Foundation
import CoreLocation

/// Ensures Swift 6 concurrency compliance.
/// CLLocationCoordinate2D is a thread-safe C-struct, safely bypassing strict concurrency checks.
extension CLLocationCoordinate2D: @retroactive @unchecked Sendable {}

/// A thread-safe namespace for generating spatial datasets.
///
/// Architected as a stateless enum instead of an actor or class to avoid 
/// asynchronous context-switching overhead, achieving maximum CPU throughput.
enum MapTestDataGenerator {
    
    /// Generates a spatially distributed array of map annotations using a sine wave distribution. 
    /// This provides a baseline for testing map rendering and spatial indexing performance.
    ///
    /// - Parameter count: The total number of map points to generate. Defaults to 20.
    /// - Returns: An array of initialized `MapPoint` entities.
    nonisolated static func generatePoints(count: Int = 20) -> [MapPoint] {
        // Base coordinates anchored to Istanbul for spatial rendering tests.
        let centerLat = 41.0082
        let centerLon = 28.9784
        let spread = 0.05

        // Generates coordinates using trigonometric distribution.
        // Using .map on a Range pre-allocates exact memory, avoiding CPU overhead 
        // from dynamic array resizing.
        return (0..<count).map { i in
            let progress = Double(i) / Double(count)
            
            // Calculates linear latitude and sine-wave longitude oscillation.
            let latOffset = (progress - 0.5) * spread * 2
            let lonOffset = sin(progress * .pi * 4) * spread

            return MapPoint(
                // Uses a deterministic integer ID to eliminate CPU and RAM overhead 
                // of UUID() generation.
                id: i,
                coordinate: CLLocationCoordinate2D(
                    latitude: centerLat + latOffset,
                    longitude: centerLon + lonOffset
                ),
                title: "Pin \(i + 1)"
            )
        }
    }
}