import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:go_router/go_router.dart';

import 'storage_test_view_model.dart';

/// A dedicated Flutter UI layer for orchestrating heavy I/O storage benchmarks.
class StorageTestScreen extends StatelessWidget {
  const StorageTestScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (_) => StorageTestViewModel(),
      child: const _StorageTestContent(),
    );
  }
}

class _StorageTestContent extends StatelessWidget {
  const _StorageTestContent();

  @override
  Widget build(BuildContext context) {
    // Enforces deterministic teardown of heavy I/O operations and guarantees
    // a safe return to the root dashboard, preventing memory leaks.
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (bool didPop, dynamic result) {
        if (didPop) return;

        // Note: For heavy synchronous I/O, actual interruption must be handled within the ViewModel.
        // Explicit declarative routing to the root application context.
        context.go('/');
      },
      child: Scaffold(
        appBar: AppBar(
          title: const Text(
            "Storage Benchmark",
            style: TextStyle(fontWeight: FontWeight.bold),
          ),
          // Mirrors the hardware back button behavior.
          leading: IconButton(
            icon: const Icon(Icons.arrow_back),
            onPressed: () {
              // Explicit declarative routing to the root dashboard.
              context.go('/');
            },
          ),
        ),
        body: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 10.0),
          child: Column(
            children: [
              const SizedBox(height: 20),

              // MARK: - Benchmark Status HUD
              Consumer<StorageTestViewModel>(
                builder: (context, vm, _) => Column(
                  children: [
                    const Icon(Icons.storage, size: 50, color: Colors.orange),
                    const SizedBox(height: 12),
                    Text(
                      vm.status,
                      style: const TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ],
                ),
              ),

              const SizedBox(height: 25),

              // MARK: - Isolated Progress Visualization
              const _IsolatedProgressView(),

              const SizedBox(height: 25),

              // MARK: - Execution Controls
              Consumer<StorageTestViewModel>(
                builder: (context, vm, _) => Column(
                  children: [
                    // STEP 1: WRITE
                    SizedBox(
                      width: double.infinity,
                      child: ElevatedButton.icon(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.orange,
                          disabledBackgroundColor: Colors.orange.withOpacity(
                            0.5,
                          ),
                          foregroundColor: Colors.white,
                          padding: const EdgeInsets.all(16),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(12),
                          ),
                        ),
                        onPressed: vm.isRunning
                            ? null
                            : () => vm.runBenchmark(true),
                        icon: const Icon(Icons.edit, size: 18),
                        label: const Text(
                          "Step 1: Write 2GB Payload",
                          style: TextStyle(fontWeight: FontWeight.bold),
                        ),
                      ),
                    ),
                    const SizedBox(height: 15),

                    // STEP 2: READ
                    SizedBox(
                      width: double.infinity,
                      child: OutlinedButton.icon(
                        style: OutlinedButton.styleFrom(
                          foregroundColor:
                              (!vm.isRunning && vm.isWriteCompleted)
                              ? Colors.orange
                              : Colors.grey,
                          side: BorderSide(
                            color: (!vm.isRunning && vm.isWriteCompleted)
                                ? Colors.orange
                                : Colors.grey.withOpacity(0.5),
                          ),
                          padding: const EdgeInsets.all(16),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(12),
                          ),
                        ),
                        onPressed: (vm.isRunning || !vm.isWriteCompleted)
                            ? null
                            : () => vm.runBenchmark(false),
                        icon: const Icon(Icons.book, size: 18),
                        label: const Text(
                          "Step 2: Read 2GB Payload",
                          style: TextStyle(fontWeight: FontWeight.bold),
                        ),
                      ),
                    ),
                  ],
                ),
              ),

              const SizedBox(height: 25),

              // MARK: - Telemetry Export Pipeline
              Consumer<StorageTestViewModel>(
                builder: (context, vm, _) {
                  if (vm.isReportReady && !vm.isRunning) {
                    return Container(
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
                              backgroundColor: const Color(0xFF34C759),
                              foregroundColor: Colors.white,
                              padding: const EdgeInsets.symmetric(
                                horizontal: 16,
                                vertical: 10,
                              ),
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(10),
                              ),
                            ),
                            onPressed: () => vm.shareResults(),
                            icon: const Icon(Icons.system_update_alt, size: 16),
                            label: const Text("Save Results"),
                          ),
                        ],
                      ),
                    );
                  }
                  return const SizedBox.shrink();
                },
              ),

              // Pushes the entire layout upwards.
              const Spacer(),
            ],
          ),
        ),
      ),
    );
  }
}

// ─── Recomposition Firewall ──────────────────────────────────────────────────

/// Deeply isolated Consumer widget to prevent UI updates generated by the progress stream
/// from invalidating the static layout tree.
class _IsolatedProgressView extends StatelessWidget {
  const _IsolatedProgressView();

  @override
  Widget build(BuildContext context) {
    return Consumer<StorageTestViewModel>(
      builder: (context, vm, _) => Column(
        children: [
          LinearProgressIndicator(
            value: vm.progress,
            minHeight: 8,
            color: Colors.orange,
            backgroundColor: Colors.grey.withOpacity(0.3),
            borderRadius: BorderRadius.circular(4),
          ),
          const SizedBox(height: 8),
          Text(
            "${(vm.progress * 100).toInt()}%",
            style: const TextStyle(
              fontSize: 14,
              color: Colors.grey,
              fontFamily:
                  'monospace', // Monospace to prevent horizontal jitter.
            ),
          ),
        ],
      ),
    );
  }
}
