import Foundation
import CoreLocation

/// A lightweight, thread-safe data model for geographical annotations.
/// Conforms to `Identifiable` for optimal UI diffing in SwiftUI, and `Sendable` for strict 
/// concurrency compliance when bridging spatial datasets across threads.
struct MapPoint: Identifiable, Sendable {
    
    /// A deterministic integer used as a unique identifier.
    /// Using an `Int` instead of `UUID()` eliminates CPU overhead during dataset generation. 
    /// This ensures the benchmark measures native MapKit rendering performance isolated from data-generation bottlenecks.
    let id: Int
    
    /// The precise coordinate (latitude and longitude) for map placement.
    let coordinate: CLLocationCoordinate2D
    
    /// A localized label for the geographic coordinate.
    let title: String
}