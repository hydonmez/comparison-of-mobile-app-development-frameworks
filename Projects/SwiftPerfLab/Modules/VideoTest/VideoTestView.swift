import SwiftUI
import AVKit

/// A SwiftUI view for hardware-accelerated video decoding benchmarks.
///
/// Uses dynamic view routing to isolate the full-screen rendering surface
/// from the idle preparation state. This separation prevents the UI thread
/// from executing unnecessary render passes under heavy GPU load.
struct VideoTestView: View {
    @StateObject private var viewModel = VideoTestViewModel()
    @Environment(\.presentationMode) var presentationMode
    
    var body: some View {
        Group {
            if viewModel.isFullScreen {
                VideoExecutionSurface(viewModel: viewModel)
            } else {
                IdlePreparationUI(
                    viewModel: viewModel,
                    onNavigateBack: { presentationMode.wrappedValue.dismiss() }
                )
            }
        }
        .onAppear {
            viewModel.prepareVideo()
        }
        .onDisappear {
            viewModel.stopTestAndExport(isFinished: false)
            // Enforces a portrait orientation lock upon view dismissal to maintain UI stability.
            viewModel.forceRotation(isLandscape: false)
        }
        // Export functionality is handled directly via ShareLink to reduce memory overhead.
        .navigationBarHidden(true)
    }
}

/// Provides a dedicated rendering context for the native video pipeline,
/// ensuring the SwiftUI view hierarchy does not interfere with hardware decoding.
struct VideoExecutionSurface: View {
    @ObservedObject var viewModel: VideoTestViewModel
    
    var body: some View {
        ZStack(alignment: .topLeading) {
            Color.black.ignoresSafeArea()
            
            if let player = viewModel.engine.player {
                VideoPlayer(player: player)
                    .ignoresSafeArea()
            }
            
            Button {
                viewModel.stopTestAndExport(isFinished: false)
            } label: {
                Text("Terminate Benchmark")
                    .font(.system(size: 12))
                    .foregroundColor(.white)
                    .padding()
                    .background(Color.red.opacity(0.85))
                    .cornerRadius(8)
            }
            .padding(.top, 50)
            .padding(.leading, 20)
            
            VStack {
                HStack {
                    Spacer()
                    FPSIndicator()
                        .padding(.top, 50)
                        .padding(.trailing, 20)
                }
                Spacer()
            }
        }
        // Triggers OS-level hardware orientation overrides when the view appears.
        .onAppear {
            viewModel.forceRotation(isLandscape: true)
        }
        .onDisappear {
            viewModel.forceRotation(isLandscape: false)
        }
    }
}

/// Initial configuration state for benchmark preparation and post-test data export.
struct IdlePreparationUI: View {
    @ObservedObject var viewModel: VideoTestViewModel
    var onNavigateBack: () -> Void
    
    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onNavigateBack) {
                    Image(systemName: "arrow.left")
                        .foregroundColor(.primary)
                        .font(.system(size: 20, weight: .semibold))
                }
                
                Text("Video Benchmark")
                    .font(.system(size: 20, weight: .bold))
                    .padding(.leading, 8)
                
                Spacer()
            }
            .padding(.top, 16)
            .padding(.horizontal, 16)
            
            VStack(spacing: 20) {
                Spacer()
                
                Image(systemName: "play.fill")
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 100, height: 100)
                    .foregroundColor(Color(red: 0, green: 0.478, blue: 1.0))
                
                Spacer().frame(height: 20)
                
                Text("Full Screen Video Benchmark")
                    .font(.system(size: 22, weight: .bold))
                
                Spacer().frame(height: 10)
                
                Text("The video plays from start to finish while hardware telemetry is recorded. The display will be locked to landscape orientation for high-fidelity decoding.")
                    .foregroundColor(.gray)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 16)
                
                Spacer().frame(height: 40)
                
                if viewModel.isReportReady, let url = viewModel.exportURL {
                    VStack(spacing: 10) {
                        Text("Benchmark Report Ready")
                            .fontWeight(.bold)
                        
                        // Uses SwiftUI ShareLink to present the export menu as a half-screen bottom sheet.
                        ShareLink(item: url) {
                            HStack {
                                Image(systemName: "square.and.arrow.up")
                                Text("Export Results (CSV)")
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 12)
                            .background(Color(red: 0.204, green: 0.78, blue: 0.349))
                            .foregroundColor(.white)
                            .cornerRadius(10)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(16)
                    .background(Color.gray.opacity(0.1))
                    .cornerRadius(12)
                }
                
                Spacer().frame(height: 40)
                
                Button {
                    viewModel.startTest()
                } label: {
                    Text("START FULL SCREEN BENCHMARK")
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .background(Color(red: 0, green: 0.478, blue: 1.0))
                        .cornerRadius(12)
                }
                .padding(.horizontal, 16)
                
                Spacer()
            }
            .padding(16)
        }
        .navigationTitle("Video Performance")
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// Visualizes VSYNC-synchronized frame frequency provided by the PerformanceManager.
struct FPSIndicator: View {
    @ObservedObject private var perfManager = PerformanceManager.shared
    
    var body: some View {
        HStack(spacing: 4) {
            Text("FPS: \(perfManager.currentFPS)")
                .font(.system(size: 14, design: .monospaced))
                .bold()
                .foregroundColor(perfManager.currentFPS < 50 ? .red : .green)
        }
        .padding(8)
        .background(Color.black.opacity(0.6))
        .cornerRadius(8)
    }
}