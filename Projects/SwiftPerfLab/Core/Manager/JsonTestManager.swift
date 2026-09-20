import Foundation

/// A synchronous, thread-safe benchmarking engine for measuring the raw CPU throughput of Swift's JSONDecoder.
///
/// Implemented as an @unchecked Sendable final class using a low-level mutex (NSLock) instead of a Swift actor. 
/// This bypasses the Swift Concurrency scheduler to eliminate asynchronous context-switching overhead 
/// and isolate absolute CPU parsing latency.
final class JsonTestManager: @unchecked Sendable {
    
    static let shared = JsonTestManager()
    
    /// The pre-loaded JSON payload residing entirely in active RAM.
    /// Guarded by a low-level mutex (NSLock) for Swift 6 strict concurrency compliance without actor overhead.
    private var cachedData: Data?
    
    /// Instantiates a single JSONDecoder to eliminate object allocation and configuration overhead
    /// during the high-frequency benchmark loops.
    private let decoder = JSONDecoder()
    
    private let dataLock = NSLock()
    
    private init() {}
    
    // MARK: - Pre-Computation Phase
    
    /// Executes before the benchmark timer starts.
    ///
    /// Offloads disk I/O to a detached background thread to ensure the benchmark measures 
    /// pure CPU deserialization performance, isolated from storage read latencies.
    func preloadDataOnce() async throws {
        
        // Uses withLock to guarantee a synchronous, safe execution scope and prevent thread-pool deadlocks.
        let isAlreadyLoaded = dataLock.withLock {
            return (cachedData != nil)
        }
        
        if isAlreadyLoaded { return }
        
        // Isolate disk I/O from the caller's context
        let data = try await Task.detached(priority: .background) {
            guard let url = Bundle.main.url(forResource: "benchmark_data", withExtension: "json") else {
                throw NSError(
                    domain: "FileNotFound",
                    code: 404,
                    userInfo: [NSLocalizedDescriptionKey: "JSON benchmark file not found in the main bundle."]
                )
            }
            
            // Uses Data(contentsOf:) without .mappedIfSafe to avoid memory mapping (mmap). 
            // This prevents deferred physical memory allocation and unpredictable OS page faults 
            // during active JSON parsing.
            return try Data(contentsOf: url)
        }.value
        
        // Thread-safe assignment using async-safe scoped locking
        dataLock.withLock {
            cachedData = data
        }
    }
    
    // MARK: - Active Benchmark Phase
    
    /// The active computational payload for the benchmark.
    ///
    /// This method is strictly synchronous, running directly on the calling thread to capture 
    /// the exact CPU deserialization throughput.
    ///
    /// - Returns: The total number of decoded entity graphs.
    func runParseTest() throws -> Int {
        
        // Safely extract the memory reference using scoped locking to prevent dangling mutexes.
        let data: Data = dataLock.withLock {
            guard let cached = cachedData else {
                fatalError("CRITICAL EXCEPTION: preloadDataOnce() MUST be executed prior to benchmark initiation.")
            }
            return cached
        }
        
        // Forces immediate deallocation of temporary Objective-C bridged objects created by 
        // NSJSONSerialization. This prevents ARC accumulation from skewing RAM telemetry.
        return try autoreleasepool {
            // Evaluates the instantiation of deeply nested Swift standard library collections.
            let items = try decoder.decode([GitHubEvent].self, from: data)
            return items.count
        }
    }
}