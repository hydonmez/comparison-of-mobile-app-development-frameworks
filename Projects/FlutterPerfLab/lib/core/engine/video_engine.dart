import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:video_player/video_player.dart';

/// A Main-Isolate-bound video playback engine utilizing the video_player plugin.
class VideoEngine extends ChangeNotifier {
  // Singleton pattern to enforce single-instance allocation.
  static final VideoEngine shared = VideoEngine._internal();

  VideoPlayerController? _controller;
  bool hasEnded = false;

  VideoPlayerController? get controller => _controller;

  VideoEngine._internal();

  /// Prepares a video asset for playback.
  Future<void> prepareVideo({required String name, required String ext}) async {
    final String assetPath = 'assets/$name.$ext';

    // Purges controllers and listeners before re-allocation to prevent memory issues.
    await release();

    // Note: The video_player plugin relies on OS-level buffering defaults, which pre-caches media into RAM.
    _controller = VideoPlayerController.asset(assetPath);

    try {
      await _controller!.initialize();

      // Registering the listener after initialization prevents redundant state evaluations.
      _controller!.addListener(_videoListener);

      // Resets state to measure immediate decoder readiness.
      hasEnded = false;
      notifyListeners();
    } catch (e) {
      // Fallback state on decoder failure or missing asset
      hasEnded = true;
      notifyListeners();
    }
  }

  /// Evaluates the playback position against the total duration to handle the EOF state.
  void _videoListener() {
    if (_controller == null || !_controller!.value.isInitialized) return;

    final Duration position = _controller!.value.position;
    final Duration duration = _controller!.value.duration;

    // Prevents redundant state emissions using the hasEnded flag.
    if (!hasEnded && position >= duration && duration > Duration.zero) {
      hasEnded = true;
      notifyListeners();
    }
  }

  Future<void> playFromStart() async {
    if (_controller == null) return;

    hasEnded = false;

    // Bypasses redundant seekTo calls if the video is already at the start, preventing CPU spikes from unnecessary keyframe re-decoding.
    if (_controller!.value.position > Duration.zero) {
      await _controller!.seekTo(Duration.zero);
    }

    await _controller!.play();
    notifyListeners();
  }

  Future<void> stop() async {
    await _controller?.pause();
    notifyListeners();
  }

  /// Destroys the VideoPlayerController instance and flushes buffers to prevent memory leaks.
  Future<void> release() async {
    if (_controller != null) {
      await _controller!.pause();

      // Listeners must be removed before disposing the controller to prevent dangling references.
      _controller!.removeListener(_videoListener);
      await _controller!.dispose();
      _controller = null;
    }

    hasEnded = false;
    notifyListeners();
  }
}
