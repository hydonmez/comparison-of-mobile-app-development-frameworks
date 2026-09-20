import 'dart:async';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:go_router/go_router.dart';

import 'package:flutterperflab/models/product_data.dart';
import 'list_test_view_model.dart'; // Ensure ScrollCommand is defined here

/// A virtualized scroll UI engineered to evaluate hardware rendering throughput
/// and GPU frame-time during high-frequency programmatic scrolling.
class ListTestScreen extends StatelessWidget {
  const ListTestScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (_) => ListTestViewModel(),
      child: const _ListTestContent(),
    );
  }
}

class _ListTestContent extends StatefulWidget {
  const _ListTestContent();

  @override
  State<_ListTestContent> createState() => _ListTestContentState();
}

class _ListTestContentState extends State<_ListTestContent> {
  final ScrollController _scrollController = ScrollController();
  StreamSubscription<ScrollCommand>? _scrollSubscription;

  @override
  void initState() {
    super.initState();

    // Listens strictly to precise animation commands (index, duration, easing curve)
    // bypassing full widget tree recomposition.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final vm = context.read<ListTestViewModel>();

      _scrollSubscription = vm.scrollCommandStream.listen((command) {
        if (!_scrollController.hasClients) return;

        // Calculates the precise offset based on strict layout dimensions
        // instead of manual offset math to ensure deterministic scrolling.
        // Row calculation: 2 items per row.
        final int rowIndex = command.targetIndex ~/ 2;

        // 280 (Card Height) + 15 (Spacing).
        // Adding 16 for top padding offset.
        final double targetOffset = (rowIndex * 295.0) + 16.0;

        // Prevents out-of-bounds scrolling exceptions.
        final maxScroll = _scrollController.position.maxScrollExtent;
        final finalOffset = targetOffset > maxScroll ? maxScroll : targetOffset;

        _scrollController.animateTo(
          finalOffset,
          duration: command.duration,
          curve: command.curve,
        );
      });
    });
  }

  @override
  void dispose() {
    _scrollSubscription?.cancel();
    _scrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (bool didPop, dynamic result) {
        if (didPop) return;
        final vm = context.read<ListTestViewModel>();
        if (vm.isRunning) vm.stopTest(isFinished: false);
        context.go('/');
      },
      child: Scaffold(
        backgroundColor: const Color(0xFFF2F2F7),
        appBar: AppBar(
          title: const Text(
            "Storefront Performance",
            style: TextStyle(fontWeight: FontWeight.bold),
          ),
          backgroundColor: Colors.white,
          leading: IconButton(
            icon: const Icon(Icons.arrow_back),
            onPressed: () {
              final vm = context.read<ListTestViewModel>();
              if (vm.isRunning) vm.stopTest(isFinished: false);
              context.go('/');
            },
          ),
        ),
        body: Column(
          children: [
            // --- Telemetry Monitor Bar ---
            // Isolated Consumer prevents main view re-rendering.
            Consumer<ListTestViewModel>(
              builder: (context, vm, _) => Container(
                color: const Color(0xFFF2F2F7),
                padding: const EdgeInsets.symmetric(
                  horizontal: 16.0,
                  vertical: 12.0,
                ),
                child: Row(
                  children: [
                    Expanded(
                      child: Text(
                        vm.status,
                        style: const TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.bold,
                          color: Colors.grey,
                        ),
                      ),
                    ),
                    if (vm.isLoadingMore)
                      const SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      ),
                  ],
                ),
              ),
            ),

            // --- Main Rendering Surface ---
            Expanded(
              // Using Selector ensures the heavy GridView is ONLY rebuilt when the
              // total count of products changes, not when status/text changes.
              child: Selector<ListTestViewModel, int>(
                selector: (context, vm) => vm.products.length,
                builder: (context, productCount, _) {
                  final vm = context.read<ListTestViewModel>();

                  return GridView.builder(
                    controller: _scrollController,
                    padding: const EdgeInsets.all(16.0),
                    gridDelegate:
                        const SliverGridDelegateWithFixedCrossAxisCount(
                          crossAxisCount: 2,
                          crossAxisSpacing: 15.0,
                          mainAxisSpacing: 15.0,
                          mainAxisExtent: 280.0,
                        ),
                    itemCount:
                        productCount +
                        (vm.isRunning && productCount < 300 ? 2 : 0),
                    itemBuilder: (context, index) {
                      if (index >= productCount) {
                        return const Center(child: CircularProgressIndicator());
                      }

                      final product = vm.products[index];
                      return RepaintBoundary(
                        key: ValueKey(product.id),
                        child: _ProductCardView(product: product),
                      );
                    },
                  );
                },
              ),
            ),

            // --- Controller Interface ---
            // Isolated Consumer for button states.
            Consumer<ListTestViewModel>(
              builder: (context, vm, _) => Container(
                color: Colors.white,
                padding: const EdgeInsets.all(16.0),
                child: Column(
                  children: [
                    if (!vm.isRunning && !vm.isReportReady)
                      SizedBox(
                        width: double.infinity,
                        height: 56,
                        child: ElevatedButton.icon(
                          style: ElevatedButton.styleFrom(
                            backgroundColor: Colors.blue,
                            foregroundColor: Colors.white,
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(12),
                            ),
                          ),
                          onPressed: () => vm.startTest(),
                          icon: const Icon(Icons.shopping_cart),
                          label: const Text(
                            "Start E-Commerce Benchmark",
                            style: TextStyle(fontWeight: FontWeight.bold),
                          ),
                        ),
                      )
                    else if (vm.isRunning)
                      SizedBox(
                        width: double.infinity,
                        height: 56,
                        child: ElevatedButton.icon(
                          style: ElevatedButton.styleFrom(
                            backgroundColor: Colors.red,
                            foregroundColor: Colors.white,
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(12),
                            ),
                          ),
                          onPressed: () => vm.stopTest(isFinished: false),
                          icon: const Icon(Icons.stop),
                          label: const Text(
                            "Stop Benchmark",
                            style: TextStyle(fontWeight: FontWeight.bold),
                          ),
                        ),
                      ),

                    if (vm.isReportReady && !vm.isRunning)
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
                          onPressed: () => vm.exportResults(),
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
            ),
          ],
        ),
      ),
    );
  }
}

