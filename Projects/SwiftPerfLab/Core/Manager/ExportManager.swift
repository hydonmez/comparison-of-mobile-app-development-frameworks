import Foundation

/// A utility class for aggregating, processing, and exporting telemetry data.
///
/// Designed as an @unchecked Sendable singleton for maximum CPU throughput via non-isolated static processing. 
/// It offloads heavy string concatenation and file I/O operations from the main execution thread.
final class ExportManager: @unchecked Sendable {
    
    static let shared = ExportManager()
    private init() {}
    
    // MARK: - CSV Export Pipeline
    
    /// Generates a comma-separated values (CSV) file from an array of performance telemetry logs.
    ///
    /// - Parameters:
    ///   - logs: The raw performance logs captured during the benchmark suite.
    ///   - testName: A descriptive identifier for the benchmark, utilized for file naming.
    ///   - customSummary: Optional appended text for additional context or metadata.
    /// - Returns: A localized `URL` pointing to the generated CSV file in the temporary directory.
    func generateCSV(
        from logs: [PerformanceLog],
        testName: String,
        customSummary: String? = nil
    ) async throws -> URL? {
        
        guard !logs.isEmpty else { return nil }
        
        // Pre-maps complex object properties into Sendable-safe tuples on the caller's context 
        // (e.g., MainActor) to prevent cross-actor reference violations under Swift 6's strict concurrency model.
        let rawDataForExport = logs.map { log in
            (
                time: log.formattedTime,
                cpu: log.cpuUsage,
                raw: log.rawRAM,
                net: log.netRAM,
                bat: log.batteryLevel,
                fps: log.fps,
                thermal: log.thermalState
            )
        }
        
        // Offloads intensive CPU operations (string interpolation and mathematical aggregation)
        // to a background thread to prevent UI stalling.
        return try await Task.detached(priority: .background) {
            var rows: [String] = []
            
            // Pre-allocating memory capacity prevents dynamic array resizing overhead.
            rows.reserveCapacity(rawDataForExport.count + 10)
            
            let locale = Locale(identifier: "en_US")
            
            // Header
            rows.append("Timestamp,CPU(%),Raw_RAM(MB),Net_RAM(MB),Battery(%),FPS,Thermal_State")
            
            // Raw Logs
            for log in rawDataForExport {
                
                // Enforces immediate deallocation of temporary strings created during interpolation 
                // to prevent localized heap fragmentation during massive dataset exports.
                let rowStr = autoreleasepool { () -> String in
                    return [
                        log.time,
                        String(format: "%.2f", locale: locale, log.cpu),
                        String(format: "%.2f", locale: locale, log.raw),
                        String(format: "%.2f", locale: locale, log.net),
                        String(format: "%.1f", locale: locale, log.bat),
                        "\(log.fps)",
                        log.thermal
                    ].joined(separator: ",")
                }
                rows.append(rowStr)
            }
            
            // Summary
            rows.append("\n--- SUMMARY ---")
            
            let summaryString = Self.calculateSummary(from: rawDataForExport, locale: locale)
            rows.append(summaryString)
            
            if let customSummary = customSummary {
                rows.append("\n" + customSummary)
            }
            
            let csvContent = rows.joined(separator: "\n")
            let fileName = "\(testName)_\(Int(Date().timeIntervalSince1970)).csv"
            let url = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)
            
            try csvContent.write(to: url, atomically: true, encoding: .utf8)
            return url
            
        }.value
    }
    
    // MARK: - Statistical Aggregation
    
    /// Calculates core statistical metrics (Average, Standard Deviation, Min, Max) for the dataset.
    ///
    /// Marked as static and nonisolated to allow execution from any background thread 
    /// without actor-hopping latency.
    nonisolated private static func calculateSummary(
        from data: [(time: String, cpu: Double, raw: Double, net: Double, bat: Double, fps: Int, thermal: String)],
        locale: Locale
    ) -> String {
        let cpu = data.map { $0.cpu }
        let raw = data.map { $0.raw }
        let net = data.map { $0.net }
        let fps = data.map { Double($0.fps) }
        
        func avg(_ v: [Double]) -> Double {
            v.isEmpty ? 0 : v.reduce(0, +) / Double(v.count)
        }
        
        /// Calculates the Sample Standard Deviation using Bessel's correction (N-1).
        /// Guard clause prevents division-by-zero (NaN/Infinity) when only a single frame is logged.
        func std(_ v: [Double]) -> Double {
            if v.count <= 1 { return 0 }
            let m = avg(v)
            let sumOfSquaredDifferences = v.map { pow($0 - m, 2) }.reduce(0, +)
            return sqrt(sumOfSquaredDifferences / Double(v.count - 1))
        }
        
        func formatStat(_ name: String, _ v: [Double]) -> String {
            let m = avg(v)
            let s = std(v)
            let minVal = v.min() ?? 0
            let maxVal = v.max() ?? 0
            return "\(name),\(String(format: "%.2f", locale: locale, m)),\(String(format: "%.2f", locale: locale, s)),\(String(format: "%.2f", locale: locale, minVal)),\(String(format: "%.2f", locale: locale, maxVal))"
        }
        
        return [
            "Metric,Average,StdDev,Min,Max",
            formatStat("CPU(%)", cpu),
            formatStat("Raw_RAM(MB)", raw),
            formatStat("Net_RAM(MB)", net),
            formatStat("FPS", fps)
        ].joined(separator: "\n")
    }
}