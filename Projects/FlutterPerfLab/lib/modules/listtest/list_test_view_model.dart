import 'dart:async';
import 'dart:math' as math;
import 'package:flutter/foundation.dart';
import 'package:flutter/animation.dart'; // Required for Curve/Curves
import 'package:share_plus/share_plus.dart';

import 'package:flutterperflab/core/managers/performance_manager.dart';
import 'package:flutterperflab/core/managers/list_test_manager.dart';
import 'package:flutterperflab/core/managers/export_manager.dart';
import 'package:flutterperflab/models/product_data.dart';

/// Encapsulates the parameters for UI scrolling to ensure precise mechanical
/// and visual benchmarks.
class ScrollCommand {
  final int targetIndex;
  final Duration duration;
  final Curve curve;

  const ScrollCommand({
    required this.targetIndex,
    required this.duration,
    required this.curve,
  });
}

// Bezier curves replicating iOS CoreAnimation physics.
const Curve swiftEaseOut = Cubic(0.0, 0.0, 0.58, 1.0);
const Curve swiftEaseInOut = Cubic(0.42, 0.0, 0.58, 1.0);

/// A ViewModel engineered to orchestrate automated, deterministic UI scrolling benchmarks.
class ListTestViewModel extends ChangeNotifier {
  // MARK: - Reactive UI State
  List<Product> products = [];
  bool isRunning = false;
  bool isLoadingMore = false;
  String status = "Ready";
  bool isReportReady = false;

  final PerformanceManager _performance = PerformanceManager.shared;

  // Transmits the exact ScrollCommand (index, duration, easing) to bypass
  // full widget tree recomposition.
  final StreamController<ScrollCommand> _scrollCommandController =
      StreamController<ScrollCommand>.broadcast();
  Stream<ScrollCommand> get scrollCommandStream =>
      _scrollCommandController.stream;

  bool _isTaskCancelled = false;
  final int _pageSize = 20;
  final int _targetCount = 300;

  // MARK: - Benchmark Initialization

  Future<void> startTest() async {
    stopTest(isFinished: false);

    _isTaskCancelled = false;
    products.clear();
    isRunning = true;
    status = "Preparing Dataset...";
    isReportReady = false;
    notifyListeners();

    try {
      await ListTestManager.shared.init();
    } catch (e) {
      status = "Error: Dataset missing. $e";
      isRunning = false;
      notifyListeners();
      return;
    }

    _performance.startMonitoring();
    ListTestManager.shared.resetCursor();

    await _loadMoreData();
    await Future.delayed(const Duration(milliseconds: 420));

    _runAutoScrollScenario();
  }

  Future<void> _loadMoreData() async {
    if (isLoadingMore) return;

    isLoadingMore = true;
    notifyListeners();

    await Future.delayed(const Duration(milliseconds: 100));

    final newItems = ListTestManager.shared.fetchPage(_pageSize);

    products.addAll(newItems);
    isLoadingMore = false;

    // UI Notification for new items.
    notifyListeners();
  }

  // MARK: - Automated Interaction Scenarios

  Future<void> _runAutoScrollScenario() async {
    int index = 0;

    // Downward Scrolling & Pagination Stress Test
    while (index < (_targetCount - 5)) {
      if (_isTaskCancelled || !isRunning) break;

      final int currentCount = products.length;
      final int next = math.min(index + 2, currentCount - 1);

      if (next > index) {
        index = next;
        status = "Scrolling Down... ($index/$currentCount)";

        // Notify UI only for text updates, ensuring the view restricts rebuilds
        // to the Text widget, NOT the entire ListView.
        notifyListeners();

        // Emitting precise command with duration and linear curve.
        _scrollCommandController.add(
          ScrollCommand(
            targetIndex: index,
            duration: const Duration(milliseconds: 800),
            curve: Curves.linear,
          ),
        );

        // Delay: 800ms travel time + 16ms buffer (60Hz).
        await Future.delayed(const Duration(milliseconds: 816));
      } else {
        await Future.delayed(const Duration(milliseconds: 500));
      }

      if (!isLoadingMore &&
          currentCount < _targetCount &&
          index >= currentCount - 10) {
        _loadMoreData();
      }
    }

    if (_isTaskCancelled || !isRunning) return;

    // Target Traversal Achieved
    if (products.isNotEmpty) {
      status = "Target Achieved";
      notifyListeners();

      _scrollCommandController.add(
        ScrollCommand(
          targetIndex: products.length - 1,
          duration: const Duration(milliseconds: 1500),
          curve: swiftEaseOut,
        ),
      );

      await Future.delayed(const Duration(milliseconds: 1516));
    }

    if (_isTaskCancelled || !isRunning) return;

    // Ascending Traverse
    if (products.isNotEmpty) {
      status = "Returning to Top...";
      notifyListeners();

      _scrollCommandController.add(
        ScrollCommand(
          targetIndex: 0,
          duration: const Duration(milliseconds: 2500),
          curve: swiftEaseInOut,
        ),
      );

      await Future.delayed(const Duration(milliseconds: 2516));
    }

    if (!_isTaskCancelled) {
      stopTest(isFinished: true);
    }
  }

  void stopTest({required bool isFinished}) {
    _isTaskCancelled = true;

    if (!isRunning) return;

    _performance.stopMonitoring();
    isRunning = false;
    status = isFinished ? "Test Finalized" : "Terminated";
    isReportReady = isFinished;

    notifyListeners();
  }

  // MARK: - Telemetry Export Pipeline

  Future<void> exportResults() async {
    try {
      final file = await ExportManager.shared.generateCSV(
        logs: _performance.currentLogs,
        testName: "Ecommerce_Fluent_Test_Flutter",
      );

      if (file != null) {
        final xFile = XFile(file.path);
        await Share.shareXFiles([xFile]);
      }
    } catch (e) {
      status = "Export Failed: $e";
      notifyListeners();
    }
  }

  @override
  void dispose() {
    _isTaskCancelled = true;
    _scrollCommandController.close();
    if (isRunning) _performance.stopMonitoring();
    super.dispose();
  }
}
