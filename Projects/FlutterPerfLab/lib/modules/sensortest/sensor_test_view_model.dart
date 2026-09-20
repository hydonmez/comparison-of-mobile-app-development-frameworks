import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:share_plus/share_plus.dart';

import '../../core/managers/performance_manager.dart';
import '../../core/managers/sensor_test_manager.dart';
import '../../core/managers/export_manager.dart';

/// A lifecycle-aware ViewModel orchestrating high-frequency (100Hz) sensor telemetry.
///
/// Employs a pure monotonic Stopwatch to guarantee absolute temporal precision
/// and prevent timer drift during the benchmark.
class SensorTestViewModel extends ChangeNotifier {
  // MARK: - Reactive UI State
  bool isRunning = false;
  String status = "Sensors Ready";
  bool isReportReady = false;
  double progress = 0.0;

  // MARK: - Hoisted Telemetry State
  // Primitive arrays optimized for low-memory representation.
  List<double> accelData = [0.0, 0.0, 0.0];
  List<double> gyroData = [0.0, 0.0, 0.0];
  List<double> magnetData = [0.0, 0.0, 0.0];
  int stepCount = 0;

  // MARK: - Manager Integration
  final PerformanceManager _performance = PerformanceManager.shared;
  final SensorTestManager _sensorManager = SensorTestManager.shared;

  // Standardized benchmark duration (60 seconds) strictly enforced.
  final double _testDuration = 60.0;

  // Concurrency & Timing Management
  Timer? _timerTask;
  final Stopwatch _hardwareClock = Stopwatch();

  // MARK: - Benchmark Lifecycle

  void startTest() {
    if (isRunning) return;

    isRunning = true;
    status = "Acquiring Telemetry (100Hz)...";
    isReportReady = false;
    progress = 0.0;

    _performance.startMonitoring();

    // Directly invokes the Singleton engine to prevent allocation overhead.
    _sensorManager.startSensors(
      accelCallback: (data) {
        accelData = data;
        notifyListeners();
      },
      gyroCallback: (data) {
        gyroData = data;
        notifyListeners();
      },
      magnetCallback: (data) {
        magnetData = data;
        notifyListeners();
      },
      stepCallback: (count) {
        stepCount = count;
        notifyListeners();
      },
    );

    // Captures a monotonic baseline to eliminate timer drift.
    _hardwareClock.reset();
    _hardwareClock.start();

    // Cooperative event loop polling with a 100ms delay.
    _timerTask = Timer.periodic(const Duration(milliseconds: 100), (_) {
      _updateProgress();
    });

    notifyListeners();
  }

  void stopTest({bool isFinished = false}) {
    if (!isRunning) return;

    isRunning = false;
    status = isFinished ? "✅ Test Finalized" : "🛑 Terminated";

    // Gracefully cancel the asynchronous timing task.
    _timerTask?.cancel();
    _timerTask = null;
    _hardwareClock.stop();

    // Hardware Teardown
    _sensorManager.stopSensors();

    _performance.stopMonitoring();

    // Flush stale telemetry data to ensure visual cleanliness and GC release.
    accelData = [0.0, 0.0, 0.0];
    gyroData = [0.0, 0.0, 0.0];
    magnetData = [0.0, 0.0, 0.0];
    stepCount = 0;

    if (isFinished) {
      progress = 1.0;
      isReportReady = true;
    } else {
      progress = 0.0;
      isReportReady = false;
    }

    notifyListeners();
  }

  // Exports telemetry via the native share sheet.
  Future<void> exportResults() async {
    try {
      final file = await ExportManager.shared.generateCSV(
        logs: _performance.currentLogs,
        testName: "Sensor_Flutter_Stress_100Hz",
        customSummary: "Total_Steps,$stepCount,,,,",
      );

      if (file != null) {
        final xFile = XFile(file.path);
        await Share.shareXFiles([xFile]);
        status = "✅ Export Ready.";
      } else {
        status = "❌ CSV Export Failed (Empty logs)";
      }
    } catch (e) {
      status = "❌ CSV Export Failed: $e";
    }
    notifyListeners();
  }

  // MARK: - Internal Engine

  void _updateProgress() {
    // Always reads from the monotonic clock to compensate for timer inaccuracies.
    final double elapsedSeconds = _hardwareClock.elapsedMilliseconds / 1000.0;

    progress = (elapsedSeconds / _testDuration).clamp(0.0, 1.0);

    if (elapsedSeconds >= _testDuration) {
      stopTest(isFinished: true);
    } else {
      notifyListeners();
    }
  }

  @override
  void dispose() {
    stopTest();
    super.dispose();
  }
}
