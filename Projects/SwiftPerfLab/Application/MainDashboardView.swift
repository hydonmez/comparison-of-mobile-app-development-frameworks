import SwiftUI

// MARK: - Navigation Routing Model

/// Defines routing paths for the benchmarking suite. 
/// Uses a Hashable enum for type-safe and lazy initialization to minimize memory footprint.
enum BenchmarkRoute: Hashable {
    case video, audio, storage, json, map, list, sensor
}

// MARK: - Core Navigation Hub

/// Primary entry point for the benchmarking suite UI.
struct MainDashboardView: View {
    
    var body: some View {
        NavigationStack {
            List {
                
                // MARK: - Application Launch Telemetry Dashboard
                
                // Telemetry data processing is isolated to prevent frame drops 
                // in the parent List during high-frequency state updates.
                LaunchTelemetryDashboardView()
                
                // MARK: - Performance Benchmarks
                Section(header: Text("Performance Benchmarks")) {
                    
                    // Uses value-based NavigationLinks to prevent eager initialization 
                    // of benchmark views during Cold Start, ensuring accurate initial CPU and RAM metrics.
                    NavigationLink(value: BenchmarkRoute.video) {
                        TestRowView(title: "Video Playback Test (1080p)", icon: "play.tv.fill", color: .red)
                    }
                    NavigationLink(value: BenchmarkRoute.audio) {
                        TestRowView(title: "Audio Playback Test (5 Min)", icon: "waveform", color: .pink)
                    }
                    NavigationLink(value: BenchmarkRoute.storage) {
                        TestRowView(title: "Storage Analysis", icon: "internaldrive.fill", color: .gray)
                    }
                    NavigationLink(value: BenchmarkRoute.json) {
                        TestRowView(title: "JSON Parsing Test", icon: "doc.text.fill", color: .teal)
                    }
                    NavigationLink(value: BenchmarkRoute.map) {
                        TestRowView(title: "Map Rendering Test", icon: "map.fill", color: .green)
                    }
                    NavigationLink(value: BenchmarkRoute.list) {
                        TestRowView(title: "UI Showcase Test (LazyVGrid)", icon: "square.grid.2x2.fill", color: .blue)
                    }
                    NavigationLink(value: BenchmarkRoute.sensor) {
                        TestRowView(title: "Sensor Fusion Test (100Hz)", icon: "sensor.tag.radiowaves.forward", color: .orange)
                    }
                }
            }
            .environment(\.defaultMinListRowHeight, 40)
            .navigationTitle("Benchmark Lab")
            .navigationBarTitleDisplayMode(.inline)
            
            // MARK: - Deferred View Resolution
            // Lazily instantiates benchmark views upon user interaction.
            .navigationDestination(for: BenchmarkRoute.self) { route in
                switch route {
                case .video: VideoTestView()
                case .audio: AudioTestView()
                case .storage: StorageTestView()
                case .json: JsonTestView()
                case .map: MapTestView()
                case .list: ListTestView()
                case .sensor: SensorTestView()
                }
            }
        }
    }
}

// MARK: - Isolated Telemetry Sub-View

/// An isolated sub-view for observing lifecycle telemetry.
struct LaunchTelemetryDashboardView: View {
    
    // Binds to the singleton instance, restricting re-renders to this view's 
    // local scope to prevent CPU bottlenecks.
    @ObservedObject var tracker = LaunchPerformanceManager.shared
    
