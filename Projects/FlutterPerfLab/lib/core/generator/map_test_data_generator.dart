import 'dart:math' as math;
import 'package:google_maps_flutter/google_maps_flutter.dart';
import '../../models/map_point.dart';

/// A stateless, thread-safe namespace dedicated to generating deterministic
/// spatial datasets for map rendering benchmarks.
///
/// Uses a synchronous execution model to avoid asynchronous context switch overhead.
abstract class MapTestDataGenerator {
  /// Generates a spatially distributed array of map annotations synchronously.
  ///
  /// Utilizes a mathematical sine wave distribution to simulate a realistic
  /// but deterministic spread. This creates a standardized baseline for testing
  /// the rendering performance and spatial indexing capabilities of mapping engines.
  static List<MapPoint> generatePoints([int count = 20]) {
    // Base coordinates anchored to Istanbul for localized spatial rendering tests.
    const double centerLat = 41.0082;
    const double centerLon = 28.9784;
    const double spread = 0.05;

    // List.generate with growable: false pre-allocates the exact contiguous
    // memory required upfront. This eliminates the CPU overhead of dynamic array
    // resizing (reallocation) in the Dart VM, guaranteeing that only pure mathematical
    // calculation throughput is benchmarked.
    return List.generate(count, (i) {
      final double progress = i / count;

      // Calculate linear latitude progression and sine-wave based longitude oscillation.
      final double latOffset = (progress - 0.5) * spread * 2.0;
      final double lonOffset = math.sin(progress * math.pi * 4.0) * spread;

      return MapPoint(
        // Deterministic integer ID is utilized to eliminate the heavy CPU and GC
        // overhead associated with cryptographic UUID() generation.
        id: i,
        coordinate: LatLng(centerLat + latOffset, centerLon + lonOffset),
        title: "Pin ${i + 1}",
      );
    }, growable: false); // Enforces fixed-length list allocation
  }
}
