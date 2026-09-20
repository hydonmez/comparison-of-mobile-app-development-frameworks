import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:go_router/go_router.dart';

import 'package:flutterperflab/core/managers/performance_manager.dart';
import 'audio_test_view_model.dart';

/// A dedicated presentation layer for executing high-fidelity audio playback benchmarks.
///
/// 1. The UI strictly isolates high-frequency telemetry (FPS/Thermal) and time-tick rebuilds
/// into dedicated `Consumer` sub-trees. This prevents the computationally heavy Gradient Box
/// and static texts from rebuilding at 60Hz.
/// 2. Exclusively utilizes Flutter's internal `IconData` (Font Glyphs). This bypasses
/// bitmap decoding pipelines, ensuring strict memory efficiency.
class AudioTestScreen extends StatelessWidget {
  const AudioTestScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (_) => AudioTestViewModel(),
      child: const _AudioTestContent(),
    );
  }
}

class _AudioTestContent extends StatefulWidget {
  const _AudioTestContent();

  @override
  State<_AudioTestContent> createState() => _AudioTestContentState();
}

class _AudioTestContentState extends State<_AudioTestContent> {
  @override
  void initState() {
    super.initState();
    // Asynchronously prepares the audio decoder without blocking the first frame render.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<AudioTestViewModel>().loadTestAudio();
    });
  }

  @override
  Widget build(BuildContext context) {
    // Captures physical OS back-button events to enforce a deterministic teardown
    // of the audio engine and guarantee a safe return to the root dashboard.
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (bool didPop, dynamic result) {
        if (didPop) return;

        final vm = context.read<AudioTestViewModel>();
        if (vm.isTesting || vm.isPlaying) {
          vm.stopTest(isFinished: false);
        }

        context.go('/');
      },
      child: Scaffold(
        backgroundColor: const Color(
          0xFFF2F2F7,
        ), // System grouped background color
        appBar: AppBar(
          title: const Text(
            "Audio Performance",
            style: TextStyle(fontWeight: FontWeight.bold),
          ),
          backgroundColor: Colors.white,
          leading: IconButton(
            icon: const Icon(Icons.arrow_back),
            onPressed: () {
              final vm = context.read<AudioTestViewModel>();
              if (vm.isTesting || vm.isPlaying) {
                vm.stopTest(isFinished: false);
              }
              context.go('/');
            },
          ),
        ),
        body: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16.0),
          child: Column(
            children: [
              const SizedBox(height: 30),

              // 1. Media Artwork Placeholder (Static Zone)
              Container(
                width: 250,
                height: 250,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(20),
                  gradient: const LinearGradient(
                    colors: [Color(0xFFFFA500), Color(0xFFFFC0CB)],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: const Color(0xFFFFC0CB).withOpacity(0.5),
                      blurRadius: 15,
                      offset: const Offset(0, 10),
                    ),
                  ],
                ),
                child: const Icon(
                  Icons.play_arrow,
                  size: 100,
                  color: Colors.white,
                ),
              ),

              const SizedBox(height: 25),

              // 2. Title & Error Reporting
              Consumer<AudioTestViewModel>(
                builder: (context, vm, _) {
                  if (vm.errorMessage != null) {
                    return Container(
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: Colors.red,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Text(
                        "⚠️ Error: ${vm.errorMessage}",
                        style: const TextStyle(
                          fontWeight: FontWeight.bold,
                          color: Colors.white,
                        ),
                        textAlign: TextAlign.center,
                      ),
                    );
                  }

                  return Column(
                    children: [
                      const Text(
                        "Flutter Audio Benchmark",
                        style: TextStyle(
                          fontSize: 22,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 8,
                          vertical: 6,
                        ),
                        decoration: BoxDecoration(
                          color: Colors.grey.withOpacity(0.2),
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: const Text(
                          "Engine: just_audio (Optimized)",
                          style: TextStyle(fontSize: 14, color: Colors.black87),
                        ),
                      ),
                    ],
                  );
                },
              ),

              const SizedBox(height: 25),

              // 3. Playback Timeline & Scrubber
              Consumer<AudioTestViewModel>(
                builder: (context, vm, _) {
                  // Prevents UI thread crashes during native decoder hardware initialization phases.
                  final double safeDuration =
                      vm.totalDuration > 0 && !vm.totalDuration.isNaN
                      ? vm.totalDuration
                      : 1.0;
                  final double safePosition = vm.currentTime.clamp(
                    0.0,
                    safeDuration,
                  );

                  return Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 14.0),
                    child: Column(
                      children: [
                        Slider(
                          value: safePosition,
                          min: 0.0,
                          max: safeDuration,
                          activeColor: const Color(0xFFFFA500),
                          inactiveColor: Colors.grey.shade300,
                          onChanged: vm.isAudioLoaded
                              ? (val) => vm.seekAudio(val)
                              : null,
                        ),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              vm.formatTime(safePosition),
                              style: const TextStyle(
                                fontSize: 12,
                                color: Colors.black54,
                                fontFamily: 'monospace',
                              ),
                            ),
                            Text(
                              vm.formatTime(safeDuration),
                              style: const TextStyle(
                                fontSize: 12,
                                color: Colors.black54,
                                fontFamily: 'monospace',
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  );
                },
              ),

              const SizedBox(height: 25),

              // 4. Transport Controls
              Consumer<AudioTestViewModel>(
                builder: (context, vm, _) => Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    IconButton(
                      iconSize: 40,
                      icon: const Icon(Icons.fast_rewind),
                      color: vm.isAudioLoaded
                          ? Theme.of(context).primaryColor
                          : Colors.grey,
                      onPressed: vm.isAudioLoaded ? () => vm.skip(-15.0) : null,
                    ),
                    const SizedBox(width: 20),
                    IconButton(
                      iconSize: 80,
                      icon: Icon(
                        vm.isPlaying
                            ? Icons.pause_circle_filled
                            : Icons.play_circle_fill,
                      ),
                      color: vm.isAudioLoaded
                          ? const Color(0xFFFFA500)
                          : Colors.grey,
                      onPressed: vm.isAudioLoaded
                          ? () {
                              if (vm.isPlaying) {
                                vm.stopTest(isFinished: false);
                              } else {
                                vm.startTest();
                              }
                            }
                          : null,
                    ),
                    const SizedBox(width: 20),
                    IconButton(
                      iconSize: 40,
                      icon: const Icon(Icons.fast_forward),
                      color: vm.isAudioLoaded
                          ? Theme.of(context).primaryColor
                          : Colors.grey,
                      onPressed: vm.isAudioLoaded ? () => vm.skip(15.0) : null,
                    ),
                  ],
                ),
              ),

              const Spacer(),

              // 5. Hardware Telemetry Overlay
              Consumer<AudioTestViewModel>(
                builder: (context, vm, _) =>
                    _TelemetryHUDLayer(isTesting: vm.isTesting),
              ),

              const SizedBox(height: 20),
            ],
          ),
        ),
      ),
    );
  }
}

