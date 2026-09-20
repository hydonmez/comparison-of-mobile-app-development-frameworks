package com.reactnativeperflab

import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import kotlinx.coroutines.*

/**
 * Native Storage Benchmark Module
 * Acts as the control layer between JavaScript and the physical I/O engine.
 */
class NativeStorageBenchmarkModule(reactContext: ReactApplicationContext) : 
    ReactContextBaseJavaModule(reactContext) {

    private var activeJob: Job? = null
    
    // Maintains the last emitted progress to throttle Bridge events.
    private var lastEmittedProgress: Double = -1.0

    override fun getName(): String {
        return "NativeStorageBenchmark"
    }

    private fun sendProgressUpdate(progress: Double) {
        // Throttles signal traffic to 1% increments.
        // Prevents UI thread lock-up by reducing the number of Native-to-JS calls.
        if (progress - lastEmittedProgress >= 0.01 || progress >= 1.0) {
            lastEmittedProgress = progress
            reactApplicationContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("onStorageProgress", progress)
        }
    }

    @ReactMethod
    fun writeData(megabytes: Int, promise: Promise) {
        activeJob?.cancel() 
        lastEmittedProgress = -1.0 // Reset baseline before benchmarking
        
        activeJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                // Delegates binary allocation strictly to the native JVM heap
                StorageEngine.writeData(reactApplicationContext, megabytes) { progress ->
                    sendProgressUpdate(progress)
                }
                promise.resolve(StorageEngine.securityChecksum)
            } catch (e: CancellationException) {
                promise.reject("CANCELLED", "Task was cancelled programmatically.")
            } catch (e: Exception) {
                promise.reject("WRITE_ERROR", e.localizedMessage)
            }
        }
    }

    @ReactMethod
    fun readData(promise: Promise) {
        activeJob?.cancel()
        lastEmittedProgress = -1.0
        
        activeJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                StorageEngine.readData(reactApplicationContext) { progress ->
                    sendProgressUpdate(progress)
                }
                promise.resolve(StorageEngine.securityChecksum)
            } catch (e: CancellationException) {
                promise.reject("CANCELLED", "Task was cancelled programmatically.")
            } catch (e: Exception) {
                promise.reject("READ_ERROR", e.localizedMessage)
            }
        }
    }

    @ReactMethod
    fun cancelTask() {
        activeJob?.cancel()
    }
}