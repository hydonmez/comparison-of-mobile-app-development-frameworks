import 'dart:async';
import 'dart:isolate';
import 'package:flutter/foundation.dart';
import 'package:share_plus/share_plus.dart';

import 'package:flutterperflab/core/managers/performance_manager.dart';
import 'package:flutterperflab/core/managers/export_manager.dart';
import 'package:flutterperflab/core/managers/json_test_manager.dart';

/// Data Transfer Object (DTO) explicitly designed for Isolate messaging.
class _BenchmarkConfig {
  final SendPort sendPort;
  final Uint8List payload;
  final int iterations;

  const _BenchmarkConfig(this.sendPort, this.payload, this.iterations);
}

/// An orchestrator engineered to benchmark high-frequency JSON deserialization throughput.
///
/// By spawning exactly one isolate and running the execution loop internally,
/// it prevents OS-level thread thrashing associated with repetitive compute calls.
class JsonTestViewModel extends ChangeNotifier {
  // MARK: - Reactive UI State
  String status = "Ready";
  bool isTesting = false;
  bool isReportReady = false;
  int parsedCount = 0;

  final PerformanceManager _performance = PerformanceManager.shared;
  final int _iterations = 200;

  // MARK: - Benchmark Preparation

  /// Eagerly fetches the target dataset directly into active RAM.
  Future<void> preloadData() async {
    try {
      await JsonTestManager.shared.preloadDataOnce();
      status = "Payload Pre-loaded. Ready to Test.";
      notifyListeners();
    } catch (e) {
      status = "Preload Failed: $e";
      notifyListeners();
    }
  }

  // MARK: - Benchmark Execution

  /// Initiates the automated deserialization benchmark sequence.
  Future<void> startBenchmark() async {
    if (isTesting) return;

    final Uint8List? payload = JsonTestManager.shared.cachedPayload;
    if (payload == null) {
      status = "Error: Payload not preloaded in RAM.";
      notifyListeners();
      return;
    }

    isTesting = true;
    status = "Initiating In-Memory JSON Benchmark...";
    parsedCount = 0;
    isReportReady = false;
    notifyListeners();

    _performance.startMonitoring();

    final ReceivePort receivePort = ReceivePort();
    final config = _BenchmarkConfig(receivePort.sendPort, payload, _iterations);

    try {
      // Single isolate spawn.
      await Isolate.spawn(_runIsolatedBenchmark, config);

      receivePort.listen((dynamic message) {
        if (message is Map<String, dynamic>) {
          final String type = message['type'] as String;

          switch (type) {
            case 'progress':
              final int progress = message['value'] as int;
              status = "Processing: $progress%";
              notifyListeners();
              break;
            case 'complete':
              parsedCount = message['totalItems'] as int;
              final double durationSecs = message['durationSecs'] as double;
              final double roundedTime =
                  (durationSecs * 1000).roundToDouble() / 1000.0;
              status = "Completed in $roundedTime sec";

              receivePort.close();
              _finishTest(isSuccess: true);
              break;
            case 'error':
              status = "Error: ${message['error']}";
              receivePort.close();
              _finishTest(isSuccess: false);
              break;
          }
        }
      });
    } catch (e) {
      status = "Isolate Spawn Failed: $e";
      _finishTest(isSuccess: false);
    }
  }

  /// Isolate entry point. Executes entirely on a detached hardware thread.
  /// Operates its own memory heap.
  static void _runIsolatedBenchmark(_BenchmarkConfig config) {
    try {
      int localTotalItems = 0;
      final Stopwatch clock = Stopwatch()..start();

      // Zero-overhead loop.
      for (int i = 1; i <= config.iterations; i++) {
        final int count = JsonTestManager.shared.runParseTest(config.payload);
        localTotalItems += count;

        // UI Update Throttling: 10% interval to avoid bridge congestion.
        if (i % (config.iterations ~/ 20) == 0) {
          final progress = ((i / config.iterations) * 100).toInt();
          config.sendPort.send({'type': 'progress', 'value': progress});
        }
      }

      clock.stop();
      final double durationSecs = clock.elapsedMicroseconds / 1000000.0;

      config.sendPort.send({
        'type': 'complete',
        'totalItems': localTotalItems,
        'durationSecs': durationSecs,
      });
    } catch (e) {
      config.sendPort.send({'type': 'error', 'error': e.toString()});
    }
  }

  // MARK: - Post-Benchmark Serialization

  void _finishTest({required bool isSuccess}) {
    isTesting = false;
    _performance.stopMonitoring();
    if (isSuccess) isReportReady = true;
    notifyListeners();
  }

  Future<void> exportResults() async {
    try {
      final file = await ExportManager.shared.generateCSV(
        logs: _performance.currentLogs,
        testName: "JSON_Benchmark_Flutter_RamCached",
        customSummary: "Total_Parsed_Items,$parsedCount,,,,",
      );

      if (file != null) {
        await Share.shareXFiles([XFile(file.path)]);
      } else {
        status = "❌ CSV Export Failed (Empty logs)";
        notifyListeners();
      }
    } catch (e) {
      status = "❌ Export Failed: $e";
      notifyListeners();
    }
  }

  // MARK: - Lifecycle Management

  @override
  void dispose() {
    if (isTesting) {
      _performance.stopMonitoring();
    }
    // Purges the RAM cache to prevent Out-Of-Memory (OOM) exceptions.
    JsonTestManager.shared.releaseMemory();
    super.dispose();
  }
}
