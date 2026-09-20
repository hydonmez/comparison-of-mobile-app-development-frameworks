import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:share_plus/share_plus.dart';

import 'package:flutterperflab/core/managers/performance_manager.dart';
import 'package:flutterperflab/core/managers/export_manager.dart';
import 'package:flutterperflab/core/engine/audio_engine.dart';

/// A lifecycle-aware orchestrator responsible for managing audio playback lifecycle
/// and UI synchronization.
///
/// 1. Implements a 100ms telemetry debounce delay to prevent the capture of initial hardware allocation CPU spikes.
/// 2. Implements strict Complete vs. Partial CSV taxonomy for data integrity.
class AudioTestViewModel extends ChangeNotifier {
  // MARK: - Reactive UI State
  bool isTesting = false;
  bool isAudioLoaded = false;
  String? errorMessage;

  final PerformanceManager _perfManager = PerformanceManager.shared;
  final AudioEngine _engine = AudioEngine.shared;

  // Asynchronous telemetry start timer to isolate benchmark from UI thread locks.
  Timer? _telemetryStartTimer;

  // Binds directly to the AudioEngine's state without creating redundant objects.
  double get currentTime => _engine.currentTime;
  double get totalDuration => _engine.totalDuration;
  bool get isPlaying => _engine.isPlaying;

  AudioTestViewModel() {
    _engine.addListener(_onEngineStateChanged);
  }

  void _onEngineStateChanged() {
    // Safely terminates the benchmark ONLY when the underlying native decoder
    // explicitly broadcasts an End-Of-File state.
    if (isTesting && _engine.hasEnded) {
      stopTest(isFinished: true);
    } else {
      notifyListeners();
    }
  }

  // MARK: - Benchmark Initialization

  /// Eagerly loads the benchmark audio asset asynchronously.
  Future<void> loadTestAudio() async {
    const String resourceName = "assets/test_audio_high.mp3";

    try {
      final bool success = await _engine.loadAudio(resourceName);
      isAudioLoaded = success;

      if (!success) {
        errorMessage =
            "Asset Missing: '$resourceName' could not be located in the bundle.";
      }
    } catch (e) {
      isAudioLoaded = false;
      errorMessage = "Asset Corrupted: The audio file is unreadable. $e";
    }

    notifyListeners();
  }

  // MARK: - Benchmark Actions

  void startTest() {
    if (!isAudioLoaded || isTesting) return;

    // Resets playhead if starting at the very end.
    if (totalDuration > 0 && currentTime >= totalDuration - 0.1) {
      _engine.seek(0.0);
    }

    _engine.play();
    isTesting = true;
    notifyListeners();

    // Defers telemetry recording by 100ms to allow the underlying audio codec
    // to allocate buffers, preventing false CPU spikes.
    _telemetryStartTimer?.cancel();
    _telemetryStartTimer = Timer(const Duration(milliseconds: 100), () {
      if (isTesting) {
        _perfManager.startMonitoring();
      }
    });
  }

  Future<void> stopTest({bool isFinished = false}) async {
    if (!isTesting) {
      _engine.pause();
      return;
    }

    _telemetryStartTimer?.cancel();
    _telemetryStartTimer = null;

    _engine.pause();
    isTesting = false;
    _perfManager.stopMonitoring();
    notifyListeners();

    // Async I/O Serialization
    try {
      // Differentiates between manual stops and physical EOF.
      final String testTaxonomyName = isFinished
          ? "Audio_Flutter_Complete"
          : "Audio_Flutter_Partial";

      final file = await ExportManager.shared.generateCSV(
        logs: _perfManager.currentLogs,
        testName: testTaxonomyName,
      );

      if (file != null) {
        final xFile = XFile(file.path);
        await Share.shareXFiles([xFile]);
      } else {
        errorMessage = "I/O Failure: Telemetry serialization failed.";
        notifyListeners();
      }
    } catch (e) {
      errorMessage = "Audio Telemetry Export Failed: $e";
      notifyListeners();
    }
  }

  void seekAudio(double time) {
    if (!isAudioLoaded) return;

    _engine.seek(time);

    // Stops the test automatically if seeking to EOF during a benchmark.
    if (isTesting && totalDuration > 0 && time >= (totalDuration - 0.2)) {
      stopTest(isFinished: true);
    } else if (isTesting) {
      // Explicitly halts test upon mid-track intervention to preserve CSV integrity.
      stopTest(isFinished: false);
    }
  }

  void skip(double seconds) {
    if (!isAudioLoaded) return;

    _engine.skip(seconds);

    // Checks logical EOF after skipping.
    if (isTesting &&
        totalDuration > 0 &&
        currentTime >= (totalDuration - 0.2)) {
      stopTest(isFinished: true);
    } else if (isTesting) {
      stopTest(isFinished: false);
    }
  }

  /// Eliminates floating-point String formatting overhead during 60FPS UI repaints.
  String formatTime(double time) {
    if (time.isNaN || time.isInfinite) return "00:00";
    final int minutes = (time / 60).toInt();
    final int seconds = (time % 60).toInt();
    final String minStr = minutes.toString().padLeft(2, '0');
    final String secStr = seconds.toString().padLeft(2, '0');
    return "$minStr:$secStr";
  }

  // MARK: - Lifecycle Management

  @override
  void dispose() {
    if (isTesting) {
      _perfManager.stopMonitoring();
    }
    _telemetryStartTimer?.cancel();
    _engine.removeListener(_onEngineStateChanged);
    _engine.release();
    super.dispose();
  }
}
