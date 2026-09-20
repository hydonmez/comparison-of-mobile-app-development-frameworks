import Foundation

/// A thread-safe, high-performance storage engine for benchmarking sequential I/O and RAM Cache operations.
/// Uses low-level POSIX APIs and Grand Central Dispatch (GCD) to isolate threads and prevent
/// Main Thread starvation during heavy memory bandwidth saturation.
actor StorageEngine {
    
    static let shared = StorageEngine()
    private init() {}

    /// An internal checksum used to prevent the compiler's Dead Code Elimination (DCE)
    /// from removing the read/write loops in Release builds.
    /// Exposed as `private(set)` to force the CPU to execute the operations.
    private(set) var securityChecksum: Int = 0

    /// The URL for the benchmark test file in the application's document directory.
    private var testFileURL: URL {
        URL.documentsDirectory.appending(path: "benchmark_test_io.tmp")
    }

    /// Writes a specified amount of data sequentially.
    func writeData(megabytes: Int, onProgress: @escaping @Sendable (Double) -> Void) async throws {
        let fileManager = FileManager.default
        let url = testFileURL

        if fileManager.fileExists(atPath: url.path) {
            try fileManager.removeItem(at: url)
        }
        fileManager.createFile(atPath: url.path, contents: nil)

        // Offloads the operation to a background GCD queue.
        // This prevents locking the Swift cooperative async thread pool during heavy I/O.
        let computedChecksum: Int = try await withCheckedThrowingContinuation { continuation in
            DispatchQueue.global(qos: .userInitiated).async {
                do {
                    let handle = try FileHandle(forWritingTo: url)
                    defer { try? handle.close() }

                    let chunkSize = 1024 * 1024
                    let pattern = (0..<chunkSize).map { UInt8($0 % 256) }
                    let dataBlock = Data(pattern)

                    var lastEmittedProgress: Double = 0.0
                    var localSecurityChecksum: Int = 0 // Local variable for isolated off-actor calculation

                    for i in 1...megabytes {
                        try autoreleasepool {
                            try handle.write(contentsOf: dataBlock)
                            try handle.synchronize()

                            let xorResult = dataBlock.withUnsafeBytes { buffer -> Int in
                                var result = 0
                                for byte in buffer {
                                    result ^= Int(byte)
                                }
                                return result
                            }
                            localSecurityChecksum ^= xorResult
                        }

                        let currentProgress = Double(i) / Double(megabytes)
                        if currentProgress - lastEmittedProgress >= 0.01 || currentProgress >= 1.0 {
                            lastEmittedProgress = currentProgress
                            onProgress(currentProgress)
                        }
                    }
                    
                    // Returns the final computed value back to the actor context.
                    continuation.resume(returning: localSecurityChecksum)
                    
                } catch {
                    continuation.resume(throwing: error)
                }
            }
        }
        
        // Mutates the actor's state only after the isolated I/O operations are fully resolved.
        self.securityChecksum ^= computedChecksum
    }

    /// Reads benchmark data from RAM/Disk using a zero-allocation POSIX stream.
    func readData(onProgress: @escaping @Sendable (Double) -> Void) async throws {
        let fileManager = FileManager.default
        let url = testFileURL

        guard fileManager.fileExists(atPath: url.path) else { return }

        let attributes = try fileManager.attributesOfItem(atPath: url.path)
        let totalSize = (attributes[.size] as? NSNumber)?.uint64Value ?? 0
        guard totalSize > 0 else { return }

        // Completely decouples the read loop from both UI and Swift cooperative worker threads.
        let computedChecksum: Int = try await withCheckedThrowingContinuation { continuation in
            DispatchQueue.global(qos: .userInitiated).async {
                
                let fileDescriptor = open(url.path, O_RDONLY)
                guard fileDescriptor != -1 else {
                    continuation.resume(throwing: NSError(domain: NSPOSIXErrorDomain, code: Int(errno), userInfo: nil))
                    return
                }
                
                // F_NOCACHE is intentionally omitted so the OS retains the file in the Page Cache.
                // This allows data to be read instantaneously from memory, maximizing CPU stress
                // while keeping the UI thread isolated.
                defer { close(fileDescriptor) }

                let chunkSize = 1024 * 1024
                let buffer = UnsafeMutableRawPointer.allocate(byteCount: chunkSize, alignment: MemoryLayout<UInt64>.alignment)
                defer { buffer.deallocate() }

                var readBytes: UInt64 = 0
                var lastEmittedProgress: Double = 0.0
                var localSecurityChecksum: Int = 0

                while readBytes < totalSize {
                    let bytesRead = read(fileDescriptor, buffer, chunkSize)
                    guard bytesRead > 0 else { break }

                    let wordCount = bytesRead / MemoryLayout<UInt64>.size
                    let words = buffer.bindMemory(to: UInt64.self, capacity: wordCount)
                    
                    var localChecksum: UInt64 = 0
                    for i in 0..<wordCount {
                        localChecksum ^= words[i]
                    }
                    
                    let remainder = bytesRead % MemoryLayout<UInt64>.size
                    if remainder > 0 {
                        let remainderBytes = buffer.advanced(by: bytesRead - remainder).bindMemory(to: UInt8.self, capacity: remainder)
                        for i in 0..<remainder {
                            localChecksum ^= UInt64(remainderBytes[i])
                        }
                    }

                    localSecurityChecksum ^= Int(truncatingIfNeeded: localChecksum)
                    readBytes += UInt64(bytesRead)

                    let currentProgress = Double(readBytes) / Double(totalSize)
                    if currentProgress - lastEmittedProgress >= 0.01 || currentProgress >= 1.0 {
                        lastEmittedProgress = currentProgress
                        onProgress(currentProgress)
                    }
                }
                
                continuation.resume(returning: localSecurityChecksum)
            }
        }
        
        self.securityChecksum ^= computedChecksum
    }
}