import 'dart:convert';
import 'package:flutter/foundation.dart'; // Required for compute()
import 'package:flutter/services.dart';
import '../../models/product_data.dart';

/// Top-level function for background isolate execution.
///
/// Offloads heavy JSON decoding from the Main UI Isolate. This prevents UI thread
/// blockage, minimizes CPU spikes, and isolates Garbage Collection churn
/// during the initial payload parsing.
List<ProductData> _parseJsonInBackground(String jsonString) {
  final List<dynamic> rawList = jsonDecode(jsonString) as List<dynamic>;

  return rawList
      .map((dynamic item) => ProductData.fromJson(item as Map<String, dynamic>))
      .toList(
        growable: false,
      ); // Pre-allocated fixed-size list for memory efficiency
}

/// A high-throughput, in-memory data provider engineered for virtualized UI
/// scrolling benchmarks (SliverList/ListView).
///
/// To ensure zero context-switching overhead on the UI thread, heavy I/O and
/// parsing operations are explicitly offloaded to a secondary isolate.
class ListTestManager {
  // Singleton instance
  static final ListTestManager shared = ListTestManager._internal();
  ListTestManager._internal();

  List<ProductData> _cachedData = [];
  int _cursor = 0;
  bool _isInitialized = false;

  /// Initializes the in-memory dataset asynchronously.
  Future<void> init() async {
    if (_isInitialized) return;

    try {
      // 1. Offload heavy File I/O via platform channels.
      final String jsonString = await rootBundle.loadString(
        'assets/mock_products.json',
      );

      // 2. Offload computational parsing to a background isolate.
      _cachedData = await compute(_parseJsonInBackground, jsonString);

      _isInitialized = true;
    } catch (e) {
      throw Exception(
        "CRITICAL: JSON decoding failed. Ensure mock_products.json is in assets. Error: $e",
      );
    }
  }

  /// Generates a deterministic page of mock products for the UI layer.
  ///
  /// List.generate with growable: false strictly pre-allocates contiguous memory
  /// for the page. This eliminates the O(n) overhead of dynamic array resizing
  /// during rapid scrolling.
  List<Product> fetchPage(int pageSize) {
    if (_cachedData.isEmpty) return [];

    // O(1) identity generation using the Event Loop's natural sequential execution.
    return List<Product>.generate(pageSize, (index) {
      final int uniqueId = _cursor;
      final int cacheIndex = uniqueId % _cachedData.length;

      // Cursor incremented synchronously.
      _cursor++;

      return Product(id: uniqueId, data: _cachedData[cacheIndex]);
    }, growable: false); // Enforces fixed-length memory allocation
  }

  /// Resets the pagination cursor to its initial state (0).
  /// Essential for reproducibility; guarantees that every benchmark session starts
  /// with the exact same data sequence.
  void resetCursor() {
    _cursor = 0;
  }
}
