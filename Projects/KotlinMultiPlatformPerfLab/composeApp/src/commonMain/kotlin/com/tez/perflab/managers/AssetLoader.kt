package com.tez.perflab.managers

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Platform-agnostic interface for reading raw asset files directly into memory.
 * Decouples shared business logic from OS-level constraints (e.g., Android Context vs iOS NSBundle).
 *
 * @param fileName The exact name of the asset file (including extension).
 * @return A ByteArray containing the raw file payload.
 */
expect suspend fun loadBenchmarkBytes(fileName: String): ByteArray

/**
 * Resolves the physical asset path for direct I/O operations in image loading pipelines.
 * Bypasses framework-level abstractions to maintain accurate hardware-level rendering metrics.
 *
 * @param fileName The exact name of the asset (e.g., "laptop.jpg").
 * @return The absolute file URI required for direct disk reads.
 */
expect fun getPlatformImagePath(fileName: String): String

/**
 * Decodes a raw asset directly into a hardware-backed ImageBitmap.
 * Applies memory optimizations required for high-throughput GPU filtering benchmarks.
 * 
 * @param fileName The exact name of the asset (e.g., "testphoto.jpg").
 * @return An optimized ImageBitmap ready for pixel manipulation, or null if decoding fails.
 */
expect suspend fun loadOptimizedImageBitmap(fileName: String): ImageBitmap?