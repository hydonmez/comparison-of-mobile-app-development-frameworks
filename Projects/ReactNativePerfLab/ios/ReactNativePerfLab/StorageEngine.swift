// ios/StorageEngine.swift

import Foundation

/// Engineered for deterministic NAND flash benchmarking using low-level POSIX APIs.
public actor StorageEngine {
    
    public static let shared = StorageEngine()
    private init() {}

    public private(set) var securityChecksum: Int = 0

    private var testFileURL: URL {
        URL.documentsDirectory.appending(path: "benchmark_test_io.tmp")
    }

    public func writeData(megabytes: Int, onProgress: @escaping @Sendable (Double) -> Void) throws {
        let fileManager = FileManager.default
        let url = testFileURL

        if fileManager.fileExists(atPath: url.path) {
            try fileManager.removeItem(at: url)
        }
        fileManager.createFile(atPath: url.path, contents: nil)

        let handle = try FileHandle(forWritingTo: url)
        defer { try? handle.close() }

        let chunkSize = 1024 * 1024
        let pattern = (0..<chunkSize).map { UInt8($0 % 256) }
        let dataBlock = Data(pattern)

        var lastEmittedProgress: Double = 0.0

        for i in 1...megabytes {
            if Task.isCancelled { break }

            try autoreleasepool {
                try handle.write(contentsOf: dataBlock)
                try handle.synchronize() // Force physical disk write

                let xorResult = dataBlock.withUnsafeBytes { buffer -> Int in
                    var result = 0
                    for byte in buffer {
                        result ^= Int(byte)
                    }
                    return result
                }
                securityChecksum ^= xorResult
            }

            let currentProgress = Double(i) / Double(megabytes)
            if currentProgress - lastEmittedProgress >= 0.01 || currentProgress >= 1.0 {
                lastEmittedProgress = currentProgress
                onProgress(currentProgress)
            }
        }
    }

    public func readData(onProgress: @escaping @Sendable (Double) -> Void) throws {
        let fileManager = FileManager.default
        let url = testFileURL
        guard fileManager.fileExists(atPath: url.path) else { return }

        let attributes = try fileManager.attributesOfItem(atPath: url.path)
        let totalSize = (attributes[.size] as? NSNumber)?.uint64Value ?? 0
        guard totalSize > 0 else { return }

        let fileDescriptor = open(url.path, O_RDONLY)
        guard fileDescriptor != -1 else { throw NSError(domain: NSPOSIXErrorDomain, code: Int(errno)) }
        defer { close(fileDescriptor) }

        let chunkSize = 1024 * 1024
        let buffer = UnsafeMutableRawPointer.allocate(byteCount: chunkSize, alignment: MemoryLayout<UInt64>.alignment)
        defer { buffer.deallocate() }

        var readBytes: UInt64 = 0
        var lastEmittedProgress: Double = 0.0

        while readBytes < totalSize {
            if Task.isCancelled { break }

            let bytesRead = read(fileDescriptor, buffer, chunkSize)
            guard bytesRead > 0 else { break }

            let wordCount = bytesRead / MemoryLayout<UInt64>.size
            let words = buffer.bindMemory(to: UInt64.self, capacity: wordCount)
            
            var localChecksum: UInt64 = 0
            for i in 0..<wordCount {
                localChecksum ^= words[i]
            }
            securityChecksum ^= Int(truncatingIfNeeded: localChecksum)
            readBytes += UInt64(bytesRead)

            let currentProgress = Double(readBytes) / Double(totalSize)
            if currentProgress - lastEmittedProgress >= 0.01 || currentProgress >= 1.0 {
                lastEmittedProgress = currentProgress
                onProgress(currentProgress)
            }
        }
    }
}