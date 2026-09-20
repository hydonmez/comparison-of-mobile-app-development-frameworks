package com.tez.perflab.managers

import com.tez.perflab.models.GitHubEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile

/**
 * A cross-platform JSON deserialization engine designed for CPU throughput benchmarks.
 *
 * The payload is cached as a UTF-16 [String] to isolate pure computational parsing
 * from Disk I/O latency. [@Volatile] guarantees cross-thread visibility between the
 * loading thread (IO) and the parsing thread (Default/Main).
 */
object JsonTestManager {

    // Double-checked locking prevents redundant I/O operations, while @Volatile
    // ensures the JIT compiler does not serve stale data from CPU registers.
    @Volatile
    private var cachedString: String? = null
    private val cacheMutex = Mutex()

    /**
     * Configured to match the default behaviors of Moshi (Android) and JSONDecoder (iOS).
     */
    private val json = Json {
        ignoreUnknownKeys = true
    }

    /**
     * Pre-computes the generic ListSerializer to circumvent the overhead of
     * reified type resolution during high-frequency benchmark loops.
     */
    private val listSerializer = ListSerializer(GitHubEvent.serializer())

    /**
     * Asynchronously preloads the JSON payload into RAM.
     * Conversion to String is performed here to ensure [runParseTest] measures
     * only pure CPU-bound deserialization.
     */
    suspend fun preloadDataOnce(fileName: String = "benchmark_data.json") {
        // Fast-path: Avoid lock acquisition if data is already resident in RAM.
        if (cachedString != null) return

        cacheMutex.withLock {
            // Second check inside the lock to prevent race-condition duplications.
            if (cachedString != null) return

            cachedString = withContext(Dispatchers.IO) {
                try {
                    loadBenchmarkBytes(fileName).decodeToString()
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    /**
     * Executes a synchronous JSON parsing workload using a pre-compiled Serializer.
     * @return The item count to prevent compiler Dead Code Elimination (DCE).
     */
    fun runParseTest(): Int {
        val payload = cachedString ?: return 0

        return try {
            // Injects the cached Serializer directly to bypass reflection-like overhead.
            val items = json.decodeFromString(listSerializer, payload)
            items.size
        } catch (e: Exception) {
            // Graceful degradation for malformed telemetry assets.
            0
        }
    }

    /**
     * Enforces explicit memory deallocation post-benchmark to prevent memory inflation
     * during subsequent test phases.
     */
    suspend fun releaseMemory() {
        cacheMutex.withLock {
            cachedString = null
        }
    }
}