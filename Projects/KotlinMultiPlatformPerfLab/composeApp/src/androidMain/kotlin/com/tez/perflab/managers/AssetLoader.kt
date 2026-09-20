package com.tez.perflab.managers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Global registry for the application-level context.
 * Retains the application context to safely bridge Android-specific
 * lifecycle dependencies into the KMP domain without leaking Activities.
 *
 * [appContext] is declared [@Volatile] to guarantee cross-thread visibility on the JVM.
 * This prevents I/O threads from observing a stale null after the Main Thread initializes it.
 */
object ApplicationContextHolder {

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        // Uses ApplicationContext to prevent Activity and memory leaks.
        appContext = context.applicationContext
    }

    fun get(): Context {
        return appContext
            ?: error("ApplicationContextHolder is not initialized. Call init() within Application.onCreate().")
    }
}

/**
 * Reads raw byte data from the Android assets directory.
 * Allocates the entire file payload into memory; suitable for payloads
 * where file size is bounded and predictable.
 */
actual suspend fun loadBenchmarkBytes(fileName: String): ByteArray =
    withContext(Dispatchers.IO) {
        val context = ApplicationContextHolder.get()
        context.assets.open(fileName).use { it.readBytes() }
    }

/**
 * Constructs a direct URI to the Android AssetManager.
 * The [file:///android_asset/] scheme instructs cross-platform loaders (e.g., Coil) to
 * perform raw file reads, bypassing the Android Resource system resolution overhead.
 */
actual fun getPlatformImagePath(fileName: String): String {
    return "file:///android_asset/$fileName"
}

/**
 * Decodes a named asset from the Android AssetManager into a hardware-ready [ImageBitmap].
 * Applies explicit [BitmapFactory.Options] to prevent framework-level rescaling
 * from skewing memory telemetry.
 */
actual suspend fun loadOptimizedImageBitmap(fileName: String): ImageBitmap? =
    withContext(Dispatchers.IO) {
        try {
            val context = ApplicationContextHolder.get()
            context.assets.open(fileName).use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888

                    inScaled = false        // Enforces 1:1 pixel rendering for accuracy.
                    inMutable = true        // Allows in-place filtering without memory-intensive copying.
                    inPremultiplied = true  // Pre-calculates alpha channels to optimize rendering throughput.
                }
                BitmapFactory.decodeStream(inputStream, null, options)?.asImageBitmap()
            }
        } catch (e: Exception) {
            null
        }
    }