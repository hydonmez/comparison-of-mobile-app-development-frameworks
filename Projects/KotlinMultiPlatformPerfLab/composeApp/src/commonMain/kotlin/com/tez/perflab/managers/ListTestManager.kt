package com.tez.perflab.managers

import com.tez.perflab.models.Product
import com.tez.perflab.models.ProductData
import kotlinx.atomicfu.atomic
import kotlinx.serialization.json.Json

/**
 * A high-throughput, deterministic data provider designed for Compose Multiplatform UI benchmarks.
 *
 * This manager utilizes lock-free concurrency mechanics to ensure thread-safe pagination
 * and strictly O(1) read operations, simulating a heavy e-commerce dataset without
 * introducing I/O bottlenecks during rendering cycles.
 */
object ListTestManager {

    // Configured for lenient parsing to prevent unexpected benchmark crashes
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Thread-safe atomic reference for the dataset to prevent cross-thread race conditions
    private val cachedData = atomic<List<ProductData>>(emptyList())

    // Lock-free atomic cursor for thread-safe pagination state
    private val cursor = atomic(0)

    /**
     * Initializes the in-memory dataset from a raw JSON payload.
     *
     * Architectural Note:
     * Decoding a ByteArray to a String prior to JSON parsing is an inherent
     * framework constraint within `kotlinx.serialization` (commonMain).
     * This introduces a temporary memory allocation spike during initialization,
     * which is an accepted trade-off in the KMP ecosystem unless platform-specific
     * deserializers are implemented.
     *
     * @param jsonBytes The raw JSON file payload.
     * @throws IllegalArgumentException if the provided byte array is empty.
     */
    fun loadJSON(jsonBytes: ByteArray) {
        require(jsonBytes.isNotEmpty()) { "Benchmark payload cannot be empty." }

        try {
            val decodedString = jsonBytes.decodeToString()
            val parsedList = jsonParser.decodeFromString<List<ProductData>>(decodedString)

            // Atomically update the cache to ensure cross-thread visibility
            cachedData.value = parsedList
        } catch (e: Exception) {
            throw IllegalStateException("Failed to parse JSON payload during benchmark initialization.", e)
        }
    }

    /**
     * Generates a deterministic, paginated dataset for UI benchmarking.
     *
     * @param pageSize The number of items requested per page.
     * @return A list of newly instantiated [Product] entities.
     */
    fun fetchPage(pageSize: Int): List<Product> {
        // Read the atomic reference once to prevent data races during iteration
        val currentData = cachedData.value
        if (currentData.isEmpty()) return emptyList()

        // Utilizes Kotlin's inline list factory for optimal memory pre-allocation
        // and avoidance of manual ArrayList capacity management overhead.
        return List(pageSize) {
            val uniqueId = cursor.getAndIncrement()
            val dataItem = currentData[uniqueId % currentData.size]

            Product(id = uniqueId, data = dataItem)
        }
    }

    /**
     * Resets the pagination cursor to guarantee reproducible benchmark iterations.
     */
    fun resetCursor() {
        cursor.value = 0
    }
}