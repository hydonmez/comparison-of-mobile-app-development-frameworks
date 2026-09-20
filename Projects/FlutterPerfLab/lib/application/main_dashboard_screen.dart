import 'dart:io';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';
import 'package:permission_handler/permission_handler.dart';

import 'package:flutterperflab/core/managers/launch_performance_manager.dart';

/// The primary telemetry dashboard and navigation hub for the benchmarking suite.
/// High-frequency state reads are encapsulated within the _LaunchTelemetryDashboard node to prevent UI thread jitter.
class MainDashboardScreen extends StatefulWidget {
  const MainDashboardScreen({super.key});

  @override
  State<MainDashboardScreen> createState() => _MainDashboardScreenState();
}

class _MainDashboardScreenState extends State<MainDashboardScreen> {
  @override
  void initState() {
    super.initState();

    // Permission requests are deferred until after the first frame render.
    // This ensures the cold start telemetry is not interrupted by system dialogs.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _requestGlobalHardwarePermissions();
    });
  }

  /// Requests system-level hardware permissions globally.
  /// Decouples permission requests from benchmarking modules to prevent context-switching overhead.
  Future<void> _requestGlobalHardwarePermissions() async {
    try {
      if (Platform.isAndroid) {
        // Activity Recognition permission for the Pedometer on Android.
        final status = await Permission.activityRecognition.request();
        if (status.isDenied) {
          debugPrint("🛑 Android Activity Recognition Denied");
        }
      } else if (Platform.isIOS) {
        // CoreMotion sensors permission for iOS.
        final status = await Permission.sensors.request();
        if (status.isDenied) {
          debugPrint("🛑 iOS Sensors Permission Denied");
        }
      }
    } catch (e) {
      debugPrint("🛑 Global Permission Handshake Failed: $e");
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF2F2F7),
      appBar: AppBar(
        title: const Text(
          "Benchmark Lab (FLUTTER)",
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        backgroundColor: Colors.white,
        surfaceTintColor: Colors.transparent,
      ),
      // Uses SingleChildScrollView instead of ListView.builder to eliminate lazy-loading overhead during benchmarking.
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Injects the singleton into the widget tree for isolated consumption.
            ChangeNotifierProvider.value(
              value: LaunchPerformanceManager.shared,
              child: const _LaunchTelemetryDashboard(),
            ),
            const SizedBox(height: 20),
            const _TestSuiteRows(),
          ],
        ),
      ),
    );
  }
}

/// Subscribes to LaunchPerformanceManager via Consumer.
/// This isolates state updates to prevent full-screen re-renders during high-frequency latency changes.
class _LaunchTelemetryDashboard extends StatelessWidget {
  const _LaunchTelemetryDashboard();

