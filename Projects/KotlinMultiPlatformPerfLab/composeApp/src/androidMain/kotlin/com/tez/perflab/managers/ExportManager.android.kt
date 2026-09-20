package com.tez.perflab.managers

import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Persists the dataset to the application's isolated cache directory.
 * Executes asynchronously on an I/O dispatcher to prevent main thread blocking.
 */
actual suspend fun saveCsvToCache(
    fileName: String,
    content: String,
    context: PlatformContext
): String? = withContext(Dispatchers.IO) {
    try {
        val nativeContext = (context as AndroidPlatformContext).androidContext
        val cacheDir = nativeContext.cacheDir

        val file = File(cacheDir, fileName)
        file.writeText(content)

        return@withContext file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Invokes the native OS share sheet utilizing Android's Inter-Process Communication (IPC).
 * Grants temporary read access to external applications via FileProvider authority.
 */
actual fun platformShareFile(context: PlatformContext, filePath: String) {
    val nativeContext = (context as AndroidPlatformContext).androidContext
    val file = File(filePath)

    if (!file.exists()) return

    try {
        val appId = nativeContext.packageName
        val contentUri = FileProvider.getUriForFile(
            nativeContext,
            "$appId.provider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooserIntent = Intent.createChooser(shareIntent, "PerfLab: Benchmark Results")
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        nativeContext.startActivity(chooserIntent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

actual fun getCurrentEpochSeconds(): Long {
    return System.currentTimeMillis() / 1000
}