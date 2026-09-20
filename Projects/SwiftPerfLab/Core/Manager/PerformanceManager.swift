import Foundation
import UIKit
import QuartzCore
import Combine
import Darwin

/// Defines telemetry resolution profiles.
/// - Macro: 1.0s intervals with a 2.0s warm-up filter for prolonged algorithmic tasks.
/// - Micro: 0.25s intervals with no warm-up filter for volatile I/O-bound tasks.
enum BenchmarkType {
    case macro
    case micro
}

/// A thread-safe telemetry engine utilizing CADisplayLink for temporal accuracy.
///
/// Uses stateless Mach Thread Summation instead of POSIX `getrusage` to provide accurate,
/// granular CPU metrics without higher-level API overhead, establishing a strict performance baseline.
@MainActor
final class PerformanceManager: ObservableObject {
    static let shared = PerformanceManager()

    @Published var isMeasuring = false
    @Published var currentLogs: [PerformanceLog] = []
    @Published var currentFPS: Int = 0
    @Published var thermalStateString: String = "Nominal"

    private var displayLink: CADisplayLink?
    private var lastTimestamp: CFTimeInterval = 0
    private var testStartTime: CFTimeInterval = 0
    private var lastMetricCaptureTime: CFTimeInterval = 0
    private var frameCount: Int = 0
    private var baselineRAM: Double = 0
    
    private var currentTestType: BenchmarkType = .macro
    
    // A dedicated background queue offloads heavy Mach/POSIX kernel calls
    // to prevent Main Thread starvation during high-frequency hardware polling.
    private let metricsQueue = DispatchQueue(label: "com.perflab.telemetry", qos: .userInitiated)

    private init() {}

