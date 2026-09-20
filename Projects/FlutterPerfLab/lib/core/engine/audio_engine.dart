import 'dart:async';
import 'dart:math' as math;
import 'package:flutter/foundation.dart';
import 'package:just_audio/just_audio.dart';

/// A Main-Isolate-bound audio playback engine using just_audio.
/// The player instance is completely disposed and re-allocated on every load to flush native audio buffers and prevent RAM bloat.
class AudioEngine extends ChangeNotifier {
  // Singleton pattern to enforce single-instance allocation.
  static final AudioEngine shared = AudioEngine._internal();

  // Nullable to allow explicit garbage collection.
  AudioPlayer? _player;

  // State Synchronization
  StreamSubscription<Duration>? _positionSub;
  StreamSubscription<PlayerState>? _stateSub;
  StreamSubscription<Duration?>? _durationSub;

  double currentTime = 0.0;
  double totalDuration = 0.0;
  bool isPlaying = false;

  // Tracks the physical End-Of-File state from the native decoder.
  bool hasEnded = false;

  AudioEngine._internal();

  Future<bool> loadAudio(String urlPath) async {
    // Purges observers, playback, and native references.
    release();

    try {
      // Instantiating a new player guarantees a clean buffer state in the native heap.
      _player = AudioPlayer();

      final duration = await _player!.setAsset(urlPath);
      if (duration != null) {
        totalDuration = duration.inMilliseconds / 1000.0;
      }

      _setupObservers();
      return true;
    } catch (e) {
      return false;
    }
  }

  void play() {
    if (_player == null) return;

    // EOF Guard: Resets the playhead if playback is triggered at the end of the track.
    if (hasEnded || (totalDuration > 0 && currentTime >= totalDuration - 0.1)) {
      seek(0.0);
    }
    _player!.play();
  }

  void pause() {
    _player?.pause();
  }

  void seek(double seconds) {
    if (_player == null) return;

    final duration = Duration(milliseconds: (seconds * 1000).toInt());
    _player!.seek(duration);
    currentTime = seconds;
    hasEnded = false; // Reset EOF state manually upon user intervention
    notifyListeners();
  }

  void skip(double seconds) {
    final double newTime = math.min(
      math.max(currentTime + seconds, 0.0),
      totalDuration,
    );
    seek(newTime);
  }

  void _setupObservers() {
    if (_player == null) return;

    _stateSub = _player!.playerStateStream.listen((state) {
      final bool playing = state.playing;
      final ProcessingState processingState = state.processingState;

      if (processingState == ProcessingState.completed) {
        isPlaying = false;
        hasEnded = true;
        currentTime = totalDuration; // Snap accurately to the physical EOF
      } else {
        isPlaying = playing;
        hasEnded = false;
      }
      notifyListeners();
    });

    // Listens to position updates.
    _positionSub = _player!.positionStream.listen((pos) {
      currentTime = pos.inMilliseconds / 1000.0;
      notifyListeners();
    });

    _durationSub = _player!.durationStream.listen((dur) {
      if (dur != null) {
        totalDuration = dur.inMilliseconds / 1000.0;
        notifyListeners();
      }
    });
  }

  /// Explicitly tears down subscriptions and destroys the native player instance to prevent memory leaks.
  void release() {
    pause();

    _positionSub?.cancel();
    _stateSub?.cancel();
    _durationSub?.cancel();

    _positionSub = null;
    _stateSub = null;
    _durationSub = null;

    // Destroys the native instance to invoke destructors and free up RAM.
    _player?.dispose();
    _player = null;

    currentTime = 0.0;
    totalDuration = 0.0;
    isPlaying = false;
    hasEnded = false;
    notifyListeners();
  }
}
