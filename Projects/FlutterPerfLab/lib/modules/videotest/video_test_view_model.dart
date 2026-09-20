import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:share_plus/share_plus.dart';

import 'package:flutterperflab/core/managers/performance_manager.dart';
import 'package:flutterperflab/core/managers/export_manager.dart';
import 'package:flutterperflab/core/engine/video_engine.dart';

/// A centralized controller managing the lifecycle of high-frequency video decoding benchmarks.
class VideoTestViewModel extends ChangeNotifier {
  // MARK: - Reactive UI State

  bool isTestingActive = false;
  bool isFullScreen = false;
  bool isReportReady = false;

  // CORE DEPENDENCIES
  final PerformanceManager _perfManager = PerformanceManager.shared;
  final VideoEngine engine = VideoEngine.shared;

  VideoTestViewModel() {
    setupBindings();
  }

  void setupBindings() {
    engine.addListener(_onEngineStateChanged);
  }

  void _onEngineStateChanged() {
    // Safely terminates the benchmark ONLY when the underlying native decoder
    // explicitly broadcasts an End-Of-File state.
    if (engine.hasEnded && isTestingActive) {
      if (kDebugMode) {
        print(
          "[Benchmark] Video EOF Detected -> Finalizing benchmark and generating report.",
        );
      }

      // Triggers stopTest() to finalize the report and display the Export button.
      stopTest(isFinished: true);
    }
  }

  // MARK: - Hardware Resource Allocation

  Future<void> prepareVideo() async {
    isReportReady = false;
    notifyListeners();

    await engine.prepareVideo(name: "test_video_1080p", ext: "mp4");
  }

  // MARK: - Benchmark Initialization

  Future<void> startTest() async {
    if (isTestingActive) return;

    if (engine.controller == null) {
      await prepareVideo();
    }

    isReportReady = false;
    isFullScreen = true;
    isTestingActive = true;
    notifyListeners();

    _perfManager.startMonitoring();
    await engine.playFromStart();
  }

  // MARK: - Data Persistence & Finalization

  Future<void> stopTest({required bool isFinished}) async {
    if (!isTestingActive && !isFinished) return;

    isTestingActive = false;
    isFullScreen = false;
    notifyListeners();

    await engine.stop();
    _perfManager.stopMonitoring();

    // Restores default UI state and orientation upon termination.
    await SystemChrome.setPreferredOrientations([
      DeviceOrientation.portraitUp,
      DeviceOrientation.portraitDown,
    ]);
    await SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);

    final String lastTestSuffix = isFinished ? "Complete" : "Partial";

    // Asynchronous I/O Serialization
    try {
      final file = await ExportManager.shared.generateCSV(
        logs: _perfManager.currentLogs,
        testName: "Video_FullScreen_Flutter_$lastTestSuffix",
      );

      if (file != null) {
        isReportReady = true;
        notifyListeners(); // Exposes the Export button on the UI.
      }
    } catch (e) {
      if (kDebugMode) {
        print("Export Failed: $e");
      }
    }
  }

  // MARK: - Inter-Process Communication

  Future<void> shareResults() async {
    if (!isReportReady) return;

    try {
      final file = await ExportManager.shared.generateCSV(
        logs: _perfManager.currentLogs,
        testName: "Video_FullScreen_Flutter_ManualExport",
      );

      if (file != null) {
        final xFile = XFile(file.path);
        await Share.shareXFiles([xFile]);
      }
    } catch (_) {}
  }

  // MARK: - Hardware Orientation Override

  Future<void> forceRotation({required bool isLandscape}) async {
    if (isLandscape) {
      await SystemChrome.setPreferredOrientations([
        DeviceOrientation.landscapeRight,
        DeviceOrientation.landscapeLeft,
      ]);
      await SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    } else {
      await SystemChrome.setPreferredOrientations([
        DeviceOrientation.portraitUp,
        DeviceOrientation.portraitDown,
      ]);
      await SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    }
  }

  @override
  void dispose() {
    engine.removeListener(_onEngineStateChanged);
    engine.release();
    super.dispose();
  }
}