    func startMonitoring(type: BenchmarkType = .macro) {
        stopMonitoring()
        
        UIDevice.current.isBatteryMonitoringEnabled = true
        isMeasuring = true
        currentTestType = type

        currentLogs.removeAll(keepingCapacity: false)
        currentLogs.reserveCapacity(2000)

        currentFPS = 0
        baselineRAM = getSafeRAMUsage()

        displayLink = CADisplayLink(target: self, selector: #selector(onFrameTick))
        displayLink?.add(to: .main, forMode: .common)
    }

    func stopMonitoring() {
        displayLink?.invalidate()
        displayLink = nil
        isMeasuring = false
    }

    @objc private func onFrameTick(sender: CADisplayLink) {
        if lastTimestamp == 0 {
            lastTimestamp = sender.timestamp
            lastMetricCaptureTime = sender.timestamp
            testStartTime = sender.timestamp
            return
        }

        frameCount += 1
        let deltaFromLastCapture = sender.timestamp - lastMetricCaptureTime
        let totalElapsedTestTime = sender.timestamp - testStartTime

        // Dynamic thresholding based on the active benchmark profile
        let targetInterval: Double = (currentTestType == .micro) ? 0.25 : 1.0
        let warmupThreshold: Double = (currentTestType == .micro) ? 0.0 : 2.0

        if deltaFromLastCapture >= targetInterval {
            let snapshotFPS = Int(Double(frameCount) / deltaFromLastCapture)
            currentFPS = snapshotFPS
            frameCount = 0
            lastMetricCaptureTime = sender.timestamp
            
            if totalElapsedTestTime >= warmupThreshold {
                // Offload heavy hardware queries to the background queue.
                metricsQueue.async { [weak self] in
                    guard let self = self else { return }
                    
                    // These nonisolated methods execute safely on the background thread
                    let rawRAM = self.getSafeRAMUsage()
                    let currentCpuUsage = self.getAccurateMachCpuUsage()
                    
                    // Re-route back to MainActor to safely mutate state and access UIKit
                    DispatchQueue.main.async {
                        self.finalizeMetrics(
                            snapshotFPS: snapshotFPS,
                            rawRAM: rawRAM,
                            currentCpuUsage: currentCpuUsage
                        )
                    }
                }
            }
        }
        lastTimestamp = sender.timestamp
    }

    /// Finalizes the metric computation safely on the MainActor, ensuring thread-safe
    /// mutation of the published variables and synchronized UIKit access.
    private func finalizeMetrics(snapshotFPS: Int, rawRAM: Double, currentCpuUsage: Double) {
        // Fetch Battery & Thermal states (Safe to call on Main Thread as they are UIKit properties)
        let batteryLevel = UIDevice.current.batteryLevel
        let battery: Double = batteryLevel >= 0 ? Double(round(batteryLevel * 100) * 1) : 0.0
        
        updateThermalState()

        // Construct and append the log payload
        let log = PerformanceLog(
            timestamp: Date().timeIntervalSince1970,
            cpuUsage: currentCpuUsage,
            rawRAM: rawRAM,
            netRAM: max(0, rawRAM - baselineRAM),
            batteryLevel: battery,
            fps: snapshotFPS,
            thermalState: thermalStateString
        )
        currentLogs.append(log)
    }

    // MARK: - NON-ISOLATED HARDWARE KERNEL QUERIES
    
    /// Calculates absolute CPU percentage by iterating over all active Mach threads.
    /// The `nonisolated` keyword allows synchronous execution within background queues,
    /// decoupling heavy kernel queries from the MainActor.
    nonisolated private func getAccurateMachCpuUsage() -> Double {
        var thread_list: thread_act_array_t?
        var thread_count: mach_msg_type_number_t = 0
        
        let kr = task_threads(mach_task_self_, &thread_list, &thread_count)
        if kr != KERN_SUCCESS { return 0.0 }
        
        var totalCpuUsage: Double = 0.0
        
        if let thread_list = thread_list {
            for j in 0..<Int(thread_count) {
                var threadInfo = thread_basic_info()
                var threadInfoCount = mach_msg_type_number_t(MemoryLayout<thread_basic_info>.size / MemoryLayout<integer_t>.size)
                
                let infoResult = withUnsafeMutablePointer(to: &threadInfo) {
                    $0.withMemoryRebound(to: integer_t.self, capacity: Int(threadInfoCount)) { ptr in
                        thread_info(thread_list[j], thread_flavor_t(THREAD_BASIC_INFO), ptr, &threadInfoCount)
                    }
                }
                
                if infoResult == KERN_SUCCESS {
                    let isIdle = threadInfo.flags & Int32(TH_FLAGS_IDLE) != 0
                    if !isIdle {
                        totalCpuUsage += (Double(threadInfo.cpu_usage) / Double(TH_USAGE_SCALE)) * 100.0
                    }
                }
            }
            // Explicitly deallocates the thread list from virtual memory to prevent memory leaks during high-frequency polling.
            let size = vm_size_t(Int(thread_count) * MemoryLayout<thread_t>.stride)
            vm_deallocate(mach_task_self_, vm_address_t(bitPattern: thread_list), size)
        }
        
        return round(totalCpuUsage * 10.0) / 10.0
    }

    /// Bypasses OS caching to fetch the raw physical footprint via Mach `TASK_VM_INFO`.
    /// The `nonisolated` keyword permits synchronous execution within background queues.
    nonisolated private func getSafeRAMUsage() -> Double {
        var info = task_vm_info_data_t()
        var count = mach_msg_type_number_t(MemoryLayout<task_vm_info_data_t>.size / MemoryLayout<integer_t>.size)
        
        let result = withUnsafeMutablePointer(to: &info) {
            $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) { ptr in
                task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), ptr, &count)
            }
        }
        
        if result == KERN_SUCCESS {
            let footprintMb = Double(info.phys_footprint) / (1024.0 * 1024.0)
            return round(footprintMb * 10.0) / 10.0
        }
        return 0.0
    }

    private func updateThermalState() {
        switch ProcessInfo.processInfo.thermalState {
        case .nominal:  thermalStateString = "Nominal"
        case .fair:     thermalStateString = "Fair"
        case .serious:  thermalStateString = "Serious"
        case .critical: thermalStateString = "Critical"
        @unknown default: thermalStateString = "Nominal"
        }
    }
}