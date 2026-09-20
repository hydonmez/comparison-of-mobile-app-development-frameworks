import Foundation
import Combine
import SwiftUI

/// A MainActor-bound ViewModel for orchestrating high-frequency JSON deserialization benchmarks.
@MainActor
final class JsonTestViewModel: ObservableObject {
    
    // MARK: - Reactive UI State
    
    @Published var status: String = "Ready"
    @Published var isTesting: Bool = false
    @Published var exportURL: URL?
    @Published var parsedCount: Int = 0
    
    private let performance = PerformanceManager.shared
    
    /// The total number of consecutive parsing iterations to execute per benchmark suite.
    private let iterations = 200
    
    // MARK: - Benchmark Preparation
    
    /// Loads the dataset from storage into RAM to isolate physical disk I/O latency from pure CPU metrics.
    func preloadData() async {
        do {
            try await JsonTestManager.shared.preloadDataOnce()
            status = "Payload Pre-loaded. Ready to Test."
        } catch {
            status = "Preload Failed: \(error.localizedDescription)"
        }
    }
    
    // MARK: - Benchmark Execution
    
    /// Initiates the automated deserialization benchmark sequence safely without blocking the UI.
    func startBenchmark() {
        guard !isTesting else { return }
        
        isTesting = true
        status = "Initiating In-Memory JSON Benchmark..."
        parsedCount = 0
        exportURL = nil
        
        performance.startMonitoring()
        
        let loops = self.iterations
        
        // Detaches the parsing workload. Using .userInitiated ensures allocation to high-performance cores.
        Task.detached(priority: .userInitiated) { [weak self] in
            
            let clock = ContinuousClock()
            var localTotalItems = 0
            var totalDuration: Duration = .zero
            
            do {
                for i in 1...loops {
                    
                    // Measures only the synchronous decoding workload to prevent UI context-switch overhead 
                    // from affecting CPU telemetry.
                    let elapsed = try clock.measure {
                        let count = try JsonTestManager.shared.runParseTest()
                        localTotalItems += count
                    }
                    totalDuration += elapsed
                    
                    // Explicitly yields execution back to the Swift Concurrency scheduler. 
                    // This prevents dropping CADisplayLink frames (UI jank) during prolonged, intensive synchronous CPU operations.
                    await Task.yield()
                    
                    // Throttles UI mutations to prevent overwhelming the MainActor.
                    if i % 10 == 0 {
                        let progress = Int((Double(i) / Double(loops)) * 100)
                        await MainActor.run { [weak self] in
                            self?.status = "Processing: \(progress)%"
                        }
                    }
                }
                
                // Granular duration extraction to avoid precision loss.
                let seconds = totalDuration.components.seconds
                let attoseconds = totalDuration.components.attoseconds
                let totalSeconds = Double(seconds) + Double(attoseconds) / 1e18
                
                let finalItems = localTotalItems
                
                await MainActor.run { [weak self] in
                    guard let self = self else { return }
                    self.parsedCount = finalItems
                    
                    let roundedTime = round(totalSeconds * 1000) / 1000.0
                    self.status = "Completed in \(roundedTime) sec"
                    self.finishTest(isSuccess: true)
                }
                
            } catch {
                await MainActor.run { [weak self] in
                    self?.status = "Error: \(error.localizedDescription)"
                    self?.finishTest(isSuccess: false)
                }
            }
        }
    }
    
    // MARK: - Post-Benchmark Serialization
    
    private func finishTest(isSuccess: Bool) {
        isTesting = false
        performance.stopMonitoring()
        
        if isSuccess {
            Task { @MainActor [weak self] in
                guard let self = self else { return }
                do {
                    self.exportURL = try await ExportManager.shared.generateCSV(
                        from: self.performance.currentLogs,
                        testName: "JSON_Benchmark_Native"
                    )
                } catch {
                    self.status = "CSV Export Failed: \(error.localizedDescription)"
                }
            }
        }
    }
}