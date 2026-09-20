import 'dart:async';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:go_router/go_router.dart';
import 'package:video_player/video_player.dart';

import 'package:flutterperflab/core/managers/performance_manager.dart';
import 'video_test_view_model.dart';

/// A presentation layer for executing high-fidelity video playback benchmarks.
class VideoTestScreen extends StatelessWidget {
  const VideoTestScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (_) => VideoTestViewModel(),
      child: const _VideoTestRouter(),
    );
  }
}

class _VideoTestRouter extends StatefulWidget {
  const _VideoTestRouter();

  @override
  State<_VideoTestRouter> createState() => _VideoTestRouterState();
}

class _VideoTestRouterState extends State<_VideoTestRouter> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<VideoTestViewModel>().prepareVideo();
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<VideoTestViewModel>().forceRotation(isLandscape: false);
    });
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Consumer<VideoTestViewModel>(
      builder: (context, vm, _) {
        return PopScope(
          canPop: false,
          onPopInvokedWithResult: (bool didPop, dynamic result) {
            if (didPop) return;
            if (vm.isFullScreen) {
              vm.stopTest(isFinished: false);
            } else {
              context.go('/');
            }
          },
          child: vm.isFullScreen
              ? _ActiveVideoBenchmarkOverlay(viewModel: vm)
              : _IdlePreparationUI(viewModel: vm),
        );
      },
    );
  }
}

/// An isolated rendering surface strictly responsible for full-screen video playback and telemetry HUD.
class _ActiveVideoBenchmarkOverlay extends StatefulWidget {
  final VideoTestViewModel viewModel;

  const _ActiveVideoBenchmarkOverlay({required this.viewModel});

  @override
  State<_ActiveVideoBenchmarkOverlay> createState() =>
      _ActiveVideoBenchmarkOverlayState();
}

class _ActiveVideoBenchmarkOverlayState
    extends State<_ActiveVideoBenchmarkOverlay> {
  @override
  void initState() {
    super.initState();
    widget.viewModel.forceRotation(isLandscape: true);
  }

  @override
  void dispose() {
    widget.viewModel.forceRotation(isLandscape: false);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final controller = widget.viewModel.engine.controller;

    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        fit: StackFit.expand,
        children: [
          // 1. Hardware Video Texture
          if (controller != null && controller.value.isInitialized)
            Center(
              child: AspectRatio(
                aspectRatio: controller.value.aspectRatio,
                child: VideoPlayer(controller),
              ),
            ),

          // 2. Optimized Controls overlay
          if (controller != null && controller.value.isInitialized)
            _OptimizedNativeControls(
              controller: controller,
              viewModel: widget.viewModel,
            ),

          // 3. Control Overlay: Manual termination trigger
          Positioned(
            top: 50,
            left: 20,
            child: ElevatedButton(
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.red.withOpacity(0.85),
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 12,
                ),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(8),
                ),
              ),
              onPressed: () => widget.viewModel.stopTest(isFinished: false),
              child: const Text(
                "Terminate Benchmark",
                style: TextStyle(fontSize: 12),
              ),
            ),
          ),

          // 4. Telemetry HUD
          const Positioned(top: 50, right: 20, child: _FPSIndicator()),
        ],
      ),
    );
  }
}

/// Hardware UI orchestrator that mimics the default native media player layout.
class _OptimizedNativeControls extends StatefulWidget {
  final VideoPlayerController controller;
  final VideoTestViewModel viewModel;

  const _OptimizedNativeControls({
    required this.controller,
    required this.viewModel,
  });

  @override
  State<_OptimizedNativeControls> createState() =>
      _OptimizedNativeControlsState();
}

class _OptimizedNativeControlsState extends State<_OptimizedNativeControls> {
  bool _isVisible = false;
  Timer? _hideTimer;

  void _toggleVisibility() {
    setState(() => _isVisible = !_isVisible);
    _hideTimer?.cancel();
    if (_isVisible) {
      _hideTimer = Timer(const Duration(seconds: 3), () {
        if (mounted) setState(() => _isVisible = false);
      });
    }
  }

