import Foundation
import Combine
import AVFoundation

/// A MainActor-bound ViewModel that manages the audio playback lifecycle and UI synchronization.
/// Interfaces between the decoupled AudioEngine and AudioTestView, handling asynchronous asset loading, 
/// playback state mutations, and hardware telemetry binding to keep the view logic-free.
@MainActor
final class AudioTestViewModel: ObservableObject {
    
    // MARK: - Reactive UI State
    
    @Published var isTesting = false
    @Published var showShareSheet = false
    @Published var exportURL: URL?
    
    // Diagnostic & Load State Management
    @Published var isAudioLoaded = false
    @Published var errorMessage: String? = nil
    
    // MARK: - Engine Telemetry Streams
    
    @Published var currentTime: Double = 0
    @Published var totalDuration: Double = 0
    @Published var isPlaying = false
    
    private let engine = AudioEngine.shared
    private let perfManager = PerformanceManager.shared
    
    // Uses AnyCancellable for Combine subscriptions to prevent Swift 6 Sendable closure violations 
    // and ensure automatic deregistration upon deallocation.
    private var cancellables = Set<AnyCancellable>()
    
    init() {
        setupBindings()
        loadTestAudio()
        setupNotificationObserver()
    }
    
    /// Binds the AudioEngine state to the ViewModel's published properties.
    /// Uses assign(to: &$) syntax via Combine to prevent memory leaks (retain cycles) 
    /// that can occur with the older .assign(to:on:) pattern.
    private func setupBindings() {
        engine.$currentTime.assign(to: &$currentTime)
        engine.$totalDuration.assign(to: &$totalDuration)
        engine.$isPlaying.assign(to: &$isPlaying)
    }
    
    /// Subscribes to AVFoundation notifications to terminate the benchmark reliably.
    /// Uses Combine .publisher to safely execute on the MainActor, resolving Sendable closure warnings.
    private func setupNotificationObserver() {
        NotificationCenter.default.publisher(for: .AVPlayerItemDidPlayToEndTime)
            .receive(on: RunLoop.main) // Explicitly enforces MainActor boundary
            .sink { [weak self] _ in
                guard let self = self, self.isTesting else { return }
                print("[AudioTestViewModel] EOF Detected. Gracefully terminating the active benchmark suite.")
                self.stopTest()
            }
            .store(in: &cancellables)
    }
   
    /// Eagerly loads the benchmark audio asset asynchronously, bypassing Main Thread stalling.
    func loadTestAudio() {
        guard let url = Bundle.main.url(forResource: "test_audio_high", withExtension: "mp3") else {
            self.errorMessage = "Asset Missing: 'test_audio_high.mp3' could not be located in the main bundle."
            self.isAudioLoaded = false
            return
        }
        
        // Uses [weak self] to prevent strong reference cycles if the view is dismissed 
        // while the async I/O read is in flight.
        Task { [weak self] in
            let success = await self?.engine.loadAudio(url: url) ?? false
            self?.isAudioLoaded = success
            
            if !success {
                self?.errorMessage = "Asset Corrupted: The audio file is unreadable or unsupported by AVFoundation."
            }
        }
    }
    
    // MARK: - Benchmark Actions
    
    /// Initiates the automated hardware benchmark sequence and synchronizes telemetry polling.
    func startTest() {
        guard isAudioLoaded else {
            print("[AudioTestViewModel] Benchmark Initialization Failed: Audio asset is not loaded into memory.")
            return
        }
        
        // Loop mitigation: Resets the playhead if the test is initiated at the End-Of-File.
        if currentTime >= totalDuration - 0.1 {
            engine.seek(to: 0)
        }
        
        perfManager.startMonitoring()
        engine.play()
        isTesting = true
    }
    
    /// Terminates the active benchmark, purges monitoring states, and serializes hardware telemetry to CSV.
    func stopTest() {
        guard isTesting else { return }
        
        engine.pause()
        isTesting = false
        perfManager.stopMonitoring()
        
        // Wraps the async generateCSV function in a detached Task to move file I/O off the UI thread, 
        // satisfying strict concurrency protocols.
        Task { [weak self] in
            guard let self = self else { return }
            do {
                let generatedURL = try await ExportManager.shared.generateCSV(
                    from: self.perfManager.currentLogs,
                    testName: "Audio_Native"
                )
                
                // Context switch back to the MainActor to safely update UI state triggers.
                await MainActor.run {
                    self.exportURL = generatedURL
                    self.showShareSheet = true
                }
                
            } catch {
                print("[AudioTestViewModel] Audio Telemetry Export Failed: \(error.localizedDescription)")
            }
        }
    }
    
    /// Seeks the playback head to a specific timeline coordinate.
    func seekAudio(to time: Double) {
        guard isAudioLoaded else { return }
        engine.seek(to: time)
        
        // Failsafe: Automatically terminates the test if the user scrubs to the exact end of the file.
        if isTesting && totalDuration > 0 && time >= (totalDuration - 0.2) {
            stopTest()
        }
    }
    
    /// Jumps the playback head incrementally while validating EOF boundaries.
    func skip(by seconds: Double) {
        guard isAudioLoaded else { return }
        engine.skip(by: seconds)
        
        if isTesting && totalDuration > 0 && engine.currentTime >= (totalDuration - 0.2) {
            stopTest()
        }
    }
    
    /// Formats raw Double seconds into a standard MM:SS string structure for UI presentation.
    func formatTime(_ time: Double) -> String {
        guard !time.isNaN && !time.isInfinite else { return "00:00" }
        let minutes = Int(time) / 60
        let seconds = Int(time) % 60
        return String(format: "%02d:%02d", minutes, seconds)
    }
}