package com.tez.nativekotlinperflabapp.core.managers

import android.content.Context
import android.util.Log
import com.tez.nativekotlinperflabapp.models.Product
import com.tez.nativekotlinperflabapp.models.ProductData
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.atomicfu.atomic

/**
 * A thread-safe, in-memory data provider for list pagination.
 * Centralizes the data stream through an atomic cursor to guarantee
 * a consistent sequence of items across multiple threads.
 */
object ListTestManager {

    // Reusable decoder instance to reduce overhead.
    private val decoder = Json { ignoreUnknownKeys = true }

    // Atomic cursor ensures safe concurrent pagination.
    private val cursor = atomic(0)

    @Volatile
    private var isInitialized = false

    private var cachedData: List<ProductData> = emptyList()

    /**
     * Initializes the in-memory dataset from the APK asset bundle.
     * Uses a thread-safe approach to ensure the data is loaded exactly once.
     */
    fun init(context: Context) {
        // Skips synchronization if already initialized.
        if (isInitialized) return

        synchronized(this) {
            // Double-check inside the lock to prevent race conditions.
            if (isInitialized) return

            val appContext = context.applicationContext
            loadJSON(appContext)
            isInitialized = true
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun loadJSON(context: Context) {
        try {
            // Streams directly from assets to minimize memory allocation overhead.
            context.assets.open("mock_products.json").use { inputStream ->
                cachedData = decoder.decodeFromStream(inputStream)
            }
            Log.i("ListTestManager", "Dataset loaded. Item count: ${cachedData.size}")
        } catch (e: Exception) {
            Log.e("ListTestManager", "JSON decoding failed: ${e.message}", e)
        }
    }

    /**
     * Generates a page of mock products.
     *
     * @param pageSize The number of items to generate per page.
     * @return A list of [Product] entities with unique sequential identifiers.
     */
    fun fetchPage(pageSize: Int): List<Product> {
        if (cachedData.isEmpty()) return emptyList()

        // Pre-allocates list capacity to avoid dynamic array resizing overhead.
        val page = ArrayList<Product>(pageSize)

        repeat(pageSize) {
            // Generates sequential IDs.
            val uniqueId = cursor.getAndIncrement()
            val cacheIndex = uniqueId % cachedData.size
            page.add(Product(id = uniqueId, data = cachedData[cacheIndex]))
        }

        return page
    }

    /**
     * Resets the pagination cursor to its initial state.
     * Ensures that every run starts with the exact same data sequence.
     */
    fun resetCursor() {
        cursor.value = 0
    }
}