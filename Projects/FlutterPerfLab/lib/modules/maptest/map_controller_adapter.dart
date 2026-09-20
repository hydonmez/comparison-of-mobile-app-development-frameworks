import 'dart:math' as math;
import 'package:google_maps_flutter/google_maps_flutter.dart' as google;
import 'package:apple_maps_flutter/apple_maps_flutter.dart' as apple;
import 'camera_target.dart';

/// A polymorphic interface for Map Engines.
abstract class IMapController {
  Future<void> animateCamera(CameraTarget target);
  Future<void> moveCamera(
    CameraTarget target,
  ); // Used for instant initialization
}

// ─── GOOGLE MAPS ADAPTER (ANDROID) ────────────────────────────────────
// Preserves the Google Maps rendering behavior.
class GoogleMapAdapter implements IMapController {
  final google.GoogleMapController controller;
  GoogleMapAdapter(this.controller);

  @override
  Future<void> animateCamera(CameraTarget target) async {
    final googleUpdate = google.CameraUpdate.newCameraPosition(
      google.CameraPosition(
        target: google.LatLng(target.latitude, target.longitude),
        zoom: target.zoom,
      ),
    );
    await controller.animateCamera(googleUpdate);
  }

  @override
  Future<void> moveCamera(CameraTarget target) async {
    final googleUpdate = google.CameraUpdate.newCameraPosition(
      google.CameraPosition(
        target: google.LatLng(target.latitude, target.longitude),
        zoom: target.zoom,
      ),
    );
    await controller.moveCamera(googleUpdate);
  }
}

// ─── APPLE MAPS ADAPTER (iOS) ─────────────────────────────────────────
// Utilizes MKCoordinateRegion math.
class AppleMapAdapter implements IMapController {
  final apple.AppleMapController controller;
  AppleMapAdapter(this.controller);

  /// Replicates the spanDelta math to ensure consistent behavior across platforms.
  apple.CameraUpdate _createNativeSwiftBounds(CameraTarget target) {
    final double spanDelta = 360.0 / math.pow(2.0, target.zoom);

    final bounds = apple.LatLngBounds(
      southwest: apple.LatLng(
        target.latitude - (spanDelta / 2),
        target.longitude - (spanDelta / 2),
      ),
      northeast: apple.LatLng(
        target.latitude + (spanDelta / 2),
        target.longitude + (spanDelta / 2),
      ),
    );

    // 0.0 padding ensures strict parity with MKCoordinateRegion's boundaries
    return apple.CameraUpdate.newLatLngBounds(bounds, 0.0);
  }

  @override
  Future<void> animateCamera(CameraTarget target) async {
    await controller.animateCamera(_createNativeSwiftBounds(target));
  }

  @override
  Future<void> moveCamera(CameraTarget target) async {
    await controller.moveCamera(_createNativeSwiftBounds(target));
  }
}
