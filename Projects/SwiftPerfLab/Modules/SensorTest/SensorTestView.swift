import SwiftUI
import Combine
import CoreMotion

/// A declarative view for visualizing high-frequency (100Hz) sensor telemetry.
///
/// Uses state isolation to mitigate SwiftUI's ObservableObject broadcast overhead. 
/// By subscribing directly to specific property publishers via PassthroughSubject, 
/// it ensures that high-frequency updates (e.g., Accelerometer) do not invalidate 
/// other sensor views, significantly reducing CPU cycles.
struct SensorTestView: View {
    
    @StateObject private var viewModel = SensorTestViewModel()
    
    var body: some View {
        ScrollView {
            VStack(spacing: 25) {
                
                // MARK: - Telemetry Status Hub
                VStack(spacing: 15) {
                    Text(viewModel.status)
                        .font(.headline)
                        .foregroundColor(viewModel.isRunning ? .orange : .gray)
                        .padding(.top)
                    
                    // Isolated recomposition scope prevents high-frequency state changes 
                    // from propagating up the view tree.
                    BenchmarkProgressCard(viewModel: viewModel)
                }
                
                // MARK: - Sensor Visualization
                VStack(spacing: 15) {
                    // Injecting the ViewModel to access the transient data streams
                    AccelerometerCard(viewModel: viewModel)
                    GyroscopeCard(viewModel: viewModel)
                    MagnetometerCard(viewModel: viewModel)
                    PedometerCard(viewModel: viewModel)
                }
                .padding(.horizontal)
                
                Spacer()
                
                // MARK: - Controls
                ControlPanel(viewModel: viewModel)
            }
        }
        .navigationTitle("Sensor Performance")
        .onDisappear {
            // Stops hardware polling when the view is dismissed to ensure memory safety.
            viewModel.stopTest()
        }
    }
}

// MARK: - Subcomponents

struct BenchmarkProgressCard: View {
    @ObservedObject var viewModel: SensorTestViewModel
    
    var body: some View {
        if viewModel.isRunning {
            VStack(spacing: 8) {
                // UI interpolation is handled exclusively by CoreAnimation, 
                // bypassing the main SwiftUI render loop.
                ProgressView(value: viewModel.progress, total: 1.0)
                    .progressViewStyle(LinearProgressViewStyle(tint: .orange))
                    .scaleEffect(x: 1, y: 2, anchor: .center)
                    .animation(.linear(duration: 0.1), value: viewModel.progress)
                
                HStack {
                    Text("Benchmark Progress:")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Spacer()
                    Text("\(Int(viewModel.progress * 100))%")
                        .font(.caption)
                        .bold()
                        .foregroundColor(.orange)
                }
            }
            .padding(.horizontal)
            .transition(.opacity)
        }
    }
}

// MARK: - Atomic Sensor Components

/// Uses transient PassthroughSubject streams instead of global observation.
/// This guarantees the view only re-renders when its specific data mutates, 
/// avoiding unnecessary updates from other active sensors.
struct AccelerometerCard: View {
    @ObservedObject var viewModel: SensorTestViewModel
    
    @State private var x: Double = 0
    @State private var y: Double = 0
    @State private var z: Double = 0
    
    var body: some View {
        SensorInfoBox(
            icon: "move.3d",
            title: "Accelerometer (G-Force)",
            x: x, y: y, z: z,
            color: .blue
        )
        .onReceive(viewModel.accelStream) { data in
            self.x = data.x
            self.y = data.y
            self.z = data.z
        }
    }
}

struct GyroscopeCard: View {
    @ObservedObject var viewModel: SensorTestViewModel
    
    @State private var x: Double = 0
    @State private var y: Double = 0
    @State private var z: Double = 0
    
    var body: some View {
        SensorInfoBox(
            icon: "gyroscope",
            title: "Gyroscope (Rad/s)",
            x: x, y: y, z: z,
            color: .green
        )
        .onReceive(viewModel.gyroStream) { data in
            self.x = data.x
            self.y = data.y
            self.z = data.z
        }
    }
}

struct MagnetometerCard: View {
    @ObservedObject var viewModel: SensorTestViewModel
    
    @State private var x: Double = 0
    @State private var y: Double = 0
    @State private var z: Double = 0
    
    var body: some View {
        SensorInfoBox(
            icon: "location.north.line.fill",
            title: "Magnetometer (µT)",
            x: x, y: y, z: z,
            color: .red
        )
        .onReceive(viewModel.magnetStream) { data in
            self.x = data.x
            self.y = data.y
            self.z = data.z
        }
    }
}

struct PedometerCard: View {
    @ObservedObject var viewModel: SensorTestViewModel
    
    @State private var stepCount: Int = 0
    
    var body: some View {
        HStack {
            VStack(alignment: .leading) {
                Text("Pedometer (Steps)")
                    .font(.caption)
                    .bold()
                    .foregroundColor(.purple)
                
                Text("\(stepCount)")
                    .font(.system(.title2, design: .monospaced))
                    .bold()
            }
            Spacer()
            Image(systemName: "figure.walk")
                .font(.title)
                .foregroundColor(.purple)
        }
        .padding()
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
        .onReceive(viewModel.stepStream) { count in
            self.stepCount = count
        }
    }
}

// MARK: - Low-Level UI Primitives

private struct SensorInfoBox: View {
    let icon: String
    let title: String
    let x, y, z: Double
    let color: Color
    
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Image(systemName: icon).foregroundColor(color)
                Text(title).font(.subheadline).bold()
            }
            HStack {
                ValueText(label: "X", value: x)
                ValueText(label: "Y", value: y)
                ValueText(label: "Z", value: z)
            }
        }
        .padding()
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
    }
}

private struct ValueText: View {
    let label: String
    let value: Double
    
    /// Uses a shared, nonisolated static formatter to eliminate object 
    /// allocation overhead during high-frequency string interpolation.
    private nonisolated static let formatter: NumberFormatter = {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.minimumFractionDigits = 3
        f.maximumFractionDigits = 3
        f.locale = Locale(identifier: "en_US")
        return f
    }()
    
    var body: some View {
        VStack {
            Text(label)
                .font(.caption2)
                .foregroundColor(.secondary)
            
            // Uses a monospaced font to guarantee constant glyph widths, 
            // preventing horizontal layout jitter during rapid numerical updates.
            Text(Self.formatter.string(from: NSNumber(value: value)) ?? "0.000")
                .font(.system(.caption, design: .monospaced))
                .bold()
        }
        .frame(maxWidth: .infinity)
    }
}

private struct ControlPanel: View {
    @ObservedObject var viewModel: SensorTestViewModel
    
    var body: some View {
        VStack(spacing: 15) {
            Button(action: {
                viewModel.isRunning ? viewModel.stopTest(isFinished: false) : viewModel.startTest()
            }) {
                HStack {
                    Image(systemName: viewModel.isRunning ? "stop.fill" : "play.fill")
                    Text(viewModel.isRunning ? "Stop Benchmark" : "Start Sensor Test")
                }
                .font(.headline)
                .frame(maxWidth: .infinity)
                .padding()
                .background(viewModel.isRunning ? Color.red : Color.orange)
                .foregroundColor(.white)
                .cornerRadius(12)
            }
            
            if let url = viewModel.exportURL, !viewModel.isRunning {
                ShareLink(item: url) {
                    Label("Export Telemetry (CSV)", systemImage: "square.and.arrow.up")
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.green)
                        .foregroundColor(.white)
                        .cornerRadius(12)
                }
            }
        }
        .padding()
        .padding(.bottom, 20)
    }
}