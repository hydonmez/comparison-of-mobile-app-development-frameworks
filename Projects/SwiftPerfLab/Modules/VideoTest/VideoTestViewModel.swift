import Foundation
import Combine
import UIKit
import AVFoundation

/// A MainActor-bound ViewModel managing the video playback lifecycle and telemetry.
///
/// Handles hardware-level orientation overrides and asynchronous I/O operations
/// to ensure the view layer remains purely declarative while capturing performance metrics.
@MainActor
final class VideoTestViewModel: ObservableObject {
    
    @Published var isTesting = false
    @Published var isFullScreen = false
    @Published var exportURL: URL?
    @Published var isReportReady = false
    
    private var lastTestSuffix = "Partial"
    
    let engine = VideoEngine()
    private let perfManager = PerformanceManager.shared
    private let exportManager = ExportManager.shared
    private var cancellables = Set<AnyCancellable>()
    
    init() {
        setupBindings()
    }
    
    private func setupBindings() {
        engine.$hasEnded
            .filter { $0 == true }
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.stopTestAndExport(isFinished: true)
            }
            .store(in: &cancellables)
    }
    
    func prepareVideo() {
        isReportReady = false
        engine.prepareVideo(named: "test_video_1080p", ext: "mp4")
    }
    
    func startTest() {
        if engine.player == nil {
            prepareVideo()
        }
        
        isReportReady = false
        exportURL = nil
        isTesting = true
        isFullScreen = true
        
        perfManager.startMonitoring()
        engine.playFromStart()
    }
    
    func stopTestAndExport(isFinished: Bool = false) {
        guard isTesting else { return }
            
        isTesting = false
        isFullScreen = false
            
        engine.stop()
        perfManager.stopMonitoring()
            
        // Restores the portrait orientation lock to maintain UI stability post-benchmark.
        AppDelegate.orientationLock = .portrait
            
        if #available(iOS 16.0, *) {
            if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene {
                windowScene.requestGeometryUpdate(.iOS(interfaceOrientations: .portrait))
            }
        } else {
            UIDevice.current.setValue(UIInterfaceOrientation.portrait.rawValue, forKey: "orientation")
            UIViewController.attemptRotationToDeviceOrientation()
        }
            
        lastTestSuffix = isFinished ? "Complete" : "Partial"
            
        // Asynchronous Telemetry Export
        Task { [weak self] in
            guard let self = self else { return }
            do {
                if let url = try await self.exportManager.generateCSV(
                    from: self.perfManager.currentLogs,
                    testName: "Video_FullScreen_Native_\(self.lastTestSuffix)"
                ) {
                    await MainActor.run {
                        self.exportURL = url
                        // Ensures the UI state is updated only after the file is securely written to storage.
                        self.isReportReady = true
                    }
                }
            } catch {
                print("❌ Video Telemetry Export Failed: \(error.localizedDescription)")
            }
        }
    }
    
    /// Explicitly invalidates the root view controller's orientation cache before
    /// requesting a hardware orientation change. Delays the geometry update request
    /// until after the full-screen presentation animation completes.
    func forceRotation(isLandscape: Bool) {
        // Modern iOS versions prefer specific directional masks when forcing physical rotation.
        let targetMask: UIInterfaceOrientationMask = isLandscape ? .landscapeRight : .portrait
        
        // Updates the global absolute orientation lock within the application delegate.
        AppDelegate.orientationLock = targetMask
        
        // Allows the full-screen presentation animation to enter the view hierarchy before executing the rotation.
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            
            // Acquires the active foreground scene.
            guard let windowScene = UIApplication.shared.connectedScenes.first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene else { return }
            
            // Forces the OS to re-evaluate the application delegate's supported orientations
            // to prevent modern iOS versions from rejecting the geometry update.
            if let rootVC = windowScene.windows.first(where: { $0.isKeyWindow })?.rootViewController {
                rootVC.setNeedsUpdateOfSupportedInterfaceOrientations()
            }
            
            // Executes the hardware geometry override based on the active iOS version.
            if #available(iOS 16.0, *) {
                let preferences = UIWindowScene.GeometryPreferences.iOS(interfaceOrientations: targetMask)
                windowScene.requestGeometryUpdate(preferences) { error in
                    // Logs the exact OS-level rejection reason if the geometry update fails.
                    print("⚠️ OS Rejected Rotation: \(error.localizedDescription)")
                }
            } else {
                // Fallback execution for legacy iOS versions.
                let orientationValue = isLandscape ? UIInterfaceOrientation.landscapeRight.rawValue : UIInterfaceOrientation.portrait.rawValue
                UIDevice.current.setValue(orientationValue, forKey: "orientation")
                UIViewController.attemptRotationToDeviceOrientation()
            }
        }
    }
}