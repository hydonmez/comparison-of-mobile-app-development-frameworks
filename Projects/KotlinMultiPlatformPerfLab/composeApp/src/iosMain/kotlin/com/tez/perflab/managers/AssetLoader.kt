package com.tez.perflab.managers

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSDataReadingMappedIfSafe
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.posix.memcpy

/**
 * iOS-specific implementation for loading benchmark payloads from the application bundle.
 */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun loadBenchmarkBytes(fileName: String): ByteArray =
    withContext(Dispatchers.Default) {
        autoreleasepool {
            val resourceName = fileName.substringBeforeLast('.', fileName)
            val extension = fileName.substringAfterLast('.', "")

            val url: NSURL = NSBundle.mainBundle.URLForResource(
                name = resourceName,
                withExtension = extension
            ) ?: throw IllegalArgumentException("Benchmark payload '$fileName' not found in NSBundle.")

            // Memory-mapped file reading minimizes initial I/O overhead.
            // Due to Kotlin/Native interoperability constraints, returning a Kotlin ByteArray
            // requires copying the data from the mapped virtual memory into the Kotlin heap.
            val data: NSData = NSData.dataWithContentsOfURL(
                url = url,
                options = NSDataReadingMappedIfSafe,
                error = null
            ) ?: throw IllegalStateException("Failed to read NSData from URL: $url")

            val length = data.length.toInt()
            if (length == 0) return@autoreleasepool ByteArray(0)

            val sourcePointer = data.bytes
                ?: throw IllegalStateException("NSData.bytes returned a null pointer for payload: $fileName")

            // Safely allocates a Kotlin ByteArray and performs a low-level C memory copy.
            // usePinned ensures the Garbage Collector does not move the array during the transfer.
            ByteArray(length).also { bytes ->
                bytes.usePinned { pinned ->
                    memcpy(
                        pinned.addressOf(0),
                        sourcePointer,
                        data.length.toULong()
                    )
                }
            }
        }
    }

/**
 * Extracts the absolute POSIX file path from the iOS application bundle.
 * Prefixing with the `file://` scheme ensures that cross-platform image loaders
 * treat the asset as a local disk resource.
 */
actual fun getPlatformImagePath(fileName: String): String {
    val resourceName = fileName.substringBeforeLast('.', fileName)
    val extension = fileName.substringAfterLast('.', "")

    val absolutePath = NSBundle.mainBundle.pathForResource(
        name = resourceName,
        ofType = extension
    ) ?: throw IllegalArgumentException("Asset '$fileName' not found in NSBundle.")

    return "file://$absolutePath"
}

/**
 * Asynchronously loads and decodes an image directly into a Skia ImageBitmap.
 * Executed on Dispatchers.Default to ensure heavy decoding operations do not
 * block the Main thread during UI benchmarks.
 */
actual suspend fun loadOptimizedImageBitmap(fileName: String): ImageBitmap? =
    withContext(Dispatchers.Default) {
        val bytes = loadBenchmarkBytes(fileName)
        if (bytes.isEmpty()) return@withContext null

        try {
            // Decodes the raw ByteArray payload directly into Skia's memory space.
            Image.makeFromEncoded(bytes).toComposeImageBitmap()
        } catch (e: Exception) {
            null
        }
    }