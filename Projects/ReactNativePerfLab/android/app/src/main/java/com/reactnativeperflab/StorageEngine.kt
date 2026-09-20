package com.reactnativeperflab

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.concurrent.Volatile

/**
 * Native Android Storage I/O Engine
 * Engineered for sequential storage benchmarking.
 */
object StorageEngine {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val ioDispatcher = Dispatchers.IO.limitedParallelism(1)

    @Volatile
    var securityChecksum: Int = 0
        private set

    private fun getTestFile(context: Context): File =
        File(context.applicationContext.filesDir, "benchmark_test_io.tmp")

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
                ensureActive() 
                outputStream.write(pattern)
                fd.sync()

                var xorResult = 0
                for (byte in pattern) {
                    xorResult = xorResult xor (byte.toInt() and 0xFF)
                }
                securityChecksum = securityChecksum xor xorResult

                onProgress(i.toDouble() / megabytes.toDouble())
            }
        }
    }

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

                var xorResult = 0
                for (j in 0 until bytesRead) {
                    xorResult = xorResult xor (buffer[j].toInt() and 0xFF)
                }
                securityChecksum = securityChecksum xor xorResult

                readBytes += bytesRead
                onProgress(readBytes.toDouble() / totalSize.toDouble())
            }
        }
    }
}