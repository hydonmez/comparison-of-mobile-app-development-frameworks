import SwiftUI
import Combine
import ImageIO // Required for low-level image downsampling.

/// A SwiftUI benchmark simulating a high-velocity e-commerce scrolling environment.
///
/// Optimized to prevent Main Thread starvation and GPU overdraw during sustained, 
/// high-frequency list virtualization. Measures the rendering and diffing 
/// throughput of LazyVGrid, isolated from network or disk I/O latencies.
struct ListTestView: View {
    @StateObject private var viewModel = ListTestViewModel()
    
    // Static allocation of grid columns prevents dynamic array reallocation and 
    // memory thrashing during rapid layout updates.
    private let columns = [
        GridItem(.flexible(), spacing: 15),
        GridItem(.flexible(), spacing: 15)
    ]
    
    var body: some View {
        VStack(spacing: 0) {
            
            // MARK: - Telemetry Status Bar
            HStack {
                Text(viewModel.status)
                    .font(.caption)
                    .bold()
                    .foregroundColor(.secondary)
                Spacer()
                if viewModel.isLoadingMore {
                    ProgressView()
                        .scaleEffect(0.7)
                }
            }
            .padding(.horizontal)
            .padding(.vertical, 8)
            .background(Color(.systemGroupedBackground))
            
            // MARK: - Main Rendering Surface (LazyVGrid)
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVGrid(columns: columns, spacing: 15) {
                        
                        ForEach(viewModel.products) { product in
                            ProductCardView(product: product)
                                // Uses Equatable to enforce O(1) diffing.
                                // Prevents SwiftUI from executing redundant view invalidations
                                // for cells that remain structurally unchanged during scrolling.
                                .equatable()
                                .id(product.id)
                        }
                        
                        // Bottom Loading Indicator: Simulates UI pagination latency visually.
                        if viewModel.isRunning && viewModel.products.count < 300 {
                            ProgressView()
                                .padding()
                                .id("loader_bottom")
                        }
                    }
                    .padding()
                }
                // Routes programmatic scroll events triggered by the ViewModel.
                .onChange(of: viewModel.scrollTarget) {
                    if let target = viewModel.scrollTarget {
                        withAnimation {
                            proxy.scrollTo(target, anchor: .bottom)
                        }
                    }
                }
            }
            .background(Color(.systemGray6))
            
            // MARK: - Control Interface
            VStack(spacing: 12) {
                if !viewModel.isRunning && viewModel.exportURL == nil {
                    Button(action: {
                        viewModel.startTest()
                    }) {
                        HStack {
                            Image(systemName: "cart.fill")
                            Text("Start E-Commerce Benchmark")
                                .bold()
                        }
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.blue)
                        .foregroundColor(.white)
                        .cornerRadius(12)
                    }
                } else if viewModel.isRunning {
                    Button(action: {
                        viewModel.stopTest()
                    }) {
                        Text("Stop Benchmark")
                            .bold()
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(Color.red)
                            .foregroundColor(.white)
                            .cornerRadius(12)
                    }
                }
                
                // Telemetry Export Trigger
                // Surfaces the native ShareLink upon generation of the performance report.
                if let url = viewModel.exportURL {
                    ShareLink(item: url) {
                        Label("Export Results (CSV)", systemImage: "square.and.arrow.up")
                            .font(.headline)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(Color.green)
                            .foregroundColor(.white)
                            .cornerRadius(12)
                    }
                }
            }
            .padding()
            .background(Color(.systemBackground))
        }
        .navigationTitle("Storefront Performance")
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - Product Card Component

/// A high-performance product card optimized for rapid cell reuse.
///
/// Conforms to Equatable to bypass the SwiftUI view diffing algorithm for unchanged properties, 
/// minimizing redundant render calculations.
struct ProductCardView: View, Equatable {

    let product: Product

    static func == (lhs: ProductCardView, rhs: ProductCardView) -> Bool {
        lhs.product.id == rhs.product.id
    }

