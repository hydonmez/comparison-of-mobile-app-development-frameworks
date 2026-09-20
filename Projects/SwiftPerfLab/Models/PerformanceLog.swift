import Foundation

/// Represents a single snapshot of hardware telemetry.
/// Conforms to `Sendable` to securely transfer high-frequency telemetry data from the MainActor 
/// to detached background threads without triggering data races.
struct PerformanceLog: Identifiable, Sendable {
    
    /// Uses the monotonic timestamp as the unique identifier to avoid the CPU overhead 
    /// associated with dynamic `UUID()` allocation.
    var id: Double { timestamp }
    
    let timestamp: TimeInterval
    let cpuUsage: Double
    let rawRAM: Double
    let netRAM: Double
    let batteryLevel: Double
    let fps: Int
    let thermalState: String
    
    /// Lazily formats the absolute timestamp into a human-readable string.
    /// Delegates string interpolation to the caller's execution context to prevent 
    /// MainActor contention and UI stalling during heavy CSV exports.
    var formattedTime: String {
        return PerformanceLog.timeFormatter.string(
            from: Date(timeIntervalSince1970: timestamp)
        )
    }
    
    /// A shared DateFormatter instance.
    /// Allocating a new DateFormatter per log is a CPU bottleneck. Instantiating it statically 
    /// eliminates this overhead. Marking it as `nonisolated` allows asynchronous formatting 
    /// without blocking the Main Thread.
    nonisolated private static let timeFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "HH:mm:ss"
        return f
    }()
}