import Foundation
import Combine
import QuartzCore
import CoreMotion // Required for CMAcceleration, CMRotationRate, etc.

/// A MainActor-bound ViewModel managing high-frequency sensor telemetry benchmarks.
///
/// Uses strict actor-isolation for state mutations, monotonic time tracking to prevent 
/// timer drift, and PassthroughSubject streams to route high-frequency hardware events 
/// directly to views without triggering global recomposition.
@MainActor
final class SensorTestViewModel: ObservableObject {
    
    // MARK: - Reactive State (Low-Frequency)
    
    @Published private(set) var isRunning = false
    @Published private(set) var status = "Sensors Ready"
    @Published private(set) var exportURL: URL?
    @Published private(set) var progress: Double = 0.0
    
    // MARK: - Transient Streams (High-Frequency)
    
    // Isolated event streams emit hardware data without triggering objectWillChange 
    // on the ViewModel, ensuring zero-cost UI isolation.
    let accelStream = PassthroughSubject<CMAcceleration, Never>()
    let gyroStream = PassthroughSubject<CMRotationRate, Never>()
    let magnetStream = PassthroughSubject<CMMagneticField, Never>()
    let stepStream = PassthroughSubject<Int, Never>()
    
    // MARK: - Dependencies
    
    private let performance = PerformanceManager.shared
    
    /// Standard benchmark duration enforced for sustained hardware stress evaluation.
    private let testDuration: CFTimeInterval = 60.0
    
    // MARK: - Concurrency & Timing
    
    private var timerTask: Task<Void, Never>?
    private var absoluteStartTime: CFTimeInterval = 0
    
    // MARK: - Benchmark Lifecycle
    
    /// Initiates the sensor benchmark, engages hardware monitoring, and starts the telemetry chronometer.
    func startTest() {
        guard !isRunning else { return }
        
        isRunning = true
        status = "Acquiring Telemetry (100Hz Background)..."
        exportURL = nil
        progress = 0.0
        
        performance.startMonitoring()
        
        // Binds the decoupled hardware execution engine to the transient event streams.
        SensorEngine.shared.startSensors(
            onAccelUpdate: { [weak self] data in
                self?.accelStream.send(data)
            },
            onGyroUpdate: { [weak self] data in
                self?.gyroStream.send(data)
            },
            onMagnetUpdate: { [weak self] data in
                self?.magnetStream.send(data)
            },
            onStepUpdate: { [weak self] data in
                self?.stepStream.send(data)
            }
        )
        
        // Captures absolute baseline time to eliminate timer drift 
        // caused by thread suspensions or OS throttling.
        absoluteStartTime = CACurrentMediaTime()
        
        // Uses asynchronous sleep for memory-safe cancellation if the benchmark is aborted.
        timerTask = Task { [weak self] in
            guard let self = self else { return }
            
            while !Task.isCancelled {
                // Suspends execution for 100ms. Utilizing try? immediately throws 
                // upon cancellation, preventing zombie tasks.
                try? await Task.sleep(nanoseconds: 100_000_000)
                
                if Task.isCancelled { break }
                self.updateProgress()
            }
        }
    }
    
    /// Terminates the active benchmark, flushes hardware buffers, and serializes telemetry logs.
    ///
    /// - Parameter isFinished: Indicates if the test concluded naturally (`true`) or was aborted (`false`).
    func stopTest(isFinished: Bool = false) {
        guard isRunning else { return }
        
        isRunning = false
        status = isFinished ? "✅ Test Finalized" : "🛑 Terminated"
        
        timerTask?.cancel()
        timerTask = nil
        
        SensorEngine.shared.stopSensors()
        performance.stopMonitoring()
        
        if isFinished {
            progress = 1.0
            
            // Offloads CSV serialization to a detached Task to prevent 
            // Main Thread blockage during file I/O operations.
            Task { [weak self] in
                guard let self = self else { return }
                do {
                    self.exportURL = try await ExportManager.shared.generateCSV(
                        from: self.performance.currentLogs,
                        testName: "Sensor_Native_Stress_Test_100Hz"
                    )
                } catch {
                    self.status = "❌ CSV Export Failed: \(error.localizedDescription)"
                }
            }
        }
    }
    
    // MARK: - Internal Engine
    
    /// Evaluates the current execution duration against the monotonic hardware clock.
    private func updateProgress() {
        let now = CACurrentMediaTime()
        let timeElapsed = now - absoluteStartTime
        
        guard timeElapsed < testDuration else {
            stopTest(isFinished: true)
            return
        }
        
        // UI interpolation is deferred to the presentation layer (e.g., .animation) 
        // to mitigate CoreAnimation CPU overhead during high-frequency state updates.
        progress = min(timeElapsed / testDuration, 1.0)
    }
}