  @override
  Widget build(BuildContext context) {
    return Consumer<LaunchPerformanceManager>(
      builder: (context, tracker, child) {
        final bool hasHotStart = tracker.hotStartMs > 0;

        return Column(
          children: [
            Row(
              // ignore: prefer_const_constructors
              children: const [
                Icon(Icons.speed, color: Color(0xFF5E5CE6)),
                SizedBox(width: 8),
                Text(
                  "System Latency Distribution",
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                ),
              ],
            ),
            const SizedBox(height: 12),

            // Cold Start Telemetry Component
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: const Color(0xFF32ADE6).withOpacity(0.1),
                borderRadius: BorderRadius.circular(14),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    // ignore: prefer_const_constructors
                    children: const [
                      Icon(
                        Icons.power_settings_new,
                        color: Color(0xFF32ADE6),
                        size: 14,
                      ),
                      SizedBox(width: 4),
                      Text(
                        "Cold Start",
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.bold,
                          color: Colors.grey,
                        ),
                      ),
                    ],
                  ),
                  const Text(
                    "Post-Engine Process Initialization",
                    style: TextStyle(fontSize: 10, color: Colors.grey),
                  ),
                  const SizedBox(height: 8),

                  Text(
                    "${tracker.formattedColdStart} ms",
                    style: const TextStyle(
                      fontSize: 22,
                      fontWeight: FontWeight.w900,
                      fontFamily: 'monospace',
                    ),
                  ),

                  const SizedBox(height: 8),
                  Row(
                    children: [
                      Container(
                        width: 6,
                        height: 6,
                        decoration: const BoxDecoration(
                          color: Color(0xFF5E5CE6),
                          shape: BoxShape.circle,
                        ),
                      ),
                      const SizedBox(width: 4),
                      Text(
                        "OS Overhead: ${tracker.formattedOSDuration} ms",
                        style: const TextStyle(
                          fontSize: 11,
                          color: Colors.grey,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Row(
                    children: [
                      Container(
                        width: 6,
                        height: 6,
                        decoration: const BoxDecoration(
                          color: Color(0xFFFF2D55),
                          shape: BoxShape.circle,
                        ),
                      ),
                      const SizedBox(width: 4),
                      Text(
                        "UI Rendering: ${tracker.formattedUIDuration} ms",
                        style: const TextStyle(
                          fontSize: 11,
                          color: Colors.grey,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),

            const SizedBox(height: 12),

            // Hot Start Telemetry Component
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: const Color(0xFFFF9500).withOpacity(0.1),
                borderRadius: BorderRadius.circular(14),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    // ignore: prefer_const_constructors
                    children: const [
                      Icon(Icons.restore, color: Color(0xFFFF9500), size: 14),
                      SizedBox(width: 4),
                      Text(
                        "Hot Start",
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.bold,
                          color: Colors.grey,
                        ),
                      ),
                    ],
                  ),
                  const Text(
                    "State Restoration from RAM",
                    style: TextStyle(fontSize: 10, color: Colors.grey),
                  ),
                  const SizedBox(height: 8),

                  Text(
                    hasHotStart ? "${tracker.formattedHotStart} ms" : "-- ms",
                    style: TextStyle(
                      fontSize: 22,
                      fontWeight: FontWeight.w900,
                      fontFamily: 'monospace',
                      color: hasHotStart
                          ? Colors.black87
                          : Colors.grey.withOpacity(0.4),
                    ),
                  ),

                  const SizedBox(height: 8),
                  Row(
                    children: [
                      Icon(
                        hasHotStart ? Icons.memory : Icons.hourglass_empty,
                        color: hasHotStart
                            ? const Color(0xFF34C759)
                            : Colors.grey,
                        size: 14,
                      ),
                      const SizedBox(width: 4),
                      Text(
                        hasHotStart
                            ? "Verified: Cached Process"
                            : "Awaiting Foreground Transition...",
                        style: TextStyle(
                          fontSize: 11,
                          fontWeight: hasHotStart
                              ? FontWeight.bold
                              : FontWeight.w500,
                          color: hasHotStart
                              ? const Color(0xFF34C759)
                              : Colors.grey,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        );
      },
    );
  }
}

/// Stateless navigation hub.
/// Ensures the list is never re-evaluated after the initial layout, preserving CPU cycles.
class _TestSuiteRows extends StatelessWidget {
  const _TestSuiteRows();

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Padding(
          padding: EdgeInsets.only(bottom: 4.0, top: 10.0),
          child: Text(
            "Active Benchmarking Suites",
            style: TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.bold,
              color: Colors.grey,
            ),
          ),
        ),
        const SizedBox(height: 10),

        _TestRowView(
          title: "Video Playback (1080p/60fps)",
          icon: Icons.ondemand_video,
          color: const Color(0xFFFF3B30),
          onTap: () {
            context.go('/video_test');
          },
        ),
        const SizedBox(height: 10),
        _TestRowView(
          title: "Audio Playback (LPCM/FLAC)",
          icon: Icons.graphic_eq,
          color: const Color(0xFFFF2D55),
          onTap: () {
            context.go('/audio_test');
          },
        ),
        const SizedBox(height: 10),
        _TestRowView(
          title: "I/O Storage Throughput",
          icon: Icons.storage,
          color: Colors.grey,
          onTap: () => context.go('/storage_test'),
        ),
        const SizedBox(height: 10),
        _TestRowView(
          title: "JSON Deserialization (10MB+)",
          icon: Icons.data_object,
          color: const Color(0xFF00B0FF),
          onTap: () {
            context.go('/json_test');
          },
        ),
        const SizedBox(height: 10),
        _TestRowView(
          title: "Map Surface Rendering",
          icon: Icons.map,
          color: const Color(0xFF34C759),
          onTap: () {
            context.go('/map_test');
          },
        ),
        const SizedBox(height: 10),
        _TestRowView(
          title: "Virtual List Performance",
          icon: Icons.shopping_cart,
          color: const Color(0xFF007AFF),
          onTap: () {
            context.go('/list_test');
          },
        ),
        const SizedBox(height: 10),
        _TestRowView(
          title: "Hardware Sensor Fusion",
          icon: Icons.sensors,
          color: const Color(0xFFFF9500),
          onTap: () => context.go('/sensor_test'),
        ),
      ],
    );
  }
}

/// A lightweight, stateless UI component for navigation rows.
class _TestRowView extends StatelessWidget {
  final String title;
  final IconData icon;
  final Color color;
  final VoidCallback onTap;

  const _TestRowView({
    required this.title,
    required this.icon,
    required this.color,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Row(
          children: [
            Container(
              width: 32,
              height: 32,
              decoration: BoxDecoration(
                color: color,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Icon(icon, color: Colors.white, size: 18),
            ),
            const SizedBox(width: 14),
            Text(
              title,
              style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold),
            ),
            const Spacer(),
            const Icon(Icons.chevron_right, color: Colors.black26),
          ],
        ),
      ),
    );
  }
}
