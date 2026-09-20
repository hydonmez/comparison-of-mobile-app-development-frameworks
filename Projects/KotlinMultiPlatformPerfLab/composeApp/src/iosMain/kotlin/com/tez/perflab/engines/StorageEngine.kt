@file:OptIn(ExperimentalForeignApi::class)

package com.tez.perflab.engines

import com.tez.perflab.managers.PlatformContext
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import platform.Foundation.*
import platform.posix.memcpy
import kotlin.concurrent.Volatile

/**
 * A storage engine for benchmarking sequential I/O operations on iOS.
 * Evaluates file system throughput while minimizing Kotlin/Native Objective-C interop overhead.
 */
actual object StorageEngine {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val ioDispatcher = Dispatchers.IO.limitedParallelism(1)

    /**
     * An internal checksum used to prevent the compiler from optimizing away 
     * read/write loops via Dead Code Elimination (DCE).
     */
    @Volatile
    actual var securityChecksum: Int = 0
        private set

    /**
     * Resolves the URL for the benchmark test file within the application's document directory.
     */
    private fun getTestFileURL(): NSURL {
        val fileManager = NSFileManager.defaultManager
        val urls = fileManager.URLsForDirectory(NSDocumentDirectory, inDomains = NSUserDomainMask)
        val docDir = urls.first() as NSURL
        return docDir.URLByAppendingPathComponent("benchmark_test_io.tmp")!!
    }

    /**
     * Writes a specified amount of data to disk sequentially using pre-allocated memory chunks.
     *
     * @param context The platform-specific context.
     * @param megabytes The total number of megabytes to write.
     * @param onProgress A callback reporting the write progress (0.0 to 1.0).
     */
    actual suspend fun writeData(
        context: PlatformContext,
        megabytes: Int,
        onProgress: (Double) -> Unit
    ) = withContext(ioDispatcher) {

        val url = getTestFileURL()
        val fileManager = NSFileManager.defaultManager

        if (fileManager.fileExistsAtPath(url.path!!)) {
            fileManager.removeItemAtURL(url, null)
        }
        fileManager.createFileAtPath(url.path!!, contents = null, attributes = null)

        val handle = NSFileHandle.fileHandleForWritingToURL(url, null) ?: return@withContext

        // Pre-allocate a 1MB chunk to prevent memory allocation overhead during the iteration loop.
        val chunkSize = 1024 * 1024
        val patternArray = ByteArray(chunkSize) { (it % 256).toByte() }

        val nsData = patternArray.usePinned { pinned ->
            NSData.dataWithBytes(pinned.addressOf(0), chunkSize.toULong())
        }

        try {
            for (i in 1..megabytes) {
                autoreleasepool {
                    ensureActive()

                    handle.writeData(nsData)

                    // Force hardware synchronization to ensure data is physically written to the disk.
                    handle.synchronizeFile()

                    var xorResult = 0
                    for (byte in patternArray) {
                        // Applies 0xFF mask to handle unsigned UInt8 behavior.
                        xorResult = xorResult xor (byte.toInt() and 0xFF)
                    }
                    securityChecksum = securityChecksum xor xorResult

                    onProgress(i.toDouble() / megabytes.toDouble())
                }
            }
        } finally {
            handle.closeFile()
        }
    }

    /**
     * Reads the benchmark data from disk in chunks and calculates a verification checksum.
     *
     * @param context The platform-specific context.
     * @param onProgress A callback reporting the read progress (0.0 to 1.0).
     */
    actual suspend fun readData(
        context: PlatformContext,
        onProgress: (Double) -> Unit
    ) = withContext(ioDispatcher) {

        val url = getTestFileURL()
        val fileManager = NSFileManager.defaultManager

        if (!fileManager.fileExistsAtPath(url.path!!)) return@withContext

        val handle = NSFileHandle.fileHandleForReadingFromURL(url, null) ?: return@withContext
        val attributes = fileManager.attributesOfItemAtPath(url.path!!, null)

        // Safely cast to Kotlin's Number interface instead of NSNumber.
        val totalSize = (attributes?.get(NSFileSize) as? Number)?.toLong() ?: 0L

        if (totalSize == 0L) {
            handle.closeFile()
            return@withContext
        }

        val chunkSize = 1024 * 1024
        var readBytes: Long = 0

        try {
            while (readBytes < totalSize) {
                autoreleasepool {
                    ensureActive()

                    val chunkData = handle.readDataOfLength(chunkSize.toULong())

                    if (chunkData.length == 0uL) return@withContext

                    val length = chunkData.length.toInt()
                    val bytes = chunkData.bytes

                    if (bytes != null && length > 0) {
                        // Using memcpy to copy data in a single operation into a native Kotlin 
                        // ByteArray allows iteration to happen entirely in local memory space.
                        val kotlinArray = ByteArray(length)
                        kotlinArray.usePinned { pinned ->
                            memcpy(pinned.addressOf(0), bytes, length.toULong())
                        }

                        var xorResult = 0
                        for (byte in kotlinArray) {
                            // Applies 0xFF mask to handle unsigned UInt8 behavior.
                            xorResult = xorResult xor (byte.toInt() and 0xFF)
                        }
                        securityChecksum = securityChecksum xor xorResult
                    }

                    readBytes += chunkData.length.toLong()
                    onProgress(readBytes.toDouble() / totalSize.toDouble())
                }
            }
        } finally {
            handle.closeFile()
        }
    }
}