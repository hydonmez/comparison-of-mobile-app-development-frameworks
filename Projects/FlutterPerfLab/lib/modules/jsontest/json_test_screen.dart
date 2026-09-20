import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:go_router/go_router.dart';

import 'json_test_view_model.dart';

/// A highly optimized presentation layer for the JSON deserialization benchmark.
/// Enforces strict isolation of the UI recomposition from the intensive CPU-bound
/// background operations, ensuring deterministic benchmark telemetry.
class JsonTestScreen extends StatelessWidget {
  const JsonTestScreen({super.key});

  @override
  Widget build(BuildContext context) {
    // The View-Model is scoped directly to this screen's lifecycle.
    // It will be automatically disposed (clearing RAM) when popped.
    return ChangeNotifierProvider(
      create: (_) => JsonTestViewModel(),
      child: const _JsonTestContent(),
    );
  }
}

class _JsonTestContent extends StatefulWidget {
  const _JsonTestContent();

  @override
  State<_JsonTestContent> createState() => _JsonTestContentState();
}

class _JsonTestContentState extends State<_JsonTestContent> {
  @override
  void initState() {
    super.initState();
    // Pre-fetches the dataset from physical storage into active RAM immediately
    // after the initial frame rendering. Guarantees the subsequent benchmark strictly
    // measures CPU/RAM decoding throughput without storage latency overhead.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<JsonTestViewModel>().preloadData();
    });
  }

  @override
  Widget build(BuildContext context) {
    // Captures physical OS back-button events to enforce deterministic routing.
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (bool didPop, dynamic result) {
        if (didPop) return;
        context.go('/');
      },
      child: Scaffold(
        backgroundColor: const Color(0xFFF2F2F7),
        appBar: AppBar(
          title: const Text(
            "JSON Performance",
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
            children: [
              const SizedBox(height: 20),

              // ─── Header Section (Static, never rebuilds) ───────────────────────
              const Column(
                children: [
                  Icon(Icons.data_object, size: 50, color: Colors.purple),
                  SizedBox(height: 10),
                  Text(
                    "10MB+ Data Deserialization",
                    style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600),
                  ),
                ],
              ),

              const SizedBox(height: 25),

              // ─── Status HUD (Localized Rebuilds Only) ──────────────────────────
              Consumer<JsonTestViewModel>(
                builder: (context, vm, _) => Column(
                  children: [
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: Colors.grey.withOpacity(0.1),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      // Utilizing a monospaced technical font mitigates text-layout
                      // calculation overhead and prevents jitter during high-frequency updates.
                      child: Text(
                        vm.status,
                        textAlign: TextAlign.center,
                        style: const TextStyle(
                          fontSize: 14,
                          fontFamily: 'monospace',
                        ),
                      ),
                    ),
                    const SizedBox(height: 15),

                    if (vm.parsedCount > 0)
                      Text(
                        "${vm.parsedCount} GitHub events processed",
                        style: const TextStyle(
                          fontSize: 12,
                          color: Color(0xFF34C759),
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                  ],
                ),
              ),

              const SizedBox(height: 25),

              // ─── Benchmark Execution Trigger ───────────────────────────────────
              // Button disables during execution without altering inner layout or text.
              Consumer<JsonTestViewModel>(
                builder: (context, vm, _) => SizedBox(
                  width: double.infinity,
                  child: ElevatedButton.icon(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.purple,
                      // Disabled container color logic.
                      disabledBackgroundColor: Colors.purple.withOpacity(0.3),
                      disabledForegroundColor: Colors.white.withOpacity(0.7),
                      foregroundColor: Colors.white,
                      padding: const EdgeInsets.all(16),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                    ),
                    // If isTesting is true, onPressed is null, which automatically
                    // triggers Flutter's disabled styling.
                    onPressed: vm.isTesting ? null : () => vm.startBenchmark(),
                    icon: const Icon(Icons.memory, size: 18),
                    label: const Text(
                      "Start JSON Parsing Benchmark",
                      style: TextStyle(fontWeight: FontWeight.bold),
                    ),
                  ),
                ),
              ),

              // ─── Telemetry Export ──────────────────────────────────────────────
              Consumer<JsonTestViewModel>(
                builder: (context, vm, _) {
                  if (!vm.isReportReady) return const SizedBox.shrink();

                  return Padding(
                    padding: const EdgeInsets.only(top: 10.0),
                    child: ElevatedButton.icon(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.blue,
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.all(16),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(10),
                        ),
                      ),
                      onPressed: () => vm.exportResults(),
                      icon: const Icon(Icons.system_update_alt, size: 16),
                      label: const Text(
                        "Export Results (CSV)",
                        style: TextStyle(fontWeight: FontWeight.bold),
                      ),
                    ),
                  );
                },
              ),

              const Spacer(),
            ],
          ),
        ),
      ),
    );
  }
}