  void _skip(int seconds) {
    final targetPos =
        widget.controller.value.position + Duration(seconds: seconds);
    widget.controller.seekTo(targetPos);
    _toggleVisibility();
  }

  @override
  void dispose() {
    _hideTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.translucent,
      onTap: _toggleVisibility,
      child: AnimatedOpacity(
        opacity: _isVisible ? 1.0 : 0.0,
        duration: const Duration(milliseconds: 250),
        child: Container(
          color: Colors.black45, // Dimming mask
          child: Stack(
            children: [
              // Central Transport Controls (Play/Pause, Forward, Rewind)
              Center(
                child: IgnorePointer(
                  ignoring: !_isVisible,
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      IconButton(
                        iconSize: 48,
                        color: Colors.white,
                        icon: const Icon(Icons.fast_rewind),
                        onPressed: () => _skip(-10),
                      ),
                      const SizedBox(width: 40),
                      _MicroPlayPauseButton(
                        controller: widget.controller,
                        onInteraction: _toggleVisibility,
                      ),
                      const SizedBox(width: 40),
                      IconButton(
                        iconSize: 48,
                        color: Colors.white,
                        icon: const Icon(Icons.fast_forward),
                        onPressed: () => _skip(10),
                      ),
                    ],
                  ),
                ),
              ),

              // Bottom Layout: Time, Scrubber, and Fullscreen toggle
              Positioned(
                bottom: 16,
                left: 24,
                right: 24,
                child: IgnorePointer(
                  ignoring: !_isVisible,
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.center,
                    children: [
                      Expanded(
                        child: _TimeAndProgressBar(
                          controller: widget.controller,
                        ),
                      ),
                      const SizedBox(width: 16),
                      IconButton(
                        icon: const Icon(
                          Icons.fullscreen_exit,
                          color: Colors.white,
                          size: 28,
                        ),
                        onPressed: () =>
                            widget.viewModel.stopTest(isFinished: false),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _MicroPlayPauseButton extends StatefulWidget {
  final VideoPlayerController controller;
  final VoidCallback onInteraction;

  const _MicroPlayPauseButton({
    required this.controller,
    required this.onInteraction,
  });

  @override
  State<_MicroPlayPauseButton> createState() => _MicroPlayPauseButtonState();
}

class _MicroPlayPauseButtonState extends State<_MicroPlayPauseButton> {
  late bool _isPlaying;
  late bool _hasEnded;

  @override
  void initState() {
    super.initState();
    _evaluateState();
    widget.controller.addListener(_stateListener);
  }

  void _evaluateState() {
    final value = widget.controller.value;
    _isPlaying = value.isPlaying;
    _hasEnded =
        value.position >= value.duration && value.duration > Duration.zero;
  }

  void _stateListener() {
    final oldPlaying = _isPlaying;
    final oldEnded = _hasEnded;
    _evaluateState();

    if (oldPlaying != _isPlaying || oldEnded != _hasEnded) {
      setState(() {});
    }
  }

  @override
  void dispose() {
    widget.controller.removeListener(_stateListener);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    IconData iconData = Icons.play_arrow;
    if (_hasEnded) {
      iconData = Icons.replay;
    } else if (_isPlaying) {
      iconData = Icons.pause;
    }

    return IconButton(
      iconSize: 72,
      color: Colors.white,
      icon: Icon(iconData),
      onPressed: () {
        if (_hasEnded) {
          widget.controller.seekTo(Duration.zero);
          widget.controller.play();
        } else {
          _isPlaying ? widget.controller.pause() : widget.controller.play();
        }
        widget.onInteraction();
      },
    );
  }
}

class _TimeAndProgressBar extends StatefulWidget {
  final VideoPlayerController controller;

  const _TimeAndProgressBar({required this.controller});

  @override
  State<_TimeAndProgressBar> createState() => _TimeAndProgressBarState();
}

class _TimeAndProgressBarState extends State<_TimeAndProgressBar> {
  String _position = "00:00";
  String _duration = "00:00";

  @override
  void initState() {
    super.initState();
    _duration = _formatDuration(widget.controller.value.duration);
    widget.controller.addListener(_timeListener);
  }

  void _timeListener() {
    final newPosition = _formatDuration(widget.controller.value.position);
    if (newPosition != _position) {
      setState(() => _position = newPosition);
    }
  }

  String _formatDuration(Duration duration) {
    String twoDigits(int n) => n.toString().padLeft(2, "0");
    String twoDigitMinutes = twoDigits(duration.inMinutes.remainder(60));
    String twoDigitSeconds = twoDigits(duration.inSeconds.remainder(60));
    if (duration.inHours > 0) {
      return "${twoDigits(duration.inHours)}:$twoDigitMinutes:$twoDigitSeconds";
    }
    return "$twoDigitMinutes:$twoDigitSeconds";
  }

  @override
  void dispose() {
    widget.controller.removeListener(_timeListener);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return RepaintBoundary(
      child: Row(
        children: [
          Text(
            _position,
            style: const TextStyle(color: Colors.white, fontSize: 14),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: VideoProgressIndicator(
              widget.controller,
              allowScrubbing: true,
              padding: const EdgeInsets.symmetric(vertical: 24),
              colors: const VideoProgressColors(
                playedColor: Colors.red,
                bufferedColor: Colors.white54,
                backgroundColor: Colors.white24,
              ),
            ),
          ),
          const SizedBox(width: 16),
          Text(
            _duration,
            style: const TextStyle(color: Colors.white, fontSize: 14),
          ),
        ],
      ),
    );
  }
}

class _FPSIndicator extends StatelessWidget {
  const _FPSIndicator();

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider.value(
      value: PerformanceManager.shared,
      child: Consumer<PerformanceManager>(
        builder: (context, perfManager, _) => Container(
          padding: const EdgeInsets.all(8),
          decoration: BoxDecoration(
            color: Colors.black.withOpacity(0.6),
            borderRadius: BorderRadius.circular(8),
          ),
          child: Text(
            "FPS: ${perfManager.currentFPS}",
            style: TextStyle(
              fontSize: 14,
              fontFamily: 'monospace',
              fontWeight: FontWeight.bold,
              color: perfManager.currentFPS < 50 ? Colors.red : Colors.green,
            ),
          ),
        ),
      ),
    );
  }
}

class _IdlePreparationUI extends StatelessWidget {
  final VideoTestViewModel viewModel;

  const _IdlePreparationUI({required this.viewModel});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF2F2F7),
      appBar: AppBar(
        title: const Text(
          "Video Performance",
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        backgroundColor: Colors.white,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => context.go('/'),
        ),
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.play_arrow, size: 100, color: Colors.blue),
            const SizedBox(height: 20),

            const Text(
              "Full Screen Video Benchmark",
              style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 10),

            const Text(
              "The video plays from start to finish while hardware telemetry is recorded. The screen will lock to landscape orientation for high-fidelity decoding.",
              textAlign: TextAlign.center,
              style: TextStyle(color: Colors.grey),
            ),

            const SizedBox(height: 40),

            if (viewModel.isReportReady)
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: Colors.grey.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Column(
                  children: [
                    const Text(
                      "Benchmark Report Ready",
                      style: TextStyle(fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 10),
                    ElevatedButton.icon(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.green,
                        foregroundColor: Colors.white,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(10),
                        ),
                      ),
                      onPressed: () => viewModel.shareResults(),
                      icon: const Icon(Icons.system_update_alt, size: 16),
                      label: const Text(
                        "Export Results (CSV)",
                        style: TextStyle(fontWeight: FontWeight.bold),
                      ),
                    ),
                  ],
                ),
              ),

            const SizedBox(height: 40),

            SizedBox(
              width: double.infinity,
              height: 56,
              child: ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.blue,
                  foregroundColor: Colors.white,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                ),
                onPressed: () => viewModel.startTest(),
                child: const Text(
                  "START FULL SCREEN BENCHMARK",
                  style: TextStyle(fontWeight: FontWeight.bold),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
