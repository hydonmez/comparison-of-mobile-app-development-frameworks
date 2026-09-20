import SwiftUI

/// A presentation layer for executing storage benchmarks.
///
/// Designed to remain lightweight by isolating volatile state changes (like progress updates) 
/// into dedicated subcomponents, preventing MainActor starvation and frame drops 
/// during intensive 2GB I/O operations.
struct StorageTestView: View {

    @StateObject private var viewModel = StorageTestViewModel()
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 0) {
            
            VStack(spacing: 25) {
                
                // MARK: - Benchmark Status HUD
                VStack(spacing: 12) {
                    Image(systemName: "server.rack")
                        .font(.system(size: 50))
                        .foregroundColor(.orange)

                    Text(viewModel.status)
                        .font(.system(size: 18, weight: .semibold))
                }
                .padding(.top, 20)

                // MARK: - Progress Visualization
                IsolatedProgressView(progress: viewModel.progress)

                // MARK: - Execution Controls
                VStack(spacing: 15) {

                    Button(action: {
                        viewModel.runBenchmark(isWrite: true)
                    }) {
                        HStack {
                            Image(systemName: "pencil")
                            Text("Step 1: Write 2GB Payload").bold()
                        }
                        .frame(maxWidth: .infinity)
                        .padding(16)
                        .background(viewModel.isRunning ? Color.orange.opacity(0.5) : Color.orange)
                        .foregroundColor(.white)
                        .cornerRadius(12)
                    }
                    .disabled(viewModel.isRunning)

                    Button(action: {
                        viewModel.runBenchmark(isWrite: false)
                    }) {
                        HStack {
                            Image(systemName: "book.fill")
                            Text("Step 2: Read 2GB Payload").bold()
                        }
                        .frame(maxWidth: .infinity)
                        .padding(16)
                        .background(Color.clear)
                        .foregroundColor((!viewModel.isRunning && viewModel.isWriteCompleted) ? .orange : .gray)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke((!viewModel.isRunning && viewModel.isWriteCompleted) ? Color.orange : Color.gray.opacity(0.5), lineWidth: 1)
                        )
                    }
                    .disabled(viewModel.isRunning || !viewModel.isWriteCompleted)
                }
                .frame(maxWidth: .infinity)

                // MARK: - Telemetry Export Pipeline
                if viewModel.isReportReady && !viewModel.isRunning {
                    VStack(spacing: 10) {
                        Text("Benchmark Report Ready")
                            .bold()
                        
                        Button(action: {
                            viewModel.shareResults()
                        }) {
                            HStack {
                                Image(systemName: "arrow.down.doc.fill")
                                Text("Save Results")
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(Color.green)
                            .foregroundColor(.white)
                            .cornerRadius(10)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(16)
                    .background(Color.gray.opacity(0.1))
                    .cornerRadius(12)
                }
                
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        }
        .navigationTitle("Storage Benchmark")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $viewModel.showShareSheet) {
            if let url = viewModel.exportURL {
                StorageShareSheet(items: [url]).presentationDetents([.medium, .large])
            }
        }
    }
}

/// Encapsulates the progress value in a leaf node to ensure high-frequency 
/// state updates from the ViewModel do not invalidate the parent view tree.
struct IsolatedProgressView: View {
    let progress: Double
    
    var body: some View {
        VStack(spacing: 8) {
            ProgressView(value: progress)
                .progressViewStyle(LinearProgressViewStyle(tint: .orange))
                .scaleEffect(x: 1, y: 2)
                .frame(maxWidth: .infinity)
            
            Text("\(Int(progress * 100))%")
                .font(.system(size: 14, design: .monospaced))
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
    }
}