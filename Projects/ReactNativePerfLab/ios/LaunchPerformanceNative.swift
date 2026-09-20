// ios/ReactNativePerfLab/LaunchPerformanceNative.swift

import Foundation
import React

// Telemetry bridge module and event emitter for exporting OS-level timestamps.
@objc(LaunchPerformanceNative)
class LaunchPerformanceNative: RCTEventEmitter {
    
    private var hasListeners = false
    
    override init() {
        super.init()
        
        // Register listener for app foreground transitions (Hot Starts)
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleAppDidBecomeActive),
            name: UIApplication.didBecomeActiveNotification,
            object: nil
        )
    }
    
    deinit {
        NotificationCenter.default.removeObserver(self)
    }
    
    @objc
    override static func requiresMainQueueSetup() -> Bool {
        return true
    }
    
    override func supportedEvents() -> [String]! {
        return ["onHotStartInitiated"]
    }
    
    // MARK: - Event Emission Safety Checks
    
    override func startObserving() {
        hasListeners = true
    }
    
    override func stopObserving() {
        hasListeners = false
    }
    
    // Triggered by NotificationCenter when the app enters the foreground.
    @objc
    func handleAppDidBecomeActive() {
        if hasListeners {
            let hotStartEpochMs = AppDelegate.getHighResEpochMs()
            sendEvent(withName: "onHotStartInitiated", body: hotStartEpochMs)
        }
    }
    
    // Exports the cold start initialization timestamp synchronously during JS boot.
    @objc
    override func constantsToExport() -> [AnyHashable: Any]! {
        return [
            "osStartEpochMs": AppDelegate.osStartEpochMs
        ]
    }
}