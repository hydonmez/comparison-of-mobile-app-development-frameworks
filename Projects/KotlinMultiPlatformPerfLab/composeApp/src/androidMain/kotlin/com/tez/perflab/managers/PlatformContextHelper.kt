package com.tez.perflab.managers

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Concrete Android implementation of the cross-platform context interface.
 * Wraps the native Android Context for usage within the agnostic KMP domain.
 */
class AndroidPlatformContext(val androidContext: Context) : PlatformContext

/**
 * Retrieves and caches the platform-specific context within the Compose composition.
 * Utilizes 'remember' to prevent redundant object allocations during recomposition cycles.
 */
actual @Composable fun rememberPlatformContext(): PlatformContext {
    val context = LocalContext.current
    return remember(context) { AndroidPlatformContext(context) }
}