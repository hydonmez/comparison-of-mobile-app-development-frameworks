package com.tez.nativekotlinperflabapp.core.managers

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.tez.nativekotlinperflabapp.models.GitHubEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source

/**
 * An optimized JSON parsing engine.
 * Retains the raw payload as a UTF-8 [ByteArray] in memory to isolate CPU
 * parsing operations from Disk I/O latency.
 */
object JsonTestManager {

    // Caching raw bytes avoids the memory overhead of String allocations.
    // @Volatile ensures the background thread write is immediately visible.
    @Volatile
    private var cachedBytes: ByteArray? = null

    // Persistent Moshi instance.
    // Uses generated adapters to avoid runtime reflection.
    private val moshi = Moshi.Builder().build()

    // Pre-computed type resolution to eliminate reflection overhead.
    private val listType = Types.newParameterizedType(List::class.java, GitHubEvent::class.java)
    private val adapter = moshi.adapter<List<GitHubEvent>>(listType)

    /**
     * Loads the JSON payload from the APK assets directly into memory.
     * Ensures that File I/O does not affect parsing metrics.
     */
    suspend fun preloadDataOnce(context: Context) = withContext(Dispatchers.IO) {
        if (cachedBytes == null) {
            val appContext = context.applicationContext
            // Direct asset-to-byte loading to avoid intermediate object overhead.
            cachedBytes = appContext.assets.open("benchmark_data.json").use { it.readBytes() }
        }
    }

    /**
     * Executes a synchronous JSON parsing iteration using a streaming approach.
     * Wraps the in-memory buffer without additional memory duplication.
     *
     * @return The size of the decoded collection, returned to prevent compiler optimization
     *         (Dead Code Elimination) from bypassing the operation.
     */
    fun runParseTest(): Int {
        val bytes = cachedBytes ?: return 0

        // Streams the UTF-8 bytes directly. Avoids creating intermediate Strings
        // to reduce Garbage Collection pressure.
        val source = bytes.inputStream().source().buffer()

        return try {
            val events = adapter.fromJson(source) ?: emptyList()

            // Returning the size ensures the compiler actually executes the parsing.
            events.size
        } catch (e: Exception) {
            android.util.Log.e("Benchmark", "Parsing failure: ${e.message}")
            0
        } finally {
            source.close()
        }
    }

    /**
     * Clears the cached payload to release memory.
     */
    fun releaseMemory() {
        cachedBytes = null
    }
}