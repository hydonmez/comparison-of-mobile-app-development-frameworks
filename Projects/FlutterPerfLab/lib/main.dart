import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:go_router/go_router.dart';

// Absolute imports ensuring path stability
import 'package:flutterperflab/core/managers/launch_performance_manager.dart';
import 'package:flutterperflab/application/main_dashboard_screen.dart';

// View layer imports
import 'package:flutterperflab/modules/storagetest/storage_test_screen.dart';
import 'package:flutterperflab/modules/sensortest/sensor_test_screen.dart';
import 'package:flutterperflab/modules/videotest/video_test_screen.dart';
import 'package:flutterperflab/modules/audiotest/audio_test_screen.dart';
import 'package:flutterperflab/modules/jsontest/json_test_screen.dart';
import 'package:flutterperflab/modules/listtest/list_test_screen.dart';
import 'package:flutterperflab/modules/maptest/map_test_screen.dart';

/// The absolute entry point of the Dart Virtual Machine.
/// Executed immediately after the Flutter Engine completes thread spawning.
void main() {
  // Establishes the absolute temporal zero-point for cold start telemetry.
  // Executes in user-space before any declarative widget trees are inflated.
  LaunchPerformanceManager.shared.appStarted();

  // Binds the framework to the Flutter Engine natively.
  WidgetsFlutterBinding.ensureInitialized();

  // Enforces strict portrait rendering to prevent unprompted layout
  // invalidations and CPU spikes during physical device movement.
  SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
    DeviceOrientation.portraitDown,
  ]).then((_) {
    runApp(const BenchmarkApplication());
  });
}

/// The root declarative orchestrator.
/// Implements WidgetsBindingObserver to natively hook into the OS-level process lifecycle.
class BenchmarkApplication extends StatefulWidget {
  const BenchmarkApplication({super.key});

  @override
  State<BenchmarkApplication> createState() => _BenchmarkApplicationState();
}

class _BenchmarkApplicationState extends State<BenchmarkApplication>
    with WidgetsBindingObserver {
  // MARK: - Deterministic Routing Graph

  final GoRouter _router = GoRouter(
    initialLocation: '/',
    routes: [
      GoRoute(
        path: '/',
        builder: (context, state) => const MainDashboardScreen(),
      ),
      GoRoute(
        path: '/storage_test',
        builder: (context, state) => const StorageTestScreen(),
      ),
      GoRoute(
        path: '/sensor_test',
        builder: (context, state) => const SensorTestScreen(),
      ),
      GoRoute(
        path: '/video_test',
        builder: (context, state) => const VideoTestScreen(),
      ),
      GoRoute(
        path: '/audio_test',
        builder: (context, state) => const AudioTestScreen(),
      ),
      GoRoute(
        path: '/json_test',
        builder: (context, state) => const JsonTestScreen(),
      ),
      GoRoute(
        path: '/list_test',
        builder: (context, state) => const ListTestScreen(),
      ),
      GoRoute(
        path: '/map_test',
        builder: (context, state) => const MapTestScreen(),
      ),
    ],
  );

  @override
  void initState() {
    super.initState();

    // Hooks into the global process lifecycle to measure hot start transitions.
    WidgetsBinding.instance.addObserver(this);

    // The structural widget tree is allocated in RAM prior to the GPU render pass.
    LaunchPerformanceManager.shared.osReady();

    // Executes strictly after the initial composition and layout passes are successfully
    // committed to the rendering engine to resolve Time-To-Interactive (TTI).
    WidgetsBinding.instance.addPostFrameCallback((_) {
      LaunchPerformanceManager.shared.reportBenchmark();
    });
  }

  @override
  void dispose() {
    // Deregisters the process lifecycle observer to prevent memory leaks and
    // accumulation across potential engine restarts.
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Monitors OS-level process transitions to natively calculate hot start latency.
    switch (state) {
      case AppLifecycleState.inactive:
      case AppLifecycleState.hidden:
        // Hot Start Initiation: The OS begins restoring the suspended process.
        LaunchPerformanceManager.shared.appIsWakingUp();
        break;
      case AppLifecycleState.resumed:
        // Hot Start Resolution: The application context is fully restored and interactive.
        LaunchPerformanceManager.shared.hotStartDetected();
        break;
      default:
        break;
    }
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      title: 'Benchmark Lab',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: Colors.blue,
          background: const Color(0xFFF2F2F7),
        ),
        useMaterial3: true,
      ),
      routerConfig: _router,
    );
  }
}
