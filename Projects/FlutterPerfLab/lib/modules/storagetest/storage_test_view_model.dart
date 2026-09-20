import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:share_plus/share_plus.dart';

import 'package:flutterperflab/core/managers/performance_manager.dart';
import 'package:flutterperflab/core/managers/export_manager.dart';
import 'package:flutterperflab/core/engine/storage_engine.dart';

class StorageTestViewModel extends ChangeNotifier {
  // MARK: - Reactive UI State
  double progress = 0.0;
  bool isRunning = false;
  String status = "Ready";

  bool isWriteCompleted = false;
  bool isReportReady = false;

  String _lastTestName = "";
  final PerformanceManager _performance = PerformanceManager.shared;

  // MARK: - Benchmark Lifecycle

  Future<void> runBenchmark(bool isWrite) async {
    if (isRunning) return;

    isRunning = true;
    isReportReady = false;
    progress = 0.0;
    status = isWrite
        ? "Writing 2GB Fixed Payload..."
        : "Reading 2GB Fixed Payload...";
    notifyListeners();

    // Sets the benchmark type to 'micro' to configure the PerformanceManager
    // for 0.25s intervals without warm-up delays.
    _performance.startMonitoring(type: BenchmarkType.micro);

    try {
      if (isWrite) {
        await StorageEngine.shared.writeData(
          megabytes: 2048,
          onProgress: _onProgressUpdate,
        );
      } else {
        await StorageEngine.shared.readData(onProgress: _onProgressUpdate);
      }

      _finishTest(isWrite);
    } catch (e) {
      status = "Error: $e";
      isRunning = false;
      _performance.stopMonitoring();
      notifyListeners();
    }
  }

  void _finishTest(bool isWrite) {
    _performance.stopMonitoring();

    _lastTestName = isWrite
        ? "Storage_Write_Flutter_2GB"
        : "Storage_Read_Flutter_2GB";
    isRunning = false;
    status = isWrite ? "Write Completed ✅" : "Read Completed ✅";

    if (isWrite) {
      isWriteCompleted = true;
    }

    isReportReady = true;
    notifyListeners();
  }

  // MARK: - Internal Throttle & Serialization

  void _onProgressUpdate(double p) {
    if ((p - progress) >= 0.01 || p >= 1.0) {
      progress = p;
      notifyListeners();
    }
  }

  Future<void> shareResults() async {
    try {
      final file = await ExportManager.shared.generateCSV(
        logs: _performance.currentLogs,
        testName: _lastTestName,
      );

      if (file != null) {
        final xFile = XFile(file.path);
        await Share.shareXFiles([xFile]);
      } else {
        status = "❌ CSV Export Failed (Empty)";
        notifyListeners();
      }
    } catch (e) {
      status = "❌ Share Error: $e";
      notifyListeners();
    }
  }

  @override
  void dispose() {
    if (isRunning) _performance.stopMonitoring();
    super.dispose();
  }
}
