import 'dart:io';
import 'dart:math' as math;
import 'dart:isolate';
import 'package:intl/intl.dart';
import 'package:path_provider/path_provider.dart';
import '../../models/performance_log.dart';

/// A robust, high-throughput utility class responsible for aggregating, processing,
/// and exporting raw telemetry data.
///
/// Executes heavy string interpolation, statistical aggregation, and Disk I/O
/// entirely within a detached background isolate to prevent UI frame drops.
class ExportManager {
  // Singleton pattern ensuring a single access point for file operations.
  static final ExportManager shared = ExportManager._internal();
  ExportManager._internal();

  /// Generates a structured CSV file from a collection of performance logs.
  Future<File?> generateCSV({
    required List<PerformanceLog> logs,
    required String testName,
    String? customSummary,
  }) async {
    if (logs.isEmpty) return null;

    // Directory paths must be resolved on the Main Isolate since standard
    // MethodChannels cannot be directly invoked from a background Isolate.
    final Directory tempDir = await getTemporaryDirectory();
    final String tempPath = tempDir.path;

    // Spawns a lightweight background thread. Memory is safely transferred (copied)
    // to the new isolate, ensuring thread safety and preventing data races.
    return await Isolate.run(() async {
      try {
        final int timestamp = DateTime.now().millisecondsSinceEpoch ~/ 1000;
        final String fileName = "${testName}_$timestamp.csv";
        final File file = File('$tempPath/$fileName');

        // NumberFormat strictly binds to US locale to guarantee cross-platform
        // delimiter integrity (forcing '.' for decimals instead of ',').
        final NumberFormat format2Decimals = NumberFormat("0.00", "en_US");
        final NumberFormat format1Decimal = NumberFormat("0.0", "en_US");

        // Utilizing a StringBuffer minimizes Garbage Collection pressure and
        // localized heap fragmentation during massive string concatenations.
        final StringBuffer buffer = StringBuffer();

        // --- HEADER ---
        buffer.writeln(
          "Timestamp,CPU(%),Raw_RAM(MB),Net_RAM(MB),Battery(%),FPS,Thermal_State",
        );

        // --- RAW LOGS ---
        for (final log in logs) {
          buffer.write("${log.formattedTime},");
          buffer.write("${format2Decimals.format(log.cpuUsage)},");
          buffer.write("${format2Decimals.format(log.rawRAM)},");
          buffer.write("${format2Decimals.format(log.netRAM)},");
          buffer.write("${format1Decimal.format(log.batteryLevel)},");
          buffer.write("${log.fps},");
          buffer.writeln(
            log.thermalState,
          ); // writeln appends the \n automatically
        }

        // --- SUMMARY ---
        buffer.writeln("\n--- SUMMARY ---");
        buffer.writeln(_calculateSummary(logs, format2Decimals));

        if (customSummary != null) {
          buffer.writeln("\n$customSummary");
        }

        // Write the complete buffer to Disk sequentially (I/O bound)
        await file.writeAsString(buffer.toString());
        return file;
      } catch (e) {
        // Fallback for file permission issues or out-of-storage scenarios
        return null;
      }
    });
  }

  // MARK: - Statistical Aggregation

  /// Calculates core statistical metrics (Average, Standard Deviation, Min, Max)
  /// within the isolated memory space.
  static String _calculateSummary(
    List<PerformanceLog> logs,
    NumberFormat formatter,
  ) {
    final Iterable<double> cpu = logs.map((e) => e.cpuUsage);
    final Iterable<double> rawRam = logs.map((e) => e.rawRAM);
    final Iterable<double> netRam = logs.map((e) => e.netRAM);
    final Iterable<double> fps = logs.map((e) => e.fps.toDouble());

    double avg(Iterable<double> v) {
      if (v.isEmpty) return 0.0;
      return v.reduce((a, b) => a + b) / v.length;
    }

    /// Calculates the Sample Standard Deviation using Bessel's correction (N-1).
    /// Guard clause prevents division-by-zero (NaN/Infinity).
    double std(Iterable<double> v) {
      if (v.length <= 1) return 0.0;
      final double mean = avg(v);
      final double sumOfSquaredDifferences = v
          .map((e) => math.pow(e - mean, 2).toDouble())
          .reduce((a, b) => a + b);
      return math.sqrt(sumOfSquaredDifferences / (v.length - 1));
    }

    String formatStat(String name, Iterable<double> v) {
      final double m = avg(v);
      final double s = std(v);
      final double minVal = v.isEmpty ? 0.0 : v.reduce(math.min);
      final double maxVal = v.isEmpty ? 0.0 : v.reduce(math.max);

      return "$name,${formatter.format(m)},${formatter.format(s)},${formatter.format(minVal)},${formatter.format(maxVal)}";
    }

    final StringBuffer sb = StringBuffer();
    sb.writeln("Metric,Average,StdDev,Min,Max");
    sb.writeln(formatStat("CPU(%)", cpu));
    sb.writeln(formatStat("Raw_RAM(MB)", rawRam));
    sb.writeln(formatStat("Net_RAM(MB)", netRam));
    sb.write(formatStat("FPS", fps)); // No trailing newline on the last item

    return sb.toString();
  }
}
