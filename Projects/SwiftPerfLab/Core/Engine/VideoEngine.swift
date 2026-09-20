import Foundation
import AVKit
import Combine

/// A MainActor-bound video playback engine designed for hardware benchmarking.
/// Governs the internal memory footprint via explicit buffer constraints and exposes a Combine publisher for EOF signals.
@MainActor
final class VideoEngine: ObservableObject {

    @Published private(set) var player: AVPlayer?

    /// Signals playback termination upon receiving AVPlayerItemDidPlayToEndTimeNotification.
    /// Used to trigger benchmark finalization.
    @Published private(set) var hasEnded: Bool = false

    // Uses AnyCancellable to ensure Swift 6 Sendable closure compliance and automatic deregistration on deallocation.
    private var cancellables = Set<AnyCancellable>()

    init() {
        configureAudioSession()
    }

    /// Configures the shared AVAudioSession for background-compatible video playback.
    /// Isolated to prevent partial session states if setCategory or setActive throws an error.
    private func configureAudioSession() {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playback)
            try session.setActive(true)
        } catch {
            print("AVAudioSession configuration error: \(error)")
        }
    }

    /// Prepares a video asset for playback with explicit buffer management.
    ///
    /// - Parameters:
    ///   - name: The filename of the video asset in the main bundle.
    ///   - ext: The file extension (e.g., "mp4").
    func prepareVideo(named name: String, ext: String) {
        guard let url = Bundle.main.url(forResource: name, withExtension: ext) else {
            print("Asset not found: \(name).\(ext)")
            return
        }

        let item = AVPlayerItem(url: url)

        // Limits speculative OS-level caching to 5.0 seconds so memory measurements
        // reflect active hardware decoding rather than prefetch activity.
        item.preferredForwardBufferDuration = 5.0

        if let existingPlayer = self.player {
            existingPlayer.replaceCurrentItem(with: item)
        } else {
            let newPlayer = AVPlayer(playerItem: item)
            // Forces immediate decoder initialization to measure First Meaningful Paint (FMP) latency accurately.
            newPlayer.automaticallyWaitsToMinimizeStalling = false
            self.player = newPlayer
        }

        // Deregister any prior EOF binding before attaching to the new item.
        clearObserver()

        // Establishes a deterministic EOF signal using Combine.
        // `.receive(on: RunLoop.main)` bridges the notification safely into the MainActor's execution pool.
        NotificationCenter.default.publisher(for: .AVPlayerItemDidPlayToEndTime, object: item)
            .receive(on: RunLoop.main)
            .sink { [weak self] _ in
                self?.hasEnded = true
            }
            .store(in: &cancellables)
    }

    func playFromStart() {
        hasEnded = false
        player?.seek(to: .zero)
        player?.play()
    }

    func stop() {
        player?.pause()
    }

    /// Destroys the AVPlayer instance and flushes hardware decoder buffers.
    /// Prevents residual cache from inflating memory telemetry in subsequent runs.
    func purgeMemory() {
        player?.pause()
        player?.replaceCurrentItem(with: nil)
        clearObserver()
        player = nil
        hasEnded = false
    }

    private func clearObserver() {
        // Automatically cancels all active Combine subscriptions.
        cancellables.removeAll()
    }
}