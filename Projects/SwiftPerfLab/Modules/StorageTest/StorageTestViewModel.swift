import Foundation
import SwiftUI
import Combine

/// A MainActor-bound ViewModel coordinating heavy physical storage benchmarks.
/// 
/// Enforces strict thread isolation to prevent I/O operations from stalling the main run loop, 
/// ensuring accurate UI updates and telemetry collection during extreme hardware stress.
@MainActor
final class StorageTestViewModel: ObservableObject {
    
    // MARK: - Reactive UI State
    
    @Published var progress: Double = 0
    @Published var isRunning = false
    @Published var status = "Ready"
    @Published var exportURL: URL?
    
    /// Triggers the native iOS UIActivityViewController for telemetry CSV export.
    @Published var showShareSheet = false
    
    @Published var isWriteCompleted = false
    @Published var isReportReady = false
    
    private var lastTestName: String = ""
    private let performance = PerformanceManager.shared
    private var benchmarkTask: Task<Void, Never>?
    
    // MARK: - Benchmark Execution
    
    /// Initiates the I/O benchmark stream on an isolated background task.
    ///
    /// - Parameter isWrite: A boolean flag determining whether to execute the write or read sequence.
    func runBenchmark(isWrite: Bool) {
        guard !isRunning else { return }
        
        benchmarkTask?.cancel()
        
        self.isRunning = true
        self.isReportReady = false
        self.progress = 0
        self.status = isWrite ? "Writing 2GB Fixed Payload..." : "Reading 2GB Fixed Payload..."
        
        // Initiate high-resolution (0.25s) telemetry. 
        // The .micro profile bypasses the warm-up filter to capture immediate I/O burst spikes.
        performance.startMonitoring(type: .micro)
        
        // Offloads the 2GB payload stream to a detached background task 
        // to prevent interference with the MainActor's event loop.
        benchmarkTask = Task.detached(priority: .userInitiated) { [weak self] in
            guard let self = self else { return }
            
            do {
                if isWrite {
                    try await StorageEngine.shared.writeData(megabytes: 2048) { [weak self] p in
                        self?.throttleProgressUpdate(p)
                    }
                } else {
                    try await StorageEngine.shared.readData { [weak self] p in
                        self?.throttleProgressUpdate(p)
                    }
                }
                
                // Reads the checksum to prevent Dead Code Elimination (DCE) in Release builds, 
                // enforcing CPU execution of byte-level operations.
                let finalChecksum = await StorageEngine.shared.securityChecksum
                print("Hardware synchronization complete. Deterministic checksum: \(finalChecksum)")
                
                if !Task.isCancelled {
                    await self.finishTest(isWrite: isWrite)
                }
                
            } catch {
                if !Task.isCancelled {
                    await MainActor.run {
                        self.status = "Error: \(error.localizedDescription)"
                        self.isRunning = false
                        self.performance.stopMonitoring()
                    }
                }
            }
        }
    }
    
    /// Throttles state updates to 1% increments. Emitting @Published changes for every byte 
    /// during a 2GB stream would oversaturate the MainActor and drop the frame rate.
    private nonisolated func throttleProgressUpdate(_ p: Double) {
        Task { @MainActor [weak self] in
            guard let self = self else { return }
            if p - self.progress >= 0.01 || p >= 1.0 {
                self.progress = p
            }
        }
    }
    
    private func finishTest(isWrite: Bool) {
        performance.stopMonitoring()
        lastTestName = isWrite ? "Storage_Write_Native_2GB" : "Storage_Read_Native_2GB"
        self.isRunning = false
        self.status = isWrite ? "Write Completed ✅" : "Read Completed ✅"
        
        if isWrite {
            self.isWriteCompleted = true
        }
        
        self.isReportReady = true
    }
    
    // MARK: - Data Export
    
    /// Compiles the collected hardware telemetry and invokes the system share sheet.
    func shareResults() {
        Task.detached(priority: .userInitiated) { [weak self] in
            guard let self = self else { return }
            do {
                // Safely await MainActor properties from the detached background context
                let logs = await self.performance.currentLogs
                let testName = await self.lastTestName
                
                let url = try await ExportManager.shared.generateCSV(
                    from: logs,
                    testName: testName
                )
                
                await MainActor.run {
                    self.exportURL = url
                    self.showShareSheet = true
                }
            } catch {
                await MainActor.run {
                    self.status = "Export Failed: \(error.localizedDescription)"
                }
            }
        }
    }
    
    deinit {
        // Ensures hardware polling stops immediately if the ViewModel is deallocated.
        Task { @MainActor in
            PerformanceManager.shared.stopMonitoring()
        }
    }
}

// MARK: - Native Share Sheet Bridge

/// A SwiftUI wrapper for the UIKit UIActivityViewController, enabling
/// standard iOS export flows for the generated telemetry CSV files.
struct StorageShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}