    var body: some View {
        Section {
            VStack(alignment: .leading, spacing: 12) {
                
                // Header: System Response Latency
                HStack {
                    Image(systemName: "gauge.with.dots.needle.bottom.50percent")
                        .foregroundColor(.indigo)
                        .font(.title3)
                    Text("System Response Latency")
                        .font(.headline)
                        .fontWeight(.bold)
                }
                
                HStack(spacing: 12) {
                    
                    // MARK: - Cold Start Telemetry Component
                    VStack(alignment: .leading, spacing: 8) {
                        VStack(alignment: .leading, spacing: 2) {
                            HStack(spacing: 4) {
                                Image(systemName: "power.circle.fill")
                                    .foregroundColor(.cyan)
                                Text("Cold Start")
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(.secondary)
                            }
                            Text("Initial Initialization")
                                .font(.system(size: 10))
                                .foregroundColor(.gray)
                        }
                        
                        Text("\(tracker.formattedColdStart) ms")
                            .font(.system(size: 22, weight: .heavy, design: .rounded))
                            .monospacedDigit()
                            .foregroundColor(.primary)
                            .minimumScaleFactor(0.8)
                        
                        VStack(alignment: .leading, spacing: 4) {
                            HStack(spacing: 4) {
                                Circle().fill(Color.indigo).frame(width: 6, height: 6)
                                Text("OS: \(tracker.formattedOSDuration) ms")
                                    .font(.system(size: 11, weight: .medium))
                                    .foregroundColor(.secondary)
                            }
                            HStack(spacing: 4) {
                                Circle().fill(Color.pink).frame(width: 6, height: 6)
                                Text("UI: \(tracker.formattedUIDuration) ms")
                                    .font(.system(size: 11, weight: .medium))
                                    .foregroundColor(.secondary)
                            }
                        }
                    }
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.cyan.opacity(0.1))
                    .cornerRadius(14)
                    
                    // MARK: - Hot Start Telemetry Component
                    VStack(alignment: .leading, spacing: 8) {
                        VStack(alignment: .leading, spacing: 2) {
                            HStack(spacing: 4) {
                                Image(systemName: "arrow.uturn.backward.circle.fill")
                                    .foregroundColor(.orange)
                                Text("Hot Start")
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(.secondary)
                            }
                            Text("Resume from Background")
                                .font(.system(size: 10))
                                .foregroundColor(.gray)
                        }
                        
                        Text("\(tracker.formattedHotStart) ms")
                            .font(.system(size: 22, weight: .heavy, design: .rounded))
                            .monospacedDigit()
                            .foregroundColor(tracker.hotStartMs > 0 ? .primary : .gray.opacity(0.4))
                            .minimumScaleFactor(0.8)
                        
                        VStack(alignment: .leading, spacing: 4) {
                            if tracker.hotStartMs > 0 {
                                HStack(spacing: 4) {
                                    Image(systemName: "memorychip.fill")
                                        .font(.system(size: 11))
                                        .foregroundColor(.green)
                                    Text("Loaded from RAM")
                                        .font(.system(size: 11, weight: .bold))
                                        .foregroundColor(.green)
                                }
                            } else {
                                HStack(spacing: 4) {
                                    Image(systemName: "hourglass")
                                        .font(.system(size: 11))
                                        .foregroundColor(.gray)
                                    Text("Awaiting Data...")
                                        .font(.system(size: 11, weight: .medium))
                                        .foregroundColor(.gray)
                                }
                            }
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.orange.opacity(0.1))
                    .cornerRadius(14)
                }
            }
            .padding(.vertical, 4)
            .listRowInsets(EdgeInsets(top: 12, leading: 16, bottom: 12, trailing: 16))
            .listRowBackground(Color.clear)
        }
    }
}

// MARK: - Reusable UI Components

/// Reusable UI component for benchmark navigation rows.
struct TestRowView: View {
    let title: String
    let icon: String
    let color: Color
    
    var body: some View {
        HStack(spacing: 14) {
            ZStack {
                color
                    .frame(width: 30, height: 30)
                    .cornerRadius(7)
                
                Image(systemName: icon)
                    .foregroundColor(.white)
                    .font(.system(size: 15, weight: .semibold))
            }
            
            Text(title)
                .font(.system(size: 14, weight: .bold))
                // Adapts to system Dark/Light mode.
                .foregroundColor(.primary)
        }
    }
}

#Preview {
    MainDashboardView()
}