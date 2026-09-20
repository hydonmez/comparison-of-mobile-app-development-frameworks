package com.tez.nativekotlinperflabapp.core.engines

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.concurrent.Volatile

/**
 * Storage I/O engine for Android.
 * Confines all I/O operations to a single background thread for consistency.
 */
object StorageEngine {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val ioDispatcher = Dispatchers.IO.limitedParallelism(1)

    /**
     * An internal checksum used to prevent compiler optimizations (such as Dead Code Elimination)
     * from bypassing the read/write loops in release builds.
     */
    @Volatile
    var securityChecksum: Int = 0
        private set

    private fun getTestFile(context: Context): File =
        File(context.applicationContext.filesDir, "benchmark_test_io.tmp")

    /**
     * Performs sequential file write operations.
     * Uses a cyclic byte pattern and forces a physical disk flush on every iteration.
     */
    suspend fun writeData(
        context: Context,
        megabytes: Int,
        onProgress: (Double) -> Unit
    ) = withContext(ioDispatcher) {
        val file = getTestFile(context)
        if (file.exists()) file.delete()
        file.createNewFile()

        val chunkSize = 1024 * 1024
        val pattern = ByteArray(chunkSize) { (it % 256).toByte() }

        FileOutputStream(file).use { outputStream ->
            val fd = outputStream.fd

            for (i in 1..megabytes) {
                ensureActive() // Cooperative cancellation
                outputStream.write(pattern)

                // Forces a physical write to the storage.
                fd.sync()

                // XOR reduction workload for processing simulation.
                var xorResult = 0
                for (byte in pattern) {
                    // Applies 0xFF mask for unsigned byte behavior.
                    xorResult = xorResult xor (byte.toInt() and 0xFF)
                }
                securityChecksum = securityChecksum xor xorResult

                onProgress(i.toDouble() / megabytes.toDouble())
            }
        }
    }

    /**
     * Performs sequential file read operations to evaluate read throughput.
     */
    suspend fun readData(
        context: Context,
        onProgress: (Double) -> Unit
    ) = withContext(ioDispatcher) {
        val file = getTestFile(context)
        if (!file.exists() || file.length() == 0L) return@withContext

        val totalSize = file.length()
        val chunkSize = 1024 * 1024
        val buffer = ByteArray(chunkSize)
        var readBytes: Long = 0

        FileInputStream(file).use { inputStream ->
            while (readBytes < totalSize) {
                ensureActive()
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break

                // XOR reduction workload for processing simulation.
                var xorResult = 0
                for (j in 0 until bytesRead) {
                    // Applies 0xFF mask for unsigned byte behavior.
                    xorResult = xorResult xor (buffer[j].toInt() and 0xFF)
                }
                securityChecksum = securityChecksum xor xorResult

                readBytes += bytesRead
                onProgress(readBytes.toDouble() / totalSize.toDouble())
            }
        }
    }
}