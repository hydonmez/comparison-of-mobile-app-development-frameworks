import SwiftUI
import ComposeApp // KMP module

@main
struct iOSApp: App {

    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    // Monitors OS-level process transitions to calculate Hot Start latency with high precision.
    @Environment(\.scenePhase) private var scenePhase

    init() {
        //  Process Initialization & Bootstrap
        LaunchPerformanceManager.shared.appStarted()
    }

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.all)
                .onAppear {
                    // View Hierarchy Allocation
                    LaunchPerformanceManager.shared.osReady()
                }
        }
        .onChange(of: scenePhase) { oldPhase, newPhase in
            if oldPhase == .background && newPhase == .inactive {
                LaunchPerformanceManager.shared.appIsWakingUp()
            } else if oldPhase == .inactive && newPhase == .active {
                LaunchPerformanceManager.shared.hotStartDetected()
            }
        }
    }
}

// SwiftUI wrapper for the KMP Compose View Controller
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        return MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

// MARK: - Hardware Policy Interceptor (AppDelegate)
class AppDelegate: NSObject, UIApplicationDelegate {

    // Directly observing the Kotlin (ComposeApp) state instead of maintaining a separate Swift static variable.
    func application(_ application: UIApplication, supportedInterfaceOrientationsFor window: UIWindow?) -> UIInterfaceOrientationMask {
        
        // Read the OrientationState object from the KMP layer
        if OrientationState.shared.isLandscape {
            return .landscapeRight
        } else {
            return .portrait
        }
    }
}