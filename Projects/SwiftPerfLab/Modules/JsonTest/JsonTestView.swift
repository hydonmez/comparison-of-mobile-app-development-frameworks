import SwiftUI

/// A presentation layer for evaluating JSON deserialization performance.
///
/// The UI is entirely decoupled from the synchronous parsing loop to prevent layout recalculations 
/// from affecting hardware telemetry during intensive CPU operations.
struct JsonTestView: View {
    
    @StateObject private var viewModel = JsonTestViewModel()
    
    var body: some View {
        VStack(spacing: 25) {
            headerSection
            
            VStack(spacing: 15) {
                // Uses a monospaced font to prevent horizontal layout jitter during high-frequency telemetry updates.
                Text(viewModel.status)
                    .font(.system(.subheadline, design: .monospaced))
                    .padding()
                    .frame(maxWidth: .infinity)
                    .background(Color(.secondarySystemBackground))
                    .cornerRadius(12)
                
                if viewModel.parsedCount > 0 {
                    Text("\(viewModel.parsedCount) GitHub events processed")
                        .font(.caption)
                        .foregroundColor(.green)
                }
            }
            
            // MARK: - Benchmark Execution Interface
            
            Button(action: { viewModel.startBenchmark() }) {
                HStack(spacing: 10) {
                    if viewModel.isTesting {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                    } else {
                        Image(systemName: "cpu")
                    }
                    Text(viewModel.isTesting ? "Parsing Data..." : "Start JSON Benchmark")
                }
                .frame(maxWidth: .infinity)
                .padding()
            }
            .buttonStyle(.borderedProminent)
            .tint(.purple)
            .disabled(viewModel.isTesting)
            
            // MARK: - Telemetry Export
            
            if let url = viewModel.exportURL {
                ShareLink(item: url) {
                    Label("Export Results (CSV)", systemImage: "tray.and.arrow.down")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.blue)
                        .foregroundColor(.white)
                        .cornerRadius(12)
                }
            }
            
            Spacer()
        }
        .padding()
        .navigationTitle("JSON Performance")
        
        // Asynchronously preloads the JSON payload into physical memory before the benchmark starts 
        // to isolate CPU deserialization metrics from disk read latency.
        .task {
            await viewModel.preloadData()
        }
    }
    
    // MARK: - Sub-Components
    
    private var headerSection: some View {
        VStack(spacing: 10) {
            Image(systemName: "curlybraces")
                .font(.system(size: 50))
                .foregroundColor(.purple)
            Text("10MB+ Data Deserialization").font(.headline)
        }
        .padding(.top)
    }
}