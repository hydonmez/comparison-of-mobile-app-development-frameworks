import SwiftUI
import Combine

/// A MainActor-bound ViewModel for orchestrating automated UI scrolling benchmarks.
///
/// Simulates high-velocity e-commerce interactions by automating programmatic list navigation 
/// and on-demand pagination. Offloads data generation to ensure pure rendering telemetry 
/// without I/O thread-blocking artifacts.
@MainActor
final class ListTestViewModel: ObservableObject {

    // MARK: - Reactive UI State
    
    @Published var products: [Product] = []
    @Published var isRunning = false
    @Published var isLoadingMore = false
    @Published var status = "Ready"
    @Published var exportURL: URL?
    
    // Binds directly to the ScrollViewReader to dynamically route the viewport position.
    @Published var scrollTarget: Int?

    // MARK: - Internal Orchestration
    
    private let performance = PerformanceManager.shared
    private var testTask: Task<Void, Never>?

    private let pageSize = 20
    private let targetCount = 300

    // MARK: - Benchmark Initialization
    
    /// Initializes the deterministic benchmarking environment and engages hardware telemetry.
    func startTest() {
        stopTest(isFinished: false)

        products.removeAll()
        isRunning = true
        status = "Preparing Dataset..."
        exportURL = nil

        performance.startMonitoring()

        // Encapsulating the scrolling lifecycle within a designated Task allows for 
        // immediate cooperative cancellation if the user aborts the benchmark.
        testTask = Task { [weak self] in
            guard let self = self else { return }
            
            // Await is required to safely cross the isolation boundary into the actor.
            await ListTestManager.shared.resetCursor()
            
            await self.loadMoreData()
            await self.runAutoScrollScenario()
        }
    }

    /// Asynchronously fetches the sequential dataset payload.
    ///
    /// Explicitly offloads mock data fetching and JSON parsing to a detached background thread.
    /// This isolates the simulated I/O latency from the MainActor, preventing UI Thread 
    /// starvation during active programmatic animations.
    private func loadMoreData() async {
        guard !isLoadingMore else { return }
        isLoadingMore = true

        let currentSize = self.pageSize
        
        // Detached execution scope prevents Swift's cooperative thread pool from 
        // prioritizing this operation over critical UI rendering passes.
        let newItems = await Task.detached(priority: .userInitiated) {
            // Simulated Network Latency: Enforces a strict 100ms delay.
            try? await Task.sleep(nanoseconds: 100_000_000)
            
            // Accessing fetchPage on the ListTestManager actor from a detached context 
            // requires await to cross the boundary safely.
            return await ListTestManager.shared.fetchPage(pageSize: currentSize)
        }.value

        self.products.append(contentsOf: newItems)
        self.isLoadingMore = false
    }

    // MARK: - Automated Interaction Scenarios
    
    /// Executes a controlled UI interaction sequence: Downward traversal, pagination, and ascent.
    private func runAutoScrollScenario() async {
        var index = 0

        // PHASE 1: Downward Scrolling & Pagination Stress Test
        while index < (self.targetCount - 5) {
            
            // Terminates execution if the parent Task is cancelled.
            if Task.isCancelled || !self.isRunning { break }

            let next = min(index + 2, self.products.count - 1)
            
            if next > index {
                let id = self.products[next].id
                index = next

                self.status = "Scrolling Down... (\(index)/\(self.products.count))"

                // Mutates the scroll target, automatically triggering the ScrollViewReader.
                if self.scrollTarget != id {
                    withAnimation(.linear(duration: 0.8)) {
                        self.scrollTarget = id
                    }
                }
                try? await Task.sleep(nanoseconds: 800_000_000)
            } else {
                try? await Task.sleep(nanoseconds: 500_000_000)
            }

            // Initiates asynchronous data fetching without halting the scroll animation loop.
            if !self.isLoadingMore,
               self.products.count < self.targetCount,
               index >= self.products.count - 10 {
                
                // Dispatches pagination to an independent sub-task.
                Task { [weak self] in
                    await self?.loadMoreData()
                }
            }
        }

        // PHASE 2: Target Traversal Achieved - Finalizing Descent
        if !Task.isCancelled, let last = self.products.last?.id {
            self.status = "Target Achieved"
            withAnimation(.easeOut(duration: 1.5)) {
                self.scrollTarget = last
            }
            try? await Task.sleep(nanoseconds: 2_000_000_000)
        }

        // PHASE 3: Ascending Traverse - Measuring Recycling Performance
        // Forces the UI Engine to rapidly instantiate previously destroyed views.
        if !Task.isCancelled, let first = self.products.first?.id {
            self.status = "Returning to Top..."
            withAnimation(.easeInOut(duration: 2.5)) {
                self.scrollTarget = first
            }
            try? await Task.sleep(nanoseconds: 3_000_000_000)
        }

        // Graceful benchmark termination and serialization sequence.
        if !Task.isCancelled {
            self.stopTest(isFinished: true)
        }
    }

    /// Terminates the active sequence, purges observers, and initiates I/O serialization.
    func stopTest(isFinished: Bool = false) {
        // Broadcasts a cancellation signal to all inner task loops.
        testTask?.cancel()
        testTask = nil

        guard isRunning else { return }

        performance.stopMonitoring()
        isRunning = false
        status = isFinished ? "Test Finalized" : "Terminated"

        // Wraps the async generation within a detached scope to prevent MainActor lock contention.
        if isFinished {
            Task { @MainActor [weak self] in
                guard let self = self else { return }
                do {
                    self.exportURL = try await ExportManager.shared.generateCSV(
                        from: self.performance.currentLogs,
                        testName: "Ecommerce_Fluent_Test_Native"
                    )
                } catch {
                    self.status = "Export Failed: \(error.localizedDescription)"
                }
            }
        }
    }
}