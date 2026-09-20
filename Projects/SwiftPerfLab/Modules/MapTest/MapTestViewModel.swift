import Foundation
import CoreLocation
import Combine

/// A state-driven data structure representing the map's focal destination.
///
/// Ensures state mutations trigger exactly one data flow event, delegating 
/// spatial interpolation directly to the graphics pipeline.
struct CameraTarget: Equatable {
    let latitude: Double
    let longitude: Double
    let zoom: Float
}

/// An orchestrator to automate deterministic geospatial rendering benchmarks.
///
/// Decouples high-frequency camera interpolation from the declarative UI tree, 
/// eliminating transient memory allocations and diffing overhead during telemetry aggregation.
@MainActor
final class MapTestViewModel: ObservableObject {
    
    // MARK: - Benchmark Constants
   
    private let zoomOutLevel: Float = 13.0
    private let zoomInLevel: Float  = 17.5
    private let defaultCenterLat = 41.0082
    private let defaultCenterLon = 28.9784
    
    // MARK: - Reactive System State
    
    @Published var cameraTarget = CameraTarget(
        latitude: 41.0082,
        longitude: 28.9784,
        zoom: 11.12
    )
    
    @Published var points: [MapPoint] = []
    @Published var isRunning = false
    @Published var status = "Ready"
    @Published var exportURL: URL?
    
    // MARK: - Internal Orchestration
    
    private var testTask: Task<Void, Never>?
    private let performance = PerformanceManager.shared

    // MARK: - Execution Pipeline

    /// Initializes the benchmark and delegates data generation to a background thread.
    func startTest() {
        stopTest(isFinished: false)
        
        isRunning = true
        status = "Initializing Map & Geospatial Data..."
        exportURL = nil
        
        Task.detached(priority: .userInitiated) { [weak self] in
            let newPoints = MapTestDataGenerator.generatePoints(count: 20)
            
            await MainActor.run { [weak self] in
                guard let self = self else { return }
                self.points = newPoints
                self.status = "Benchmarking in Progress..."
                self.performance.startMonitoring()
                self.startPinTour()
            }
        }
    }
    
    /// Executes a strictly timed, state-driven spatial tour.
    ///
    /// State assignments trigger a single coordinate update per phase, bypassing 
    /// view invalidation loops to ensure objective hardware metrics.
    private func startPinTour() {
        testTask?.cancel()
        
        testTask = Task { [weak self] in
            guard let self = self else { return }
            
            // Allows the hardware baseline to stabilize post-allocation.
            try? await Task.sleep(nanoseconds: 1_000_000_000)
            let localPoints = self.points
            
            for (index, point) in localPoints.enumerated() {
                if Task.isCancelled || !self.isRunning { break }
                
                // Horizontal Panning Transition
                self.status = "Target \(index + 1) / \(localPoints.count) -> Panning ✈️"
                self.cameraTarget = CameraTarget(
                    latitude: point.coordinate.latitude,
                    longitude: point.coordinate.longitude,
                    zoom: self.zoomOutLevel
                )
                try? await Task.sleep(nanoseconds: 1_800_000_000)
                
                if Task.isCancelled || !self.isRunning { break }
                
                // Detail Inspection (Vertical Zooming)
                self.status = "Target \(index + 1) -> Inspecting Detail 🔍"
                self.cameraTarget = CameraTarget(
                    latitude: point.coordinate.latitude,
                    longitude: point.coordinate.longitude,
                    zoom: self.zoomInLevel
                )
                try? await Task.sleep(nanoseconds: 1_800_000_000)
                
                if Task.isCancelled || !self.isRunning { break }
                
                // Ascension (Zoom Out)
                if index < localPoints.count - 1 {
                    self.status = "Target \(index + 1) -> Ascending ⬆️"
                    self.cameraTarget = CameraTarget(
                        latitude: point.coordinate.latitude,
                        longitude: point.coordinate.longitude,
                        zoom: self.zoomOutLevel
                    )
                    try? await Task.sleep(nanoseconds: 1_200_000_000)
                }
            }
            
            // Global Context Review
            if self.isRunning && !Task.isCancelled {
                self.status = "Tour Finalized! Global Review 🌍"
                self.cameraTarget = CameraTarget(
                    latitude: self.defaultCenterLat,
                    longitude: self.defaultCenterLon,
                    zoom: 11.2
                )
                try? await Task.sleep(nanoseconds: 3_000_000_000)
            }
            
            if !Task.isCancelled {
                self.stopTest(isFinished: true)
            }
        }
    }
    
    // MARK: - Termination & Telemetry Export

    /// Terminates the hardware monitoring sequence and initiates data export.
    func stopTest(isFinished: Bool) {
        testTask?.cancel()
        testTask = nil
        
        guard isRunning else { return }
        
        performance.stopMonitoring()
        isRunning = false
        status = isFinished ? "Tour Completed ✅" : "Test Terminated 🛑"
        
        if isFinished {
            Task { [weak self] in
                guard let self = self else { return }
                let summary = "Total_Points_Visited,\(self.points.count),,,,"
                
                do {
                    self.exportURL = try await ExportManager.shared.generateCSV(
                        from: self.performance.currentLogs,
                        testName: "Map_Optimized_Tour_Native",
                        customSummary: summary
                    )
                } catch {
                    self.status = "Export Failed: \(error.localizedDescription)"
                }
            }
        }
    }
}