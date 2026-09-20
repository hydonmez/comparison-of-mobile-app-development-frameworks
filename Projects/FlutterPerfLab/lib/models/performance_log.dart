import 'package:intl/intl.dart';

/// An immutable Data Transfer Object (DTO) representing a temporal snapshot of hardware telemetry.
///
/// Architected with final properties to guarantee thread-safe boundary crossings when
/// transferring telemetry data between isolates.
class PerformanceLog {
  final int timestampMillis;
  final double cpuUsage;

  /// Absolute physical memory consumption footprint (MB).
  final double rawRAM;

  /// Relative application memory overhead (Net Delta in MB).
  final double netRAM;

  final double batteryLevel;
  final int fps;
  final String thermalState;

  const PerformanceLog({
    required this.timestampMillis,
    required this.cpuUsage,
    required this.rawRAM,
    required this.netRAM,
    required this.batteryLevel,
    required this.fps,
    required this.thermalState,
  });

  /// O(1) identifier optimization.
  ///
  /// Eliminates UUID generation overhead and Garbage Collection churn.
  /// Directly utilized by the UI element tree for efficient diffing.
  int get id => timestampMillis;

  /// Lazily formats the absolute timestamp into a human-readable metric.
  ///
  /// Defers DateTime parsing and formatting allocation overhead until explicitly
  /// required by the presentation layer or serialization pipeline.
  String get formattedTime {
    return _timeFormatter.format(
      DateTime.fromMillisecondsSinceEpoch(timestampMillis),
    );
  }

  /// Thread-safe, statically allocated formatter.
  ///
  /// Initialized exactly once per application lifecycle, guaranteeing zero runtime
  /// memory allocation cost during high-frequency telemetry sampling.
  static final DateFormat _timeFormatter = DateFormat('HH:mm:ss');
}
