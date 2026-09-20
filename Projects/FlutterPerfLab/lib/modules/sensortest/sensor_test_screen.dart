import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:go_router/go_router.dart';

import 'sensor_test_view_model.dart';

/// Orchestrates the visualization of high-frequency (100Hz) sensor telemetry.
///
/// Utilizes precise Selector boundaries to create a rendering firewall. When the
/// underlying hardware emits new data, only the specific numeric leaf nodes redraw.
/// Static elements remain untouched in the rendering pipeline, preserving CPU cycles
/// and matching scoped recomposition behaviors.
class SensorTestScreen extends StatelessWidget {
  const SensorTestScreen({super.key});

  @override
  Widget build(BuildContext context) {
    // Injects the ViewModel at the root level without observing it directly.
    return ChangeNotifierProvider(
      create: (_) => SensorTestViewModel(),
      child: const _SensorTestContent(),
    );
  }
}

class _SensorTestContent extends StatelessWidget {
  const _SensorTestContent();

  @override
  Widget build(BuildContext context) {
    // Captures physical OS back-button events to enforce a deterministic teardown
    // of the sensor engine and safely return to the root dashboard.
    return PopScope(
      canPop: false, // Prevents the OS from force-closing the activity.
      onPopInvokedWithResult: (bool didPop, dynamic result) {
        if (didPop) return;

        final vm = context.read<SensorTestViewModel>();
        if (vm.isRunning) {
          vm.stopTest();
        }

        // Explicit declarative routing to the root dashboard.
        context.go('/');
      },
      child: Scaffold(
        appBar: AppBar(
          title: const Text(
            "Sensor Performance",
            style: TextStyle(fontWeight: FontWeight.bold),
          ),
          backgroundColor: Colors.white,
          leading: IconButton(
            icon: const Icon(Icons.arrow_back),
            onPressed: () {
              final vm = context.read<SensorTestViewModel>();
              if (vm.isRunning) vm.stopTest();
              context.go('/');
            },
          ),
        ),
        body: SingleChildScrollView(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            children: [
              // --- Status Hub (Isolated Recomposition) ---
              Column(
                children: [
                  // Text rebuilds ONLY when the 'status' string changes.
                  Selector<SensorTestViewModel, String>(
                    selector: (_, vm) => vm.status,
                    builder: (_, status, __) => Text(
                      status,
                      style: TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                        color: status.contains("Acquiring")
                            ? Colors.orange
                            : Colors.grey,
                      ),
                    ),
                  ),
                  const SizedBox(height: 15),

                  // Progress UI rebuilds ONLY when the 'progress' double changes.
                  Selector<SensorTestViewModel, double>(
                    selector: (_, vm) => vm.progress,
                    builder: (_, progress, __) {
                      // Hide progress bar if the benchmark is not active.
                      if (progress == 0.0 &&
                          !context.read<SensorTestViewModel>().isRunning) {
                        return const SizedBox.shrink();
                      }
                      return Column(
                        children: [
                          LinearProgressIndicator(
                            value: progress,
                            color: Colors.orange,
                            minHeight: 8,
                          ),
                          const SizedBox(height: 8),
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              const Text(
                                "Benchmark Progress:",
                                style: TextStyle(
                                  fontSize: 12,
                                  color: Colors.grey,
                                ),
                              ),
                              Text(
                                "${(progress * 100).toInt()}%",
                                style: const TextStyle(
                                  fontSize: 12,
                                  fontWeight: FontWeight.bold,
                                  color: Colors.orange,
                                ),
                              ),
                            ],
                          ),
                        ],
                      );
                    },
                  ),
                  const SizedBox(height: 25),
                ],
              ),

              // --- Sensor Data Visualization ---
              const _AccelerometerCard(),
              const SizedBox(height: 15),
              const _GyroscopeCard(),
              const SizedBox(height: 15),
              const _MagnetometerCard(),
              const SizedBox(height: 15),
              const _PedometerCard(),

              const SizedBox(height: 25),

              // --- Control Panel ---
              // Utilizes Dart 3 Records to observe only explicit boolean state changes,
              // preventing button redraws during high-frequency sensor ticks.
              Selector<
                SensorTestViewModel,
                ({bool isRunning, bool isReportReady})
              >(
                selector: (_, vm) =>
                    (isRunning: vm.isRunning, isReportReady: vm.isReportReady),
                builder: (context, state, child) => Column(
                  children: [
                    SizedBox(
                      width: double.infinity,
                      height: 56,
                      child: ElevatedButton.icon(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: state.isRunning
                              ? Colors.red
                              : Colors.orange,
                          foregroundColor: Colors.white,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(12),
                          ),
                        ),
                        onPressed: () {
                          final vm = context.read<SensorTestViewModel>();
                          state.isRunning ? vm.stopTest() : vm.startTest();
                        },
                        icon: Icon(
                          state.isRunning ? Icons.stop : Icons.play_arrow,
                        ),
                        label: Text(
                          state.isRunning
                              ? "Stop Benchmark"
                              : "Start Sensor Test",
                          style: const TextStyle(fontWeight: FontWeight.bold),
                        ),
                      ),
                    ),
                    const SizedBox(height: 15),
                    if (state.isReportReady && !state.isRunning)
                      SizedBox(
                        width: double.infinity,
                        height: 56,
                        child: ElevatedButton.icon(
                          style: ElevatedButton.styleFrom(
                            backgroundColor: Colors.green,
                            foregroundColor: Colors.white,
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(12),
                            ),
                          ),
                          onPressed: () => context
                              .read<SensorTestViewModel>()
                              .exportResults(),
                          icon: const Icon(Icons.system_update_alt),
                          label: const Text(
                            "Export Results (CSV)",
                            style: TextStyle(fontWeight: FontWeight.bold),
                          ),
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

// MARK: - Isolated Sensor Components

class _AccelerometerCard extends StatelessWidget {
  const _AccelerometerCard();
  @override
  Widget build(BuildContext context) {
    // Listens exclusively to accelerometer data changes.
    return Selector<SensorTestViewModel, List<double>>(
      selector: (_, vm) => vm.accelData,
      builder: (_, data, __) => _SensorInfoBox(
        icon: Icons.open_in_full,
        title: "Accelerometer (G-Force)",
        x: data[0],
        y: data[1],
        z: data[2],
        color: Colors.blue,
      ),
    );
  }
}

class _GyroscopeCard extends StatelessWidget {
  const _GyroscopeCard();
  @override
  Widget build(BuildContext context) {
    return Selector<SensorTestViewModel, List<double>>(
      selector: (_, vm) => vm.gyroData,
      builder: (_, data, __) => _SensorInfoBox(
        icon: Icons.sync,
        title: "Gyroscope (Rad/s)",
        x: data[0],
        y: data[1],
        z: data[2],
        color: Colors.green,
      ),
    );
  }
}

class _MagnetometerCard extends StatelessWidget {
  const _MagnetometerCard();
  @override
  Widget build(BuildContext context) {
    return Selector<SensorTestViewModel, List<double>>(
      selector: (_, vm) => vm.magnetData,
      builder: (_, data, __) => _SensorInfoBox(
        icon: Icons.explore,
        title: "Magnetometer (µT)",
        x: data[0],
        y: data[1],
        z: data[2],
        color: Colors.red,
      ),
    );
  }
}

class _PedometerCard extends StatelessWidget {
  const _PedometerCard();
  @override
  Widget build(BuildContext context) {
    return Selector<SensorTestViewModel, int>(
      selector: (_, vm) => vm.stepCount,
      builder: (_, stepCount, __) => Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: const Color(0xFFF2F2F7),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Row(
          children: [
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  "Pedometer (Steps)",
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.bold,
                    color: Colors.deepPurple,
                  ),
                ),
                Text(
                  "$stepCount",
                  style: const TextStyle(
                    fontSize: 22,
                    fontWeight: FontWeight.bold,
                    fontFamily: 'monospace',
                  ),
                ),
              ],
            ),
            const Spacer(),
            const Icon(
              Icons.directions_walk,
              color: Colors.deepPurple,
              size: 32,
            ),
          ],
        ),
      ),
    );
  }
}

// MARK: - UI Primitives

class _SensorInfoBox extends StatelessWidget {
  final IconData icon;
  final String title;
  final double x, y, z;
  final Color color;

  const _SensorInfoBox({
    required this.icon,
    required this.title,
    required this.x,
    required this.y,
    required this.z,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFFF2F2F7),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        children: [
          Row(
            children: [
              Icon(icon, color: color, size: 20),
              const SizedBox(width: 8),
              Text(
                title,
                style: const TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: _ValueText(label: "X", value: x),
              ),
              Expanded(
                child: _ValueText(label: "Y", value: y),
              ),
              Expanded(
                child: _ValueText(label: "Z", value: z),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _ValueText extends StatelessWidget {
  final String label;
  final double value;

  const _ValueText({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Text(label, style: const TextStyle(fontSize: 10, color: Colors.grey)),
        Text(
          // toStringAsFixed(3) is highly optimized in the Dart VM, minimizing memory overhead.
          value.toStringAsFixed(3),
          style: const TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.bold,
            fontFamily: 'monospace',
          ),
        ),
      ],
    );
  }
}
