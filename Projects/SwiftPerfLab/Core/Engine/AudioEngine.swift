import Foundation
import AVFoundation
import Combine

/// A MainActor-bound audio playback engine designed for hardware benchmarking.
/// Overrides AVFoundation's default buffering heuristics to measure raw I/O throughput and CPU decoding latency.
@MainActor
final class AudioEngine: ObservableObject {

    static let shared = AudioEngine()

    private let player = AVPlayer()
    private var timeObserver: Any?
    
    // Holds the End-Of-File (EOF) notification subscriber to ensure automatic memory management.
    private var cancellables = Set<AnyCancellable>()

    @Published var currentTime: Double = 0
    @Published var totalDuration: Double = 0
    @Published var isPlaying = false

    private init() {
        setupSession()
    }

    private func setupSession() {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playback, mode: .default)
            try session.setActive(true)
        } catch {
            print("[AudioEngine] AVAudioSession configuration failed: \(error.localizedDescription)")
        }
    }

    /// Asynchronously loads the audio asset. Ensures the duration is fully read before
    /// updating the state, preventing NaN or 0 values in `totalDuration`.
    func loadAudio(url: URL) async -> Bool {
        // Resets all internal states before performing heavy I/O operations
        // to prevent displaying stale telemetry data from previous runs.
        release()

        let item = AVPlayerItem(url: url)

        // Limits the forward buffer to 5.0 seconds to standardize memory constraints.
        item.preferredForwardBufferDuration = 5.0

        player.automaticallyWaitsToMinimizeStalling = false
        player.replaceCurrentItem(with: item)

        do {
            let isPlayable = try await item.asset.load(.isPlayable)
            guard isPlayable else { return false }

            let duration = try await item.asset.load(.duration)
            self.totalDuration = CMTimeGetSeconds(duration)

            setupEOFObserver(for: item)
            addTimeObserver()
            
            return true

        } catch {
            print("[AudioEngine] Asset loading failed: \(error.localizedDescription)")
            return false
        }
    }

    func play() {
        player.play()
        isPlaying = true
    }

    func pause() {
        player.pause()
        isPlaying = false
    }

    func seek(to seconds: Double) {
        let time = CMTime(seconds: seconds, preferredTimescale: 600)
        player.seek(to: time, toleranceBefore: .zero, toleranceAfter: .zero)
    }

    func skip(by seconds: Double) {
        let newTime = min(max(currentTime + seconds, 0), totalDuration)
        seek(to: newTime)
    }

    private func addTimeObserver() {
        let interval = CMTime(seconds: 0.1, preferredTimescale: 600)
        
        // Specifying `.main` guarantees execution on the Main Thread.
        // `MainActor.assumeIsolated` safely bridges the older AVFoundation API
        // with Swift 6 strict concurrency without executor hopping.
        timeObserver = player.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
            MainActor.assumeIsolated {
                guard let self = self else { return }
                self.currentTime = time.seconds
                // EOF detection is handled by the dedicated setupEOFObserver method instead of here.
            }
        }
    }

    /// Subscribes to the OS-level AVPlayerItem notification for exact EOF detection,
    /// avoiding floating-point inaccuracies caused by manual chronometer polling.
    private func setupEOFObserver(for item: AVPlayerItem) {
        NotificationCenter.default.publisher(for: .AVPlayerItemDidPlayToEndTime, object: item)
            .receive(on: RunLoop.main)
            .sink { [weak self] _ in
                guard let self = self else { return }
                self.isPlaying = false
                self.currentTime = self.totalDuration // Snap accurately to the end
            }
            .store(in: &cancellables)
    }

    /// Manually purges all hardware listeners and telemetry states.
    /// Ensures strict memory deallocation for the singleton architecture.
    func release() {
        pause()
        
        if let observer = timeObserver {
            player.removeTimeObserver(observer)
            timeObserver = nil
        }
        
        cancellables.removeAll()
        player.replaceCurrentItem(with: nil)
        
        currentTime = 0
        totalDuration = 0
        isPlaying = false
    }
}