    var body: some View {
        VStack(spacing: 0) {

            // 1. Asset Area
            ZStack(alignment: .topTrailing) {
                Color.white
                
                // Replaces standard Image() with a custom ImageIO down-sampler.
                // Prevents the allocation of full-resolution uncompressed bitmaps into physical RAM.
                OptimizedLocalImage(imageName: product.data.imageName)
                    .aspectRatio(contentMode: .fit)
                    .padding(10)
                    .frame(height: 150)
                    .clipped()
                
                Text(product.data.discount)
                    .font(.caption2)
                    .fontWeight(.bold)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.red)
                    .foregroundColor(.white)
                    .cornerRadius(4)
                    .padding(8)
            }
            .frame(height: 150)
            .background(Color.gray.opacity(0.05))
            
            // 2. Metadata Area
            VStack(alignment: .leading, spacing: 6) {
                Text(product.data.name)
                    .font(.system(size: 14, weight: .semibold))
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                    .foregroundColor(.primary)
                    .frame(height: 40, alignment: .topLeading)
                
                Text(product.data.category)
                    .font(.caption)
                    .foregroundColor(.gray)
                
                Spacer(minLength: 0)
                
                HStack(alignment: .bottom) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(product.data.oldPrice)
                            .strikethrough()
                            .font(.caption2)
                            .foregroundColor(.secondary)
                        
                        Text(product.data.price)
                            .font(.subheadline)
                            .fontWeight(.bold)
                            .foregroundColor(.orange)
                    }
                    
                    Spacer()
                    
                    Image(systemName: "plus.circle.fill")
                        .font(.title2)
                        .foregroundColor(.blue)
                }
            }
            .padding(10)
        }
        // Enforcing a fixed frame height eliminates the need for SwiftUI to dynamically 
        // compute intrinsic content sizes during rapid scrolling, preventing CPU layout stalls.
        .frame(height: 280)
        
        // Elevating shadow rendering strictly to a dedicated background shape 
        // prevents the GPU from executing expensive offscreen mask calculations 
        // on the complex UI components within the card.
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color(.systemBackground))
                .shadow(color: Color.black.opacity(0.1), radius: 5, x: 0, y: 2)
        )
    }
}

// MARK: - Specialized Memory-Optimized Components

/// A specialized ImageIO wrapper for efficient memory allocation.
///
/// Forces the OS to decode only a tiny 300px thumbnail asynchronously, dropping the 
/// benchmark's net RAM footprint significantly compared to loading full-resolution assets.
struct OptimizedLocalImage: View {
    let imageName: String
    
    @State private var uiImage: UIImage?
    
    var body: some View {
        Group {
            if let uiImage = uiImage {
                Image(uiImage: uiImage)
                    .resizable()
            } else {
                // Provides a clean, zero-draw placeholder while the background thread decodes.
                Color.gray.opacity(0.1)
            }
        }
        .task(id: imageName) {
            await loadAndDownsample()
        }
    }
    
    private func loadAndDownsample() async {
        // Offloads I/O and CoreGraphics calculations to a detached background thread.
        let optimized = await Task.detached(priority: .userInitiated) { () -> UIImage? in
            
            // Handles missing file extensions and nested directory structures safely, 
            // bypassing the Apple Asset Catalog for direct I/O access.
            var fileURL: URL? = nil
            let nsString = imageName as NSString
            let name = nsString.deletingPathExtension
            let ext = nsString.pathExtension
            
            // Try resolving in the main bundle root
            if !ext.isEmpty {
                fileURL = Bundle.main.url(forResource: name, withExtension: ext)
            } else {
                // Fallback to common image extensions if JSON omits them
                fileURL = Bundle.main.url(forResource: name, withExtension: "jpg") ??
                          Bundle.main.url(forResource: name, withExtension: "png")
            }
            
            // Search specifically inside the "Resources" subdirectory if still not found
            if fileURL == nil {
                if !ext.isEmpty {
                    fileURL = Bundle.main.url(forResource: name, withExtension: ext, subdirectory: "Resources")
                } else {
                    fileURL = Bundle.main.url(forResource: name, withExtension: "jpg", subdirectory: "Resources") ??
                              Bundle.main.url(forResource: name, withExtension: "png", subdirectory: "Resources")
                }
            }
            
            guard let finalURL = fileURL else {
                print("⚠️ I/O WARNING: Could not resolve URL for asset: \(imageName)")
                return nil
            }
            
            // kCGImageSourceThumbnailMaxPixelSize forces the GPU to allocate RAM 
            // ONLY for the required viewport dimensions (300px).
            let options: [CFString: Any] = [
                kCGImageSourceCreateThumbnailFromImageIfAbsent: true,
                kCGImageSourceCreateThumbnailWithTransform: true,
                kCGImageSourceShouldCacheImmediately: true,
                kCGImageSourceThumbnailMaxPixelSize: 300
            ]
            
            guard let source = CGImageSourceCreateWithURL(finalURL as CFURL, nil),
                  let cgImage = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else {
                return nil
            }
            
            return UIImage(cgImage: cgImage)
        }.value
        
        await MainActor.run {
            self.uiImage = optimized
        }
    }
}