import SwiftUI
import AVKit

/// A dedicated presentation layer for monitoring audio playback benchmarks.
///
/// Decouples the high-frequency hardware telemetry stream (FPS/Thermal states) from the 
/// visual rendering tree to prevent the UI from skewing benchmark results via redundant layout invalidations.
struct AudioTestView: View {
    @StateObject private var viewModel = AudioTestViewModel()
    
    // High-frequency PerformanceManager observations are encapsulated within the TelemetryHUDLayer 
    // to prevent unnecessary recomposition in the parent view.
    
    var body: some View {
        VStack(spacing: 25) {
            
            // MARK: - Visual Media Placeholder
            // Static rendering zone. Unaffected by real-time telemetry updates.
            ZStack {
                RoundedRectangle(cornerRadius: 20)
                    .fill(LinearGradient(gradient: Gradient(colors: [.orange, .pink]), startPoint: .topLeading, endPoint: .bottomTrailing))
                    .frame(width: 250, height: 250)
                    .shadow(color: .pink.opacity(0.4), radius: 15, x: 0, y: 10)
                
                Image(systemName: "music.note.list")
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 100, height: 100)
                    .foregroundColor(.white)
            }
            .padding(.top, 30)
            
            // MARK: - Title & Diagnostic Errors
            VStack(spacing: 8) {
                if let error = viewModel.errorMessage {
                    Text("⚠️ Error: \(error)")
                        .font(.subheadline).bold()
                        .foregroundColor(.white)
                        .padding()
                        .background(Color.red)
                        .cornerRadius(8)
                } else {
                    Text("Native AVPlayer Benchmark")
                        .font(.title2).bold()
                    
                    Text("Engine: AVFoundation (Optimized)")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .padding(6)
                        .background(.ultraThinMaterial)
                        .cornerRadius(8)
                }
            }
            
            // MARK: - Playback Timeline
            VStack(spacing: 10) {
                Slider(value: Binding(
                    get: { viewModel.currentTime },
                    set: { viewModel.seekAudio(to: $0) }
                ), in: 0...max(viewModel.totalDuration, 1))
                .tint(.orange)
                .disabled(!viewModel.isAudioLoaded)
                
                HStack {
                    Text(viewModel.formatTime(viewModel.currentTime))
                    Spacer()
                    Text(viewModel.formatTime(viewModel.totalDuration))
                }
                .font(.caption)
                .monospacedDigit()
                .foregroundColor(.secondary)
            }
            .padding(.horizontal, 30)
            
            // MARK: - Transport Controls
            HStack(spacing: 40) {
                Button { viewModel.skip(by: -15) } label: {
                    Image(systemName: "gobackward.15").font(.largeTitle)
                }
                .disabled(!viewModel.isAudioLoaded)
                
                Button {
                    if viewModel.isPlaying {
                        viewModel.stopTest()
                    } else {
                        viewModel.startTest()
                    }
                } label: {
                    Image(systemName: viewModel.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                        .resizable()
                        .frame(width: 80, height: 80)
                        .foregroundColor(viewModel.isAudioLoaded ? .orange : .gray)
                        .shadow(radius: 5)
                }
                .disabled(!viewModel.isAudioLoaded)
                
                Button { viewModel.skip(by: 15) } label: {
                    Image(systemName: "goforward.15").font(.largeTitle)
                }
                .disabled(!viewModel.isAudioLoaded)
            }
            .foregroundColor(.primary)
            
            // MARK: - Hardware Telemetry Overlay
            TelemetryHUDLayer(isTesting: viewModel.isTesting)
            
            Spacer()
        }
        .navigationTitle("Audio Performance")
        .onDisappear {
            viewModel.stopTest()
        }
        // Programmatically presents the export sheet when the benchmark completes.
        // Uses .presentationDetents to show a half-screen bottom sheet, preserving the underlying visual context.
        .sheet(isPresented: $viewModel.showShareSheet) {
            if let url = viewModel.exportURL {
                ShareSheet(activityItems: [url])
                    // Initializes as a half-sheet, allowing user-driven expansion to full screen if needed.
                    .presentationDetents([.medium, .large])
            }
        }
    }
}

// MARK: - Isolated Subcomponents

struct TelemetryHUDLayer: View {
    @ObservedObject private var perfManager = PerformanceManager.shared
    let isTesting: Bool
    
    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 6) {
                Text("FPS: \(perfManager.currentFPS)")
                    .bold()
                    .font(.system(.body, design: .monospaced))
                    .foregroundColor(perfManager.currentFPS < 50 ? .red : .green)
                
                Text("Thermal: \(perfManager.thermalStateString)")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            Spacer()
            
            if isTesting {
                HStack(spacing: 6) {
                    ProgressView().scaleEffect(0.7)
                    Text("REC")
                        .font(.caption2).bold()
                }
                .padding(8)
                .background(Color.red.opacity(0.1))
                .foregroundColor(.red)
                .cornerRadius(8)
            }
        }
        .padding()
    }
}

/// A declarative SwiftUI wrapper for bridging the imperative `UIActivityViewController`.
struct ShareSheet: UIViewControllerRepresentable {
    var activityItems: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: activityItems, applicationActivities: nil)
    }
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}