// MARK: - Product Card Component

const Map<String, String> _assetRegistry = {
  "laptop": "assets/laptop.jpg",
  "shoe": "assets/shoe.jpg",
  "smartphone": "assets/smartphone.jpg",
  "tablet": "assets/tablet.jpg",
  "watch": "assets/watch.jpg",
  "testphoto": "assets/testphoto.jpg",
};

class _ProductCardView extends StatelessWidget {
  final Product product;

  const _ProductCardView({required this.product});

  @override
  Widget build(BuildContext context) {
    final String cleanName = product.data.imageName.split('.').first;
    final String? assetPath = _assetRegistry[cleanName];

    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12.0),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.1),
            blurRadius: 5,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            height: 150,
            child: Stack(
              children: [
                ClipRRect(
                  borderRadius: const BorderRadius.vertical(
                    top: Radius.circular(12.0),
                  ),
                  child: Container(
                    width: double.infinity,
                    color: Colors.white,
                    child: assetPath != null
                        ? Image.asset(
                            assetPath,
                            fit: BoxFit.contain,
                            // Reduces anti-aliasing CPU cost.
                            filterQuality: FilterQuality.low,
                            // Prevents flickering.
                            gaplessPlayback: true,
                          )
                        : Container(
                            color: Colors.grey.shade300,
                            child: const Center(
                              child: Text(
                                "Asset Missing",
                                style: TextStyle(
                                  fontSize: 10,
                                  color: Colors.grey,
                                ),
                              ),
                            ),
                          ),
                  ),
                ),
                Positioned(
                  top: 8,
                  right: 8,
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 8,
                      vertical: 4,
                    ),
                    decoration: BoxDecoration(
                      color: Colors.red,
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: Text(
                      product.data.discount,
                      style: const TextStyle(
                        fontSize: 10,
                        fontWeight: FontWeight.bold,
                        color: Colors.white,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.all(10.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    product.data.name,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                      color: Colors.black87,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    product.data.category,
                    style: const TextStyle(fontSize: 12, color: Colors.grey),
                  ),
                  const Spacer(),
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            product.data.oldPrice,
                            style: const TextStyle(
                              fontSize: 10,
                              color: Colors.grey,
                              decoration: TextDecoration.lineThrough,
                            ),
                          ),
                          Text(
                            product.data.price,
                            style: const TextStyle(
                              fontSize: 16,
                              fontWeight: FontWeight.bold,
                              color: Colors.orange,
                            ),
                          ),
                        ],
                      ),
                      const Spacer(),
                      const Icon(
                        Icons.add_circle,
                        color: Colors.blue,
                        size: 24,
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
