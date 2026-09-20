import 'package:google_maps_flutter/google_maps_flutter.dart';

/// A lightweight, immutable Data Transfer Object (DTO) representing a distinct geographical annotation.
///
/// Enforcing strict immutability guarantees thread-safe compliance when transferring
/// spatial datasets across isolates.
class MapPoint {
  /// A deterministic integer sequence uniquely identifying the spatial coordinate.
  ///
  /// Bypassing UUID generation eliminates cryptographic CPU overhead. This integer
  /// acts as a unique identifier for UI elements, enabling efficient deterministic diffing
  /// and preventing full-screen repaints during rapid updates.
  final int id;

  /// The precise spatial coordinates (latitude and longitude) for map rendering.
  final LatLng coordinate;

  /// A descriptive label associated with the geographic coordinate.
  final String title;

  const MapPoint({
    required this.id,
    required this.coordinate,
    required this.title,
  });
}
