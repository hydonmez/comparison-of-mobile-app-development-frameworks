import 'dart:convert';
import 'package:flutter/services.dart';
import '../../models/github_event.dart';

/// A benchmarking engine engineered to measure the raw CPU throughput of Dart's
/// JSON decoding.
///
/// Note: Dart's dart:convert uses an AST-based parsing model, requiring an
/// intermediate Map<String, dynamic> tree before strong-typed object mapping occurs.
class JsonTestManager {
  static final JsonTestManager shared = JsonTestManager._internal();
  JsonTestManager._internal();

  Uint8List? _cachedBytes;

  /// Exposes the preloaded payload to be safely transferred across isolate boundaries.
  Uint8List? get cachedPayload => _cachedBytes;

  // MARK: - Pre-Computation Phase

  Future<void> preloadDataOnce() async {
    if (_cachedBytes != null) return;

    try {
      final ByteData data = await rootBundle.load('assets/benchmark_data.json');
      _cachedBytes = data.buffer.asUint8List();
    } catch (e) {
      throw Exception(
        "CRITICAL: JSON benchmark file not found. Ensure it is added to pubspec.yaml. Error: $e",
      );
    }
  }

  // MARK: - Active Benchmark Phase

  /// Executes a synchronous JSON parsing iteration.
  ///
  /// Accepts the raw payload as a parameter to guarantee memory visibility
  /// when executed within a detached background isolate context.
  int runParseTest(Uint8List payload) {
    try {
      // Fusing utf8 and json decoders streams the byte payload directly through
      // the Dart VM's engine, bypassing user-space UTF-16 String allocation.
      final dynamic decodedTree = utf8.decoder
          .fuse(json.decoder)
          .convert(payload);

      final List<dynamic> rawList = decodedTree as List<dynamic>;
      final int length = rawList.length;

      // Replacing .map().toList() with List.generate to bypass iterator closure overhead.
      // This pre-allocates the exact contiguous memory block required for the array,
      // minimizing GC churn during object translation.
      final List<GitHubEvent> events = List<GitHubEvent>.generate(
        length,
        (int index) =>
            GitHubEvent.fromJson(rawList[index] as Map<String, dynamic>),
        growable: false,
      );

      return events.length;
    } catch (e) {
      return 0;
    }
  }

  /// Explicit memory management to purge the payload and prevent RAM inflation.
  void releaseMemory() {
    _cachedBytes = null;
  }
}
