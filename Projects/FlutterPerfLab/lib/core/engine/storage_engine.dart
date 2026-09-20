import 'dart:async';
import 'dart:io';
import 'dart:isolate';
import 'dart:typed_data';
import 'package:path_provider/path_provider.dart';

/// Storage engine for physical I/O benchmarking.
/// Uses a dedicated Isolate and synchronous POSIX I/O to bypass event loop overhead.
/// Forces immediate writes using flushSync() to avoid OS-level filesystem caching.
class StorageEngine {
  // Singleton instance.
  static final StorageEngine shared = StorageEngine._internal();
  StorageEngine._internal();

  Isolate? _activeIsolate;
  ReceivePort? _receivePort;
  int _securityChecksum = 0;

  /// Exposing the checksum prevents the compiler from optimizing away the XOR loop.
  int get securityChecksum => _securityChecksum;

  /// Aborts the active I/O operation.
  void cancel() {
    _activeIsolate?.kill(priority: Isolate.immediate);
    _activeIsolate = null;
    _receivePort?.close();
  }

  Future<String> _getTestFilePath() async {
    final Directory dir = await getApplicationDocumentsDirectory();
    return '${dir.path}/benchmark_test_io.tmp';
  }

  // MARK: - Sequential Write

  /// Executes a continuous write payload using a cyclic byte pattern to bypass SSD compression heuristics.
  Future<void> writeData({
    required int megabytes,
    required void Function(double) onProgress,
  }) async {
    cancel();
    final String filePath = await _getTestFilePath();

    _receivePort = ReceivePort();
    _activeIsolate = await Isolate.spawn(
      _writeTask,
      _WriteTaskArgs(filePath, megabytes, _receivePort!.sendPort),
    );

    // Listen for progress and checksum updates from the I/O Isolate
    await for (final message in _receivePort!) {
      if (message is (double, int)) {
        // message.$1 is progress, message.$2 is the XOR chunk result
        _securityChecksum ^= message.$2;
        onProgress(message.$1);
      } else if (message == true) {
        break; // Operation completed
      }
    }
    _receivePort?.close();
  }

  static void _writeTask(_WriteTaskArgs args) {
    final File file = File(args.filePath);
    if (file.existsSync()) {
      file.deleteSync();
    }
    file.createSync();

    // Open a synchronous file descriptor for writing.
    final RandomAccessFile raf = file.openSync(mode: FileMode.write);

    try {
      const int chunkSize = 1024 * 1024; // 1 Megabyte Chunk
      final Uint8List pattern = Uint8List(chunkSize);

      // Pre-computing the deterministic cyclic payload
      for (int i = 0; i < chunkSize; i++) {
        pattern[i] = i % 256;
      }

      for (int i = 1; i <= args.megabytes; i++) {
        // 1. Synchronous block write.
        raf.writeFromSync(pattern);

        // 2. Forces an immediate flush to disk.
        raf.flushSync();

        // 3. High-speed XOR reduction computation.
        int xorResult = 0;
        for (int j = 0; j < chunkSize; j++) {
          xorResult ^= pattern[j];
        }

        // 4. Send progress and checksum back to the main isolate.
        args.sendPort.send((i / args.megabytes, xorResult));
      }
    } finally {
      raf.closeSync();
      args.sendPort.send(true);
    }
  }

  // MARK: - Sequential Read

  /// Reads the payload back into memory to evaluate read throughput.
  Future<void> readData({required void Function(double) onProgress}) async {
    cancel();
    final String filePath = await _getTestFilePath();
    final File file = File(filePath);

    if (!file.existsSync() || file.lengthSync() == 0) return;

    _receivePort = ReceivePort();
    _activeIsolate = await Isolate.spawn(
      _readTask,
      _ReadTaskArgs(filePath, file.lengthSync(), _receivePort!.sendPort),
    );

    await for (final message in _receivePort!) {
      if (message is (double, int)) {
        _securityChecksum ^= message.$2;
        onProgress(message.$1);
      } else if (message == true) {
        break;
      }
    }
    _receivePort?.close();
  }

  static void _readTask(_ReadTaskArgs args) {
    final RandomAccessFile raf = File(
      args.filePath,
    ).openSync(mode: FileMode.read);
    const int chunkSize = 1024 * 1024;

    // Pre-allocates a contiguous memory block to prevent garbage collector thrashing.
    final Uint8List readBuffer = Uint8List(chunkSize);
    int readBytes = 0;

    try {
      while (readBytes < args.totalSize) {
        // Synchronous read directly into the pre-allocated buffer.
        final int bytesRead = raf.readIntoSync(readBuffer);
        if (bytesRead == 0) break;

        // Checksum verification loop.
        int xorResult = 0;

        for (int j = 0; j < bytesRead; j++) {
          xorResult ^= readBuffer[j];
        }

        readBytes += bytesRead;
        args.sendPort.send((readBytes / args.totalSize, xorResult));
      }
    } finally {
      raf.closeSync();
      args.sendPort.send(true);
    }
  }
}

// MARK: - Isolate Communication Models

class _WriteTaskArgs {
  final String filePath;
  final int megabytes;
  final SendPort sendPort;

  const _WriteTaskArgs(this.filePath, this.megabytes, this.sendPort);
}

class _ReadTaskArgs {
  final String filePath;
  final int totalSize;
  final SendPort sendPort;

  const _ReadTaskArgs(this.filePath, this.totalSize, this.sendPort);
}
