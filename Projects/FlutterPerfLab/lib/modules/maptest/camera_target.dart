/// Represents a framework-agnostic geospatial coordinate.
/// This ensures the Presentation and Business Logic layers remain
/// decoupled from specific Map SDKs.
class CameraTarget {
  final double latitude;
  final double longitude;
  final double zoom;

  const CameraTarget({
    required this.latitude,
    required this.longitude,
    required this.zoom,
  });
}
