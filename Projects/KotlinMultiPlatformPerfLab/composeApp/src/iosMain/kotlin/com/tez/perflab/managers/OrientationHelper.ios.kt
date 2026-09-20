@file:OptIn(ExperimentalForeignApi::class)

package com.tez.perflab.managers

import com.tez.perflab.ui.components.OrientationState
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.*

actual fun lockScreenOrientation(context: PlatformContext, isLandscape: Boolean) {

    // Updates the shared orientation state, which is observed by the AppDelegate.
    OrientationState.isLandscape = isLandscape

    val windowScene = UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .firstOrNull { it.activationState == UISceneActivationStateForegroundActive }
        ?: return

    // Invalidates the orientation cache, forcing the OS to re-evaluate supported interface orientations.
    windowScene.keyWindow?.rootViewController?.setNeedsUpdateOfSupportedInterfaceOrientations()

    // Hardware rotation request (iOS 16+)
    val targetMask = if (isLandscape) {
        UIInterfaceOrientationMaskLandscapeRight
    } else {
        UIInterfaceOrientationMaskPortrait
    }

    val preferences = UIWindowSceneGeometryPreferencesIOS().apply {
        interfaceOrientations = targetMask
    }

    // The OS will accept this request since the AppDelegate now aligns with the target mask.
    windowScene.requestGeometryUpdateWithPreferences(preferences) { error ->
        if (error != null) {
            println("❌ Hardware Rotation Error: ${error.localizedDescription}")
        }
    }
}