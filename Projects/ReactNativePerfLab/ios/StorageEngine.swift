import Foundation

/// A thread-safe, high-performance storage engine designed for benchmarking sequential I/O operations.
/// Utilizes low-level POSIX APIs and strict hardware synchronization to ensure accurate,
/// deterministic performance evaluation with minimal runtime overhead.
actor StorageEngine {
    
    static let shared = StorageEngine()
    private init() {}

    /// An internal checksum used to prevent the compiler's Dead Code Elimination (DCE)
    /// from optimizing away the read/write loops during Release builds.
    /// Exposed as `private(set)` so it can be observed externally, forcing the CPU to strictly execute the operations.
    private(set) var securityChecksum: Int = 0

    /// The designated URL for the benchmark test file within the application's document directory.
    private var testFileURL: URL {
        // Using FileManager API for secure access to the documents directory.
        let paths = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)
        let documentsDirectory = paths[0]
        
        // appendingPathComponent ensures compatibility across different Swift versions.
        return documentsDirectory.appendingPathComponent("benchmark_test_io.tmp")
    }

    /// Writes a specified amount of data to disk sequentially using pre-allocated memory chunks.
    ///
    /// - Parameters:
    ///   - megabytes: The total number of megabytes to write.
    ///   - onProgress: A thread-safe closure reporting the write progress (0.0 to 1.0).
    /// - Throws: An error if the file creation, manipulation, or writing process fails.
    func writeData(megabytes: Int, onProgress: @escaping @Sendable (Double) -> Void) throws {
        let fileManager = FileManager.default
        let url = testFileURL

        if fileManager.fileExists(atPath: url.path) {
            try fileManager.removeItem(at: url)
        }
        fileManager.createFile(atPath: url.path, contents: nil)

        let handle = try FileHandle(forWritingTo: url)
        defer { try? handle.close() }

        // Pre-allocate a 1MB chunk to prevent memory allocation overhead during the iteration loop.
        let chunkSize = 1024 * 1024
        let pattern = (0..<chunkSize).map { UInt8($0 % 256) }
        let dataBlock = Data(pattern)

        var lastEmittedProgress: Double = 0.0

        for i in 1...megabytes {
            if Task.isCancelled { break }

            try autoreleasepool {
                try handle.write(contentsOf: dataBlock)
                
                // Force hardware synchronization to ensure data is physically written to the storage controller.
                try handle.synchronize()

                let xorResult = dataBlock.withUnsafeBytes { buffer -> Int in
                    var result = 0
                    for byte in buffer {
                        result ^= Int(byte)
                    }
                    return result
                }
                securityChecksum ^= xorResult
            }

            // Throttle external progress emissions to 1% increments to prevent downstream event loop saturation.
            let currentProgress = Double(i) / Double(megabytes)
            if currentProgress - lastEmittedProgress >= 0.01 || currentProgress >= 1.0 {
                lastEmittedProgress = currentProgress
                onProgress(currentProgress)
            }
        }
    }

    /// Reads the benchmark data from disk using a zero-allocation POSIX stream
    /// and 64-bit word-aligned computations for maximized CPU efficiency.
    ///
    /// - Parameter onProgress: A thread-safe closure reporting the read progress (0.0 to 1.0).
    /// - Throws: An error if the file cannot be accessed or read properly.
    func readData(onProgress: @escaping @Sendable (Double) -> Void) throws {
        let fileManager = FileManager.default
        let url = testFileURL

        guard fileManager.fileExists(atPath: url.path) else { return }

        let attributes = try fileManager.attributesOfItem(atPath: url.path)
        let totalSize = (attributes[.size] as? NSNumber)?.uint64Value ?? 0
        guard totalSize > 0 else { return }

        // Utilize POSIX descriptor for lower-level file access, eliminating Objective-C bridging overhead.
        let fileDescriptor = open(url.path, O_RDONLY)
        guard fileDescriptor != -1 else {
            throw NSError(domain: NSPOSIXErrorDomain, code: Int(errno), userInfo: nil)
        }
        defer { close(fileDescriptor) }

        let chunkSize = 1024 * 1024
        
        // Allocate a reusable memory buffer strictly aligned for 64-bit integer processing.
        // This eliminates ARC deallocation cycles during high-frequency loop iterations.
        let buffer = UnsafeMutableRawPointer.allocate(byteCount: chunkSize, alignment: MemoryLayout<UInt64>.alignment)
        defer { buffer.deallocate() }

        var readBytes: UInt64 = 0
        var lastEmittedProgress: Double = 0.0

        while readBytes < totalSize {
            if Task.isCancelled { break }

            let bytesRead = read(fileDescriptor, buffer, chunkSize)
            guard bytesRead > 0 else { break }

            // 64-bit word-aligned XOR computation.
            // Processing memory in 8-byte blocks exponentially reduces iteration complexity compared to 8-bit access.
            let wordCount = bytesRead / MemoryLayout<UInt64>.size
            let words = buffer.bindMemory(to: UInt64.self, capacity: wordCount)
            
            var localChecksum: UInt64 = 0
            for i in 0..<wordCount {
                localChecksum ^= words[i]
            }
            
            // Reconcile remaining bytes if the chunk size evaluates to an irregular modulo.
            let remainder = bytesRead % MemoryLayout<UInt64>.size
            if remainder > 0 {
                let remainderBytes = buffer.advanced(by: bytesRead - remainder).bindMemory(to: UInt8.self, capacity: remainder)
                for i in 0..<remainder {
                    localChecksum ^= UInt64(remainderBytes[i])
                }
            }

            securityChecksum ^= Int(truncatingIfNeeded: localChecksum)
            readBytes += UInt64(bytesRead)

            // Throttle external progress emissions to 1% increments to prevent downstream event loop saturation.
            let currentProgress = Double(readBytes) / Double(totalSize)
            if currentProgress - lastEmittedProgress >= 0.01 || currentProgress >= 1.0 {
                lastEmittedProgress = currentProgress
                onProgress(currentProgress)
            }
        }
    }
}