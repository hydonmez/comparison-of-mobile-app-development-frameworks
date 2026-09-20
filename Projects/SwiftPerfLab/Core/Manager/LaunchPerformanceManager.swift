import Foundation
import Combine

/// A thread-safe, MainActor-bound diagnostic engine for measuring application launch latency.
///
/// Uses DispatchTime.now().uptimeNanoseconds for absolute monotonic precision, ensuring immunity 
/// to network time (NTP) synchronizations or clock modifications to accurately isolate initialization overhead.
@MainActor
final class LaunchPerformanceManager: ObservableObject {
    
    static let shared = LaunchPerformanceManager()
    
    // MARK: - Telemetry Outputs
    
    @Published var totalColdStartMs: Double = 0
    @Published var osDurationMs: Double = 0
    @Published var softwareDurationMs: Double = 0
    @Published var hotStartMs: Double = 0
    
    // MARK: - Internal Timestamps
    
    // Uses UInt64 for nanoseconds before applying floating-point division to ensure precise latency calculations.
    private var startTimeNanos: UInt64?
    private var osReadyTimeNanos: UInt64?
    private var hotStartWakeTimeNanos: UInt64?
    
    private var isColdStartReported = false
    
    private init() {}
    
    // MARK: - Cold Start Tracking
    
    /// Captures the initial baseline timestamp during the early application bootstrap phase.
    func appStarted() {
        startTimeNanos = DispatchTime.now().uptimeNanoseconds
    }
    
    /// Captures the timestamp when the operating system completes structural view allocation.
    func osReady() {
        osReadyTimeNanos = DispatchTime.now().uptimeNanoseconds
    }
    
    /// Finalizes the Cold Start telemetry cycle and computes Time to Interactive (TTI).
    func reportBenchmark() {
        guard !isColdStartReported, let start = startTimeNanos, let osReady = osReadyTimeNanos else { return }
        
        let now = DispatchTime.now().uptimeNanoseconds
        
        // Converts hardware-precise nanoseconds into human-readable milliseconds.
        self.osDurationMs = Double(osReady - start) / 1_000_000.0
        self.softwareDurationMs = Double(now - osReady) / 1_000_000.0
        self.totalColdStartMs = Double(now - start) / 1_000_000.0
        
        self.isColdStartReported = true
        
        // Purging raw timestamps to free memory.
        startTimeNanos = nil
        osReadyTimeNanos = nil
    }
    
    // MARK: - Hot Start Tracking
    
    /// Captures the exact hardware timestamp when the OS transitions the process from suspended to inactive.
    func appIsWakingUp() {
        hotStartWakeTimeNanos = DispatchTime.now().uptimeNanoseconds
    }
    
    /// Finalizes the Hot Start telemetry, calculating the RAM-to-Foreground rendering latency.
    func hotStartDetected() {
        guard isColdStartReported else {
            hotStartWakeTimeNanos = nil
            return
        }
        
        guard let wakeTime = hotStartWakeTimeNanos else { return }
        
        let now = DispatchTime.now().uptimeNanoseconds
        self.hotStartMs = Double(now - wakeTime) / 1_000_000.0
        
        self.hotStartWakeTimeNanos = nil
    }
}

// MARK: - View Rendering Optimizations

/// Pre-formats string properties within the ViewModel to move CPU-bound formatting out of the SwiftUI body layer. 
/// This prevents redundant memory allocations during high-frequency view redraws, ensuring telemetry accuracy.
extension LaunchPerformanceManager {
    
    var formattedColdStart: String {
        String(format: "%.1f", self.totalColdStartMs)
    }
    
    var formattedOSDuration: String {
        String(format: "%.0f", self.osDurationMs)
    }
    
    var formattedUIDuration: String {
        String(format: "%.0f", self.softwareDurationMs)
    }
    
    var formattedHotStart: String {
        self.hotStartMs > 0 ? String(format: "%.1f", self.hotStartMs) : "--"
    }
}