import SwiftUI
import MapKit

// MARK: - Low-Level Rendering Bridge

/// A hardware-accelerated rendering surface that bypasses declarative framework overhead.
///
/// Strictly manages the MKMapView lifecycle. Detaching camera interpolation from the SwiftUI 
/// view tree ensures telemetry reflects pure GPU rendering throughput without transient allocations.
struct NativeMapRepresentable: UIViewRepresentable {
    
    let cameraTarget: CameraTarget
    let points: [MapPoint]
    
    /// Caches render state to prevent redundant updates during high-frequency orchestrator changes.
    class Coordinator: NSObject {
        var pointsHash: Int = 0
        var lastLat: Double = 0.0
        var lastLon: Double = 0.0
        var lastZoom: Float = 0.0
    }
    
    func makeCoordinator() -> Coordinator {
        return Coordinator()
    }
    
    func makeUIView(context: Context) -> MKMapView {
        let mapView = MKMapView()
        
        // Disabling gesture recognizers reallocates RAM and CPU cycles 
        // from touch-event prediction directly to spatial rendering throughput.
        mapView.mapType = .standard
        mapView.showsCompass = false
        mapView.isZoomEnabled = false
        mapView.isScrollEnabled = false
        mapView.isPitchEnabled = false
        mapView.isRotateEnabled = false
        
        // Configures rendering vectors for a strict baseline complexity.
        mapView.showsPointsOfInterest = true
        mapView.showsBuildings = false
        
        return mapView
    }
    
    func updateUIView(_ mapView: MKMapView, context: Context) {
        let coordinator = context.coordinator
        
        // Generates a deterministic hash from unique identifiers to trigger rendering updates 
        // exclusively upon dataset mutations, preventing GPU overdraw.
        let currentHash = points.map { $0.id }.hashValue
        
        if currentHash != coordinator.pointsHash {
            let existing = mapView.annotations.compactMap { $0 as? MKPointAnnotation }
            mapView.removeAnnotations(existing)
            
            let newAnnotations = points.map { point -> MKPointAnnotation in
                let annotation = MKPointAnnotation()
                annotation.coordinate = point.coordinate
                return annotation
            }
            
            mapView.addAnnotations(newAnnotations)
            coordinator.pointsHash = currentHash
        }
        
        // Translates target states into spatial regions, delegating interpolation 
        // entirely to the native MapKit engine.
        if coordinator.lastLat != cameraTarget.latitude ||
           coordinator.lastLon != cameraTarget.longitude ||
           coordinator.lastZoom != cameraTarget.zoom {
            
            let spanDelta = 360.0 / pow(2.0, Double(cameraTarget.zoom))
            let center = CLLocationCoordinate2D(
                latitude: cameraTarget.latitude,
                longitude: cameraTarget.longitude
            )
            let region = MKCoordinateRegion(
                center: center,
                span: MKCoordinateSpan(latitudeDelta: spanDelta, longitudeDelta: spanDelta)
            )
            
            mapView.setRegion(region, animated: true)
            
            coordinator.lastLat = cameraTarget.latitude
            coordinator.lastLon = cameraTarget.longitude
            coordinator.lastZoom = cameraTarget.zoom
        }
    }
}

// MARK: - Presentation Layer

/// A presentation layer for visualizing and executing automated geospatial benchmarks.
struct MapTestView: View {
    
    /// Binds the UI to the benchmarking orchestrator for deterministic data injection.
    @StateObject private var viewModel = MapTestViewModel()
    
    var body: some View {
        VStack(spacing: 0) {
            
            // MARK: - Telemetry Status Header
            
            HStack {
                Text(viewModel.status)
                    .font(.caption)
                    .bold()
                Spacer()
                if viewModel.isRunning {
                    ProgressView().scaleEffect(0.8)
                }
            }
            .padding()
            .background(Color(.systemGray6))
            
            // MARK: - Map Rendering Surface
            
            NativeMapRepresentable(
                cameraTarget: viewModel.cameraTarget,
                points: viewModel.points
            )
            .edgesIgnoringSafeArea(.bottom)
            
            // MARK: - Execution Interface
            
            VStack(spacing: 15) {
                Button(action: {
                    viewModel.startTest()
                }) {
                    Label(viewModel.isRunning ? "Benchmark in Progress..." : "Start Automated Tour",
                          systemImage: viewModel.isRunning ? "airplane" : "play.fill")
                        .frame(maxWidth: .infinity)
                        .padding()
                }
                .buttonStyle(.borderedProminent)
                .tint(.purple)
                .disabled(viewModel.isRunning)
                
                if let url = viewModel.exportURL {
                    ShareLink(item: url) {
                        Label("Export Telemetry (CSV)", systemImage: "square.and.arrow.up")
                            .font(.headline)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(Color.green)
                            .foregroundColor(.white)
                            .cornerRadius(10)
                    }
                }
            }
            .padding()
            .background(Color(.systemBackground))
        }
        .navigationTitle("Map Performance Benchmark")
        .navigationBarTitleDisplayMode(.inline)
        .onDisappear {
            // Ensures telemetry threads and hardware monitoring are forcefully 
            // terminated upon premature view deallocation to prevent memory leaks.
            viewModel.stopTest(isFinished: false)
        }
    }
}