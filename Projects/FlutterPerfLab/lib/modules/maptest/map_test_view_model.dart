import 'dart:async';
import 'dart:isolate';
import 'package:flutter/foundation.dart';
import 'package:share_plus/share_plus.dart';

import 'package:flutterperflab/core/managers/performance_manager.dart';
import 'package:flutterperflab/core/managers/export_manager.dart';
import 'package:flutterperflab/models/map_point.dart';
import 'package:flutterperflab/core/generator/map_test_data_generator.dart';

// Framework-agnostic dependencies
import 'map_controller_adapter.dart';
import 'camera_target.dart';

/// Manages the benchmark state and execution strictly in Dart, relying on
/// the platform channel initialization payload.
class MapTestViewModel extends ChangeNotifier {
  // MARK: - Benchmark Constants
  static const double _zoomOutLevel = 13.0;
  static const double _zoomInLevel = 17.5;

  // Google Maps and Apple Maps calculate visible coordinate spans differently.
  // We define platform-specific baselines here to ensure equal rendering payloads.
  static const CameraTarget _androidInitialTarget = CameraTarget(
    latitude: 41.0082,
    longitude: 28.9784,
    zoom: 11.12, // Google Maps Scale
  );

  static const CameraTarget _iosInitialTarget = CameraTarget(
    latitude: 41.0082,
    longitude: 28.9784,
    zoom: 11.74, // Apple Maps Scale
  );

  // MARK: - Reactive State
  bool isRunning = false;
  String status = "Ready";
  bool isReportReady = false;
  List<MapPoint> points = [];

  // MARK: - Internal Dependencies
  IMapController? _mapController;
  final PerformanceManager _performance = PerformanceManager.shared;
  bool _isTaskCancelled = false;

  /// Returns the hardware-aligned starting coordinate based on the requested OS.
  CameraTarget getInitialTarget({required bool isIOS}) {
    return isIOS ? _iosInitialTarget : _androidInitialTarget;
  }

  void setMapController(IMapController controller) {
    _mapController = controller;
  }

  // MARK: - Execution Pipeline

  Future<void> startTest({required bool isIOS}) async {
    if (_mapController == null) {
      _updateState(status: "Error: Rendering Engine not initialized.");
      return;
    }

    stopTest(isFinished: false);
    _isTaskCancelled = false;
    _updateState(
      isRunning: true,
      status: "Initializing Geospatial Dataset...",
      isReportReady: false,
    );

    // Offloads dataset generation to an isolate to prevent main thread stalls
    // prior to telemetry initialization.
    final List<MapPoint> newPoints = await Isolate.run(
      () => MapTestDataGenerator.generatePoints(20),
    );

    points = newPoints;
    _updateState(status: "Benchmarking in Progress...");

    _performance.startMonitoring();
    _startPinTour(isIOS: isIOS);
  }

  /// Systematic multi-stage animation sequence.
  /// Translates spatial coordinates into engine-agnostic targets, delegating
  /// the interpolation entirely to the respective underlying platform views.
  Future<void> _startPinTour({required bool isIOS}) async {
    await Future.delayed(const Duration(milliseconds: 1000));

    for (int i = 0; i < points.length; i++) {
      if (_isTaskCancelled || !isRunning) break;
      final MapPoint point = points[i];

      // STAGE 1: Transition (Horizontal Panning)
      _updateState(status: "Target ${i + 1} / ${points.length} -> Panning ✈️");
      await _mapController?.animateCamera(
        CameraTarget(
          latitude: point.coordinate.latitude,
          longitude: point.coordinate.longitude,
          zoom: _zoomOutLevel,
        ),
      );

      await Future.delayed(const Duration(milliseconds: 1800));
      if (_isTaskCancelled || !isRunning) break;

      // STAGE 2: Inspection (Vertical Zooming)
      _updateState(status: "Target ${i + 1} -> Inspecting Detail 🔍");
      await _mapController?.animateCamera(
        CameraTarget(
          latitude: point.coordinate.latitude,
          longitude: point.coordinate.longitude,
          zoom: _zoomInLevel,
        ),
      );

      await Future.delayed(const Duration(milliseconds: 1800));
      if (_isTaskCancelled || !isRunning) break;

      // STAGE 3: Ascension (Zoom Out)
      if (i < points.length - 1) {
        _updateState(status: "Target ${i + 1} -> Ascending ⬆️");
        await _mapController?.animateCamera(
          CameraTarget(
            latitude: point.coordinate.latitude,
            longitude: point.coordinate.longitude,
            zoom: _zoomOutLevel,
          ),
        );
        await Future.delayed(const Duration(milliseconds: 1200));
      }
    }

    // FINAL STAGE: Global Context Review
    if (!_isTaskCancelled && isRunning) {
      _updateState(status: "Tour Finalized!");
      // Returns to the OS-specific starting position.
      await _mapController?.animateCamera(getInitialTarget(isIOS: isIOS));
      await Future.delayed(const Duration(milliseconds: 3000));
    }

    if (!_isTaskCancelled) stopTest(isFinished: true);
  }

  // MARK: - Termination & Telemetry Export

  void stopTest({required bool isFinished}) {
    _isTaskCancelled = true;
    if (!isRunning) return;

    _performance.stopMonitoring();
    _updateState(
      isRunning: false,
      status: isFinished ? "Completed ✅" : "Terminated 🛑",
      isReportReady: isFinished,
    );
  }

  Future<void> exportResults() async {
    final file = await ExportManager.shared.generateCSV(
      logs: _performance.currentLogs,
      testName: "Map_Optimized_Tour_Hybrid",
      customSummary: "Total_Points,${points.length}",
    );
    if (file != null) await Share.shareXFiles([XFile(file.path)]);
  }

  // MARK: - State Segregation

  /// Centralized state mutator to manage Rebuild triggers.
  void _updateState({bool? isRunning, String? status, bool? isReportReady}) {
    if (isRunning != null) this.isRunning = isRunning;
    if (status != null) this.status = status;
    if (isReportReady != null) this.isReportReady = isReportReady;
    notifyListeners();
  }
}
