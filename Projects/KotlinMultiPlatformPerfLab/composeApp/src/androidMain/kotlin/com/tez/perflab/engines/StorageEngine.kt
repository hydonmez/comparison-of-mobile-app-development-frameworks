package com.tez.perflab.engines

import com.tez.perflab.managers.AndroidPlatformContext
import com.tez.perflab.managers.PlatformContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.concurrent.Volatile

/**
 * Android-specific implementation of the storage engine.
 * Designed to measure I/O processing throughput.
 *
 * Sequential execution is enforced by [Dispatchers.IO.limitedParallelism(1)].
 * Mutex is omitted to avoid coroutine suspension overhead during high-frequency I/O operations.
 */
actual object StorageEngine {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val ioDispatcher = Dispatchers.IO.limitedParallelism(1)

    /**
     * An internal checksum used to prevent the compiler (R8/ProGuard) from optimizing away
     * the read/write loops via Dead Code Elimination (DCE).
     */
    @Volatile
    actual var securityChecksum: Int = 0
        private set

    private fun getTestFile(context: PlatformContext): File {
        val nativeContext = (context as AndroidPlatformContext).androidContext.applicationContext
        return File(nativeContext.filesDir, "benchmark_test_io.tmp")
    }

    actual suspend fun writeData(
        context: PlatformContext,
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
                ensureActive()
                outputStream.write(pattern)

                // Forces a kernel page-cache flush to physical storage.
                fd.sync()

                // Bitwise XOR reduction applied per chunk to measure CPU performance during heavy I/O.
                var xorResult = 0
                for (byte in pattern) {
                    // Applies 0xFF mask to simulate unsigned UInt8 behavior.
                    xorResult = xorResult xor (byte.toInt() and 0xFF)
                }
                securityChecksum = securityChecksum xor xorResult

                onProgress(i.toDouble() / megabytes.toDouble())
            }
        }
    }

    actual suspend fun readData(
        context: PlatformContext,
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

                // Simulated CPU workload to maintain computational stress during the read cycle.
                var xorResult = 0
                for (j in 0 until bytesRead) {
                    // Applies 0xFF mask to simulate unsigned UInt8 behavior.
                    xorResult = xorResult xor (buffer[j].toInt() and 0xFF)
                }
                securityChecksum = securityChecksum xor xorResult

                readBytes += bytesRead
                onProgress(readBytes.toDouble() / totalSize.toDouble())
            }
        }
    }
}