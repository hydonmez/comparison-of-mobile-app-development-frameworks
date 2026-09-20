package com.tez.perflab.managers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * iOS-specific implementation of the PlatformContext interface.
 * Acts as a lightweight placeholder to maintain compatibility with the Android ecosystem, 
 * where a concrete application context is required for OS-level operations.
 */
class IOSPlatformContext : PlatformContext

/**
 * Instantiates and caches the iOS platform context within the Compose composition tree.
 * The `remember` block ensures the object is allocated exactly once, preventing
 * unnecessary Garbage Collection (GC) overhead during recomposition.
 */
@Composable
actual fun rememberPlatformContext(): PlatformContext {
    return remember { IOSPlatformContext() }
}