/// Isolated widget to directly observe singleton telemetry streams.
/// Bypasses main UI allocations for strictly zero-cost updates.
class _TelemetryHUDLayer extends StatelessWidget {
  final bool isTesting;

  const _TelemetryHUDLayer({required this.isTesting});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider.value(
      value: PerformanceManager.shared,
      child: Consumer<PerformanceManager>(
        builder: (context, perfManager, _) => Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(16),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withOpacity(0.05),
                blurRadius: 10,
                offset: const Offset(0, 5),
              ),
            ],
          ),
          child: Row(
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    "FPS: ${perfManager.currentFPS}",
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                      fontFamily: 'monospace',
                      color: perfManager.currentFPS < 50
                          ? Colors.red
                          : Colors.green,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    "Thermal: ${perfManager.thermalStateString}",
                    style: const TextStyle(fontSize: 12, color: Colors.grey),
                  ),
                ],
              ),
              const Spacer(),
              if (isTesting)
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 8,
                    vertical: 6,
                  ),
                  decoration: BoxDecoration(
                    color: Colors.red.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Row(
                    children: [
                      SizedBox(
                        width: 12,
                        height: 12,
                        child: CircularProgressIndicator(
                          color: Colors.red,
                          strokeWidth: 2,
                        ),
                      ),
                      SizedBox(width: 6),
                      Text(
                        "REC",
                        style: TextStyle(
                          fontSize: 10,
                          fontWeight: FontWeight.bold,
                          color: Colors.red,
                        ),
                      ),
                    ],
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
