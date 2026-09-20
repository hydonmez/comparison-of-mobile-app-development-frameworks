import SwiftUI
import UIKit

@main
struct SwiftBenchmarkApp: App {
    
    // Bridges SwiftUI lifecycle with UIKit to enforce hardware constraints like 
    // UIInterfaceOrientation locking. This prevents unprompted redraws that could skew benchmark metrics.
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    
    // Process Initialization:
    // The earliest hook into the SwiftUI lifecycle. Marks the baseline timestamp for 
    // Cold Start telemetry right after dyld completes memory mapping.
    init() {
        LaunchPerformanceManager.shared.appStarted()
    }

    // Tracks OS scene state transitions (Foreground, Background, Inactive) for lifecycle profiling.
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            MainDashboardView()
                .onAppear {
                    // Triggered when the OS allocates the initial view tree in memory, 
                    // marking the completion of structural layout computation before the GPU render pass.
                    LaunchPerformanceManager.shared.osReady()
                    
                    // Records First Meaningful Paint (FMP) and Time to Interactive (TTI).
                    // Asynchronous dispatch ensures telemetry is recorded after the GPU finishes 
                    // rendering the initial frame.
                    DispatchQueue.main.async {
                        LaunchPerformanceManager.shared.reportBenchmark()
                    }
                }
        }
        // Monitors process transitions to calculate Hot Start latency.
        .onChange(of: scenePhase) { oldPhase, newPhase in
            if oldPhase == .background && newPhase == .inactive {
                // Hot Start Initiation: OS restores the suspended process to foreground memory.
                LaunchPerformanceManager.shared.appIsWakingUp()
            } else if oldPhase == .inactive && newPhase == .active {
                // Hot Start Resolution: Application is fully restored and interactive.
                LaunchPerformanceManager.shared.hotStartDetected()
            }
        }
    }
}

// MARK: - Hardware Policy Interceptor

/// Bridging delegate to control low-level device policies.
class AppDelegate: NSObject, UIApplicationDelegate {
    
    // Enforces strict portrait screen orientation to isolate CPU/GPU benchmarks 
    // from hardware interrupts caused by device rotation.
    static var orientationLock = UIInterfaceOrientationMask.portrait
    
    func application(_ application: UIApplication, supportedInterfaceOrientationsFor window: UIWindow?) -> UIInterfaceOrientationMask {
        return AppDelegate.orientationLock
    }
}