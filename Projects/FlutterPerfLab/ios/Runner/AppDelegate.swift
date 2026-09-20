import UIKit
import Flutter
import Darwin
import GoogleMaps

@UIApplicationMain
@objc class AppDelegate: FlutterAppDelegate {
    
    override func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        
        // Required to read valid battery levels.
        UIDevice.current.isBatteryMonitoringEnabled = true
        
        guard let registrar = self.registrar(forPlugin: "hardware_channel") else {
            return super.application(application, didFinishLaunchingWithOptions: launchOptions)
        }
        
        let hardwareChannel = FlutterMethodChannel(
            name: "com.benchmark.hardware",
            binaryMessenger: registrar.messenger()
        )
        
        hardwareChannel.setMethodCallHandler({ [weak self] (call: FlutterMethodCall, result: @escaping FlutterResult) -> Void in
            
            guard call.method == "getHardwareMetrics" else {
                result(FlutterMethodNotImplemented)
                return
            }
            
            guard let self = self else {
                result(FlutterError(code: "UNAVAILABLE", message: "AppDelegate is nil", details: nil))
                return
            }
            
            // Capture UI and System properties on the Main Thread to avoid thread-safety violations.
            let batteryLevel = UIDevice.current.batteryLevel
            let thermalStateString = self.getThermalStateString()
            
            // Offload heavy Mach kernel queries to a background queue to prevent UI frame drops.
            DispatchQueue.global(qos: .userInitiated).async { [weak self] in
                guard let self = self else {
                    DispatchQueue.main.async { result(nil) }
                    return
                }
                
                let currentCpuUsage = self.getAccurateMachCpuUsage()
                let rawRAM = self.getSafeRAMUsage()
                
                let batteryPercentage: Double = batteryLevel >= 0 ? Double(round(batteryLevel * 100)) : -1.0
                
                let hardwareData: [String: Any] = [
                    "cpu": currentCpuUsage,
                    "ram": rawRAM,
                    "battery": batteryPercentage,
                    "thermal": thermalStateString
                ]
                
                // Route the execution back to the Main Thread to safely return the result to Flutter.
                DispatchQueue.main.async {
                    result(hardwareData)
                }
            }
        })
        
        GeneratedPluginRegistrant.register(with: self)
        return super.application(application, didFinishLaunchingWithOptions: launchOptions)
    }
    
    // MARK: - Hardware Telemetry Methods
    
    /// Calculates the absolute CPU percentage by aggregating the usage of all active Mach threads.
    /// Excludes idle threads to prevent artificial inflation of CPU metrics.
    private func getAccurateMachCpuUsage() -> Double {
        var threadList: thread_act_array_t?
        var threadCount: mach_msg_type_number_t = 0
        
        let kr = task_threads(mach_task_self_, &threadList, &threadCount)
        guard kr == KERN_SUCCESS, let threads = threadList else {
            return 0.0
        }
        
        var totalCpuUsage: Double = 0.0
        
        for j in 0..<Int(threadCount) {
            var threadInfo = thread_basic_info()
            var threadInfoCount = mach_msg_type_number_t(MemoryLayout<thread_basic_info>.size / MemoryLayout<integer_t>.size)
            
            let infoResult = withUnsafeMutablePointer(to: &threadInfo) {
                $0.withMemoryRebound(to: integer_t.self, capacity: Int(threadInfoCount)) { ptr in
                    thread_info(threads[j], thread_flavor_t(THREAD_BASIC_INFO), ptr, &threadInfoCount)
                }
            }
            
            if infoResult == KERN_SUCCESS {
                let isIdle = threadInfo.flags & Int32(TH_FLAGS_IDLE) != 0
                if !isIdle {
                    totalCpuUsage += (Double(threadInfo.cpu_usage) / Double(TH_USAGE_SCALE)) * 100.0
                }
            }
        }
        
        // Critical: Deallocate the Mach thread array to prevent memory leaks.
        let size = vm_size_t(Int(threadCount) * MemoryLayout<thread_t>.stride)
        vm_deallocate(mach_task_self_, vm_address_t(bitPattern: threads), size)
        
        return round(totalCpuUsage * 10.0) / 10.0
    }
    
    /// Bypasses high-level OS caching to fetch the raw physical memory footprint via Mach TASK_VM_INFO.
    /// Returns the value in Megabytes (MB).
    private func getSafeRAMUsage() -> Double {
        var info = task_vm_info_data_t()
        var count = mach_msg_type_number_t(MemoryLayout<task_vm_info_data_t>.size / MemoryLayout<integer_t>.size)
        
        let result = withUnsafeMutablePointer(to: &info) {
            $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) { ptr in
                task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), ptr, &count)
            }
        }
        
        guard result == KERN_SUCCESS else {
            return 0.0
        }
        
        let footprintMb = Double(info.phys_footprint) / (1024.0 * 1024.0)
        return round(footprintMb * 10.0) / 10.0
    }
    
    /// Evaluates the device's current thermal condition.
    private func getThermalStateString() -> String {
        switch ProcessInfo.processInfo.thermalState {
        case .nominal: return "Nominal"
        case .fair: return "Fair"
        case .serious: return "Serious"
        case .critical: return "Critical"
        @unknown default: return "Unknown"
        }
    }
}