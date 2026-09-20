import Foundation
import UIKit
import React

// React Native bridge for Hardware Telemetry.
// Exposes Darwin/Mach kernel metrics to the JS runtime.
@objc(HardwareTelemetryModule)
class HardwareTelemetryModule: NSObject {

    @objc static func requiresMainQueueSetup() -> Bool {
        return false
    }

    @objc func getOptimizedRAMUsage(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        var info = task_vm_info_data_t()
        var count = mach_msg_type_number_t(MemoryLayout<task_vm_info_data_t>.size / MemoryLayout<integer_t>.size)
        
        let result = withUnsafeMutablePointer(to: &info) {
            $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) { ptr in
                task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), ptr, &count)
            }
        }
        
        if result == KERN_SUCCESS {
            let footprintMb = Double(info.phys_footprint) / (1024.0 * 1024.0)
            resolve(round(footprintMb * 10.0) / 10.0)
        } else {
            reject("ERR_TASK_INFO", "Failed to retrieve task info", nil)
        }
    }

    @objc func syncCpuBaseline(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        resolve(nil)
    }

    @objc func getProcessCpuUsage(_ deltaSeconds: Double, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        var thread_list: thread_act_array_t?
        var thread_count: mach_msg_type_number_t = 0
        
        let kr = task_threads(mach_task_self_, &thread_list, &thread_count)
        if kr != KERN_SUCCESS {
            reject("ERR_CPU_USAGE", "Failed to get thread list", nil)
            return
        }
        
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
            let size = vm_size_t(Int(thread_count) * MemoryLayout<thread_t>.stride)
            vm_deallocate(mach_task_self_, vm_address_t(bitPattern: thread_list), size)
        }
        
        resolve(round(totalCpuUsage * 10.0) / 10.0)
    }

    @objc func getBatteryLevel(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        DispatchQueue.main.async {
            UIDevice.current.isBatteryMonitoringEnabled = true
            let batteryLevel = UIDevice.current.batteryLevel
            if batteryLevel < 0 {
                reject("ERR_BATTERY", "Battery info unavailable", nil)
            } else {
                resolve(Double(round(batteryLevel * 100)))
            }
        }
    }

    @objc func getThermalStateString(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        let stateStr: String
        switch ProcessInfo.processInfo.thermalState {
        case .nominal:  stateStr = "Nominal"
        case .fair:     stateStr = "Fair"
        case .serious:  stateStr = "Serious"
        case .critical: stateStr = "Critical"
        @unknown default: stateStr = "Nominal"
        }
        resolve(stateStr)
    }
}