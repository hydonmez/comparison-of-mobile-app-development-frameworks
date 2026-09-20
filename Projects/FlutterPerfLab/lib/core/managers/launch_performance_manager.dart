import 'package:flutter/foundation.dart';

/// A Main-Isolate-bound diagnostic engine engineered to measure application launch latency.
///
/// Utilizing Dart's internal Stopwatch provides access to the OS-level high-resolution
/// monotonic clock. This isolates the user-space initialization overhead independently
/// of any network time synchronizations or manual clock modifications.
class LaunchPerformanceManager extends ChangeNotifier {
  // Singleton pattern for global telemetry access, preventing multiple clock instances.
  static final LaunchPerformanceManager shared =
      LaunchPerformanceManager._internal();
  LaunchPerformanceManager._internal();

  // MARK: - Telemetry Outputs

  double totalColdStartMs = 0.0;
  double osDurationMs = 0.0;
  double softwareDurationMs = 0.0;
  double hotStartMs = 0.0;

  // MARK: - Internal Hardware Clock

  // A globally initialized hardware clock strictly for monotonic time tracking.
  static final Stopwatch _hardwareClock = Stopwatch()..start();

  // Timestamps recorded in pure microseconds (to prevent precision loss before division)
  int? _startTimeMicro;
  int? _osReadyTimeMicro;
  int? _hotStartWakeTimeMicro;

  bool _isColdStartReported = false;

  // MARK: - Cold Start Tracking

  /// Captures the initial baseline timestamp during the very first line of main().
  void appStarted() {
    _startTimeMicro = _hardwareClock.elapsedMicroseconds;
  }

  /// Captures the timestamp when the Flutter Engine finishes bootstrapping
  /// and the first widget is attached to the tree.
  void osReady() {
    _osReadyTimeMicro = _hardwareClock.elapsedMicroseconds;
  }

  /// Finalizes the Cold Start telemetry cycle and computes Time to Interactive (TTI).
  void reportBenchmark() {
    if (_isColdStartReported ||
        _startTimeMicro == null ||
        _osReadyTimeMicro == null) {
      return;
    }

    final int now = _hardwareClock.elapsedMicroseconds;

    // Granular Latency Computation:
    // Dividing by 1000.0 safely converts hardware-precise microseconds into
    // human-readable milliseconds.
    osDurationMs = (_osReadyTimeMicro! - _startTimeMicro!) / 1000.0;
    softwareDurationMs = (now - _osReadyTimeMicro!) / 1000.0;
    totalColdStartMs = (now - _startTimeMicro!) / 1000.0;

    _isColdStartReported = true;

    // Purging raw timestamps to free memory
    _startTimeMicro = null;
    _osReadyTimeMicro = null;

    // Notify listeners precisely once at the end of the calculation to prevent GC churn.
    notifyListeners();
  }

  // MARK: - Hot Start Tracking

  /// Captures the hardware timestamp when the app transitions from paused to resumed.
  void appIsWakingUp() {
    _hotStartWakeTimeMicro = _hardwareClock.elapsedMicroseconds;
  }

  /// Finalizes the Hot Start telemetry, calculating the RAM-to-Foreground rendering latency.
  void hotStartDetected() {
    if (!_isColdStartReported || _hotStartWakeTimeMicro == null) {
      _hotStartWakeTimeMicro = null;
      return;
    }

    final int now = _hardwareClock.elapsedMicroseconds;
    hotStartMs = (now - _hotStartWakeTimeMicro!) / 1000.0;

    _hotStartWakeTimeMicro = null;

    notifyListeners();
  }
}

// MARK: - View Rendering Optimizations

/// Relocates CPU-bound string formatting operations out of the Flutter build method.
/// Pre-formatting these properties securely within the ViewModel prevents redundant
/// String memory allocations during high-frequency Widget redraws.
extension LaunchPerformanceFormatters on LaunchPerformanceManager {
  String get formattedColdStart => totalColdStartMs.toStringAsFixed(1);

  String get formattedOSDuration => osDurationMs.toStringAsFixed(0);

  String get formattedUIDuration => softwareDurationMs.toStringAsFixed(0);

  String get formattedHotStart =>
      hotStartMs > 0 ? hotStartMs.toStringAsFixed(1) : "--";
}
