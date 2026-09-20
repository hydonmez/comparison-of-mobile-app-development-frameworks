import 'dart:io';
import 'dart:math' as math;
import 'package:flutter/foundation.dart';
import 'package:flutter/scheduler.dart';
import 'package:flutter/services.dart';
import '../../models/performance_log.dart';

/// Defines the benchmark configuration types.
enum BenchmarkType {
  macro, // Long tests (1.0s interval, 2.0s warm-up)
  micro, // Short tests (0.25s interval, 0.0s warm-up)
}

class PerformanceManager extends ChangeNotifier {
  static final PerformanceManager shared = PerformanceManager._internal();

  PerformanceManager._internal();

  bool isMeasuring = false;
  PerformanceLog? latestLog;
  final List<PerformanceLog> _logsBuffer = [];
  List<PerformanceLog> get currentLogs => List.unmodifiable(_logsBuffer);

  int currentFPS = 0;
  String thermalStateString = "Nominal";

  Ticker? _ticker;
  Duration _windowStartTime = Duration.zero;
  Duration _testStartTime = Duration.zero;
  int _frameCount = 0;
  Duration _lastMetricCaptureTime = Duration.zero;

  double _baselineRAM = 0.0;

  // Stores the currently active benchmark type.
  BenchmarkType _currentTestType = BenchmarkType.macro;

  static const MethodChannel _hardwareChannel = MethodChannel(
    'com.benchmark.hardware',
  );

  /// Initializes monitoring with a specified benchmark type (defaults to macro).
  Future<void> startMonitoring({
    BenchmarkType type = BenchmarkType.macro,
  }) async {
    stopMonitoring();
    isMeasuring = true;
    _currentTestType = type; // Save the selected benchmark type

    _logsBuffer.clear();
    latestLog = null;
    currentFPS = 0;
    thermalStateString = "Nominal";

    _windowStartTime = Duration.zero;
    _testStartTime = Duration.zero;
    _frameCount = 0;
    _lastMetricCaptureTime = Duration.zero;

    _baselineRAM = await _fetchNativeRAMFallback();

    _ticker = Ticker(_onFrameTick);
    _ticker?.start();
  }

  void stopMonitoring() {
    _ticker?.stop();
    _ticker?.dispose();
    _ticker = null;

    isMeasuring = false;
    currentFPS = 0;
    _frameCount = 0;

    notifyListeners();
  }

  void _onFrameTick(Duration elapsed) {
    if (!isMeasuring) return;

    if (_windowStartTime == Duration.zero) {
      _windowStartTime = elapsed;
      _testStartTime = elapsed;
      _lastMetricCaptureTime = elapsed;
      return;
    }

    _frameCount++;

    final Duration deltaFromLastCapture = elapsed - _lastMetricCaptureTime;
    final double deltaSeconds = deltaFromLastCapture.inMicroseconds / 1000000.0;

    final Duration totalElapsed = elapsed - _testStartTime;
    final double totalElapsedSeconds = totalElapsed.inMicroseconds / 1000000.0;

    // Dynamically configure thresholds based on the benchmark type.
    final double targetInterval = (_currentTestType == BenchmarkType.micro)
        ? 0.25
        : 1.0;
    final double warmupThreshold = (_currentTestType == BenchmarkType.micro)
        ? 0.0
        : 2.0;

    if (deltaSeconds >= targetInterval) {
      currentFPS = (_frameCount / deltaSeconds).round();
      _frameCount = 0;
      _lastMetricCaptureTime = elapsed;

      // Smart warm-up filter.
      if (totalElapsedSeconds >= warmupThreshold) {
        _captureMetrics(deltaSeconds, currentFPS);
      }
    }
  }

  Future<void> _captureMetrics(double deltaSeconds, int snapshotFPS) async {
    double cpuUsage = 0.0;
    double batteryLevel = 0.0;
    String thermalState = "Nominal";
    double rawRAM = _baselineRAM;

    try {
      final Map<dynamic, dynamic>? hardwareData = await _hardwareChannel
          .invokeMapMethod('getHardwareMetrics', {
            'deltaSeconds':
                deltaSeconds, // The native side uses this delta for calculation
          });

      if (hardwareData != null) {
        cpuUsage = (hardwareData['cpu'] as num?)?.toDouble() ?? 0.0;
        batteryLevel = (hardwareData['battery'] as num?)?.toDouble() ?? 0.0;
        thermalState = hardwareData['thermal'] as String? ?? "Nominal";
        rawRAM =
            (hardwareData['ram'] as num?)?.toDouble() ?? _getSafeRAMUsage();
      }
    } catch (_) {
      rawRAM = _getSafeRAMUsage();
    }

    final double netRAM = math.max(0.0, rawRAM - _baselineRAM);
    thermalStateString = thermalState;

    final log = PerformanceLog(
      timestampMillis: DateTime.now().millisecondsSinceEpoch,
      cpuUsage: cpuUsage,
      rawRAM: rawRAM,
      netRAM: netRAM,
      batteryLevel: batteryLevel,
      fps: snapshotFPS,
      thermalState: thermalState,
    );

    _logsBuffer.add(log);
    latestLog = log;
    notifyListeners();
  }

  double _getSafeRAMUsage() {
    return ProcessInfo.currentRss / (1024 * 1024);
  }

  Future<double> _fetchNativeRAMFallback() async {
    try {
      final Map<dynamic, dynamic>? data = await _hardwareChannel
          .invokeMapMethod('getHardwareMetrics', {'deltaSeconds': 1.0});
      if (data != null && data['ram'] != null) {
        return (data['ram'] as num).toDouble();
      }
    } catch (_) {}
    return _getSafeRAMUsage();
  }
}
