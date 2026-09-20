package com.reactnativeperflab

import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.modules.core.DeviceEventManagerModule

/**
 * Launch Performance Module
 * A native module used to export OS timestamps.
 * Implements LifecycleEventListener to safely capture Hot Start events without 
 * causing race conditions during the React Native Bridge initialization phase.
 */
class LaunchPerformanceModule(reactContext: ReactApplicationContext) : 
    ReactContextBaseJavaModule(reactContext), LifecycleEventListener {

    init {
        reactContext.addLifecycleEventListener(this)
    }

    override fun getName(): String = "LaunchPerformanceNative"

    override fun getConstants(): MutableMap<String, Any> {
        val constants = HashMap<String, Any>()
        // Export the high-precision decimal timestamp
        constants["osStartEpochMs"] = MainApplication.osStartEpochMs
        return constants
    }

    /**
     * Triggered on Activity.onResume(). Safely executes only when the React Context 
     * is fully initialized, emitting the high-resolution resume timestamp to the JS thread.
     */
    override fun onHostResume() {
        val hotStartEpochMs = MainApplication.getHighResEpochMs()
        
        reactApplicationContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit("onHotStartInitiated", hotStartEpochMs)
    }

    override fun onHostPause() {}
    override fun onHostDestroy() {}
}