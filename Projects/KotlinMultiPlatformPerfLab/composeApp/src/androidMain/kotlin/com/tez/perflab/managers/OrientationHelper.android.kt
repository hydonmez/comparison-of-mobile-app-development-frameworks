package com.tez.perflab.managers

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Controls the hardware orientation and immersive UI state for the Android platform.
 * Enforces strict layout constraints to prevent system UI interruptions during execution.
 */
actual fun lockScreenOrientation(context: PlatformContext, isLandscape: Boolean) {
    val activity = (context as AndroidPlatformContext).androidContext as? Activity ?: return
    val window = activity.window ?: return
    val controller = WindowInsetsControllerCompat(window, window.decorView)

    if (isLandscape) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        WindowCompat.setDecorFitsSystemWindows(window, false)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        WindowCompat.setDecorFitsSystemWindows(window, true)
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}