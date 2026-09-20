import 'dart:io' show Platform;
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:go_router/go_router.dart';

import 'package:google_maps_flutter/google_maps_flutter.dart' as google;
import 'package:apple_maps_flutter/apple_maps_flutter.dart' as apple;

import 'map_test_view_model.dart';
import 'map_controller_adapter.dart';
import 'package:flutterperflab/models/map_point.dart';

/// A platform-aware presentation layer for map testing.
class MapTestScreen extends StatelessWidget {
  const MapTestScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (_) => MapTestViewModel(),
      child: const _MapTestContent(),
    );
  }
}

class _MapTestContent extends StatelessWidget {
  const _MapTestContent();

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (bool didPop, dynamic result) {
        if (didPop) return;
        final vm = context.read<MapTestViewModel>();
        if (vm.isRunning) vm.stopTest(isFinished: false);
        context.go('/');
      },
      child: Scaffold(
        backgroundColor: const Color(0xFFF2F2F7),
        appBar: AppBar(
          title: const Text(
            "Map Performance",
            style: TextStyle(fontWeight: FontWeight.bold),
          ),
          backgroundColor: Colors.white,
          leading: IconButton(
            icon: const Icon(Icons.arrow_back),
            onPressed: () {
              final vm = context.read<MapTestViewModel>();
              if (vm.isRunning) vm.stopTest(isFinished: false);
              context.go('/');
            },
          ),
        ),
        body: Column(
          children: [
            // ─── TELEMETRY STATUS BAR ───
            Selector<MapTestViewModel, _StatusState>(
              selector: (_, vm) => _StatusState(vm.status, vm.isRunning),
              builder: (context, state, _) => Container(
                color: Colors.grey.shade200,
                padding: const EdgeInsets.symmetric(
                  horizontal: 16.0,
                  vertical: 12.0,
                ),
                child: Row(
                  children: [
                    Expanded(
                      child: Text(
                        state.status,
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.bold,
                          color: Colors.grey.shade700,
                        ),
                      ),
                    ),
                    if (state.isRunning)
                      const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      ),
                  ],
                ),
              ),
            ),

            // ─── POLYMORPHIC MAP RENDERING SURFACE ───
            Expanded(
              child: Selector<MapTestViewModel, List<MapPoint>>(
                selector: (_, vm) => vm.points,
                builder: (context, points, _) {
                  final vm = context.read<MapTestViewModel>();
                  final isIOS = Platform.isIOS;

                  // Dynamically fetching the OS-specific initial target.
                  final initialTarget = vm.getInitialTarget(isIOS: isIOS);

                  // ─── OS-LEVEL ENGINE ROUTING ───
                  if (isIOS) {
                    // Native iOS Delegation
                    return apple.AppleMap(
                      onMapCreated: (apple.AppleMapController controller) {
                        final adapter = AppleMapAdapter(controller);
                        vm.setMapController(adapter);

                        // Forces bounds instantly, overriding the plugin's default zoom behavior.
                        adapter.moveCamera(initialTarget);
                      },
                      initialCameraPosition: apple.CameraPosition(
                        target: apple.LatLng(
                          initialTarget.latitude,
                          initialTarget.longitude,
                        ),
                        zoom: initialTarget.zoom,
                      ),
                      pitchGesturesEnabled: false,
                      scrollGesturesEnabled: false,
                      zoomGesturesEnabled: false,
                      rotateGesturesEnabled: false,
                      compassEnabled: false,
                      myLocationButtonEnabled: false,
                      annotations: points.map((point) {
                        return apple.Annotation(
                          annotationId: apple.AnnotationId(point.id.toString()),
                          position: apple.LatLng(
                            point.coordinate.latitude,
                            point.coordinate.longitude,
                          ),
                        );
                      }).toSet(),
                    );
                  } else {
                    // Native Android Delegation
                    // Preserves Google Maps initialization.
                    return google.GoogleMap(
                      onMapCreated: (google.GoogleMapController controller) {
                        vm.setMapController(GoogleMapAdapter(controller));
                      },
                      initialCameraPosition: google.CameraPosition(
                        target: google.LatLng(
                          initialTarget.latitude,
                          initialTarget.longitude,
                        ),
                        zoom: initialTarget.zoom,
                      ),
                      mapType: google.MapType.normal,
                      scrollGesturesEnabled: false,
                      zoomGesturesEnabled: false,
                      tiltGesturesEnabled: false,
                      rotateGesturesEnabled: false,
                      compassEnabled: false,
                      myLocationButtonEnabled: false,
                      mapToolbarEnabled: false,
                      zoomControlsEnabled: false,
                      markers: points.map((point) {
                        return google.Marker(
                          markerId: google.MarkerId(point.id.toString()),
                          position: google.LatLng(
                            point.coordinate.latitude,
                            point.coordinate.longitude,
                          ),
                        );
                      }).toSet(),
                    );
                  }
                },
              ),
            ),

            // ─── CONTROL INTERFACE ───
            Selector<MapTestViewModel, _ControlState>(
              selector: (_, vm) =>
                  _ControlState(vm.isRunning, vm.isReportReady),
              builder: (context, state, _) {
                final vm = context.read<MapTestViewModel>();
                return Container(
                  color: Colors.white,
                  padding: const EdgeInsets.all(16.0),
                  child: Column(
                    children: [
                      SizedBox(
                        width: double.infinity,
                        height: 56,
                        child: ElevatedButton.icon(
                          style: ElevatedButton.styleFrom(
                            backgroundColor: Colors.purple,
                            disabledBackgroundColor: Colors.purple.withOpacity(
                              0.5,
                            ),
                            foregroundColor: Colors.white,
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(12),
                            ),
                          ),
                          // Passing the OS context to the orchestrator.
                          onPressed: state.isRunning
                              ? null
                              : () => vm.startTest(isIOS: Platform.isIOS),
                          icon: Icon(
                            state.isRunning
                                ? Icons.airplanemode_active
                                : Icons.play_arrow,
                          ),
                          label: Text(
                            state.isRunning
                                ? "Benchmark in Progress..."
                                : "Start Automated Tour",
                            style: const TextStyle(fontWeight: FontWeight.bold),
                          ),
                        ),
                      ),
                      if (state.isReportReady && !state.isRunning)
                        Padding(
                          padding: const EdgeInsets.only(top: 15.0),
                          child: SizedBox(
                            width: double.infinity,
                            height: 56,
                            child: ElevatedButton.icon(
                              style: ElevatedButton.styleFrom(
                                backgroundColor: Colors.green,
                                foregroundColor: Colors.white,
                                shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(12),
                                ),
                              ),
                              onPressed: () => vm.exportResults(),
                              icon: const Icon(Icons.system_update_alt),
                              label: const Text(
                                "Export Telemetry (CSV)",
                                style: TextStyle(fontWeight: FontWeight.bold),
                              ),
                            ),
                          ),
                        ),
                    ],
                  ),
                );
              },
            ),
          ],
        ),
      ),
    );
  }
}

class _StatusState {
  final String status;
  final bool isRunning;
  _StatusState(this.status, this.isRunning);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is _StatusState &&
          runtimeType == other.runtimeType &&
          status == other.status &&
          isRunning == other.isRunning;
  @override
  int get hashCode => status.hashCode ^ isRunning.hashCode;
}

class _ControlState {
  final bool isRunning;
  final bool isReportReady;
  _ControlState(this.isRunning, this.isReportReady);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is _ControlState &&
          runtimeType == other.runtimeType &&
          isRunning == other.isRunning &&
          isReportReady == other.isReportReady;
  @override
  int get hashCode => isRunning.hashCode ^ isReportReady.hashCode;
}
