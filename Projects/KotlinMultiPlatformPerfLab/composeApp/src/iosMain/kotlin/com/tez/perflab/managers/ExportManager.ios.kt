@file:OptIn(ExperimentalForeignApi::class)

package com.tez.perflab.managers

import kotlinx.cinterop.*
import platform.CoreGraphics.CGRectMake
import platform.Foundation.*
import platform.UIKit.*
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.math.roundToLong

// MARK: - Telemetry Export & I/O Operations

/**
 * Persists a generated CSV payload to the iOS temporary directory.
 * Explicitly utilizes `NSString.create` to guarantee memory-safe bridging for large
 * telemetry datasets, preventing Out-Of-Memory (OOM) crashes during high-throughput tests.
 *
 * @param fileName The target name for the exported CSV file.
 * @param content The raw CSV string payload.
 * @param context Platform-specific context (unused in iOS, required by KMP expect).
 * @return The absolute file path if the write operation succeeds, or null if it fails.
 */
actual suspend fun saveCsvToCache(
    fileName: String,
    content: String,
    context: PlatformContext
): String? {
    val tempDir = NSTemporaryDirectory()
    val filePath = tempDir + fileName

    val nsString = NSString.create(string = content)

    val success = nsString.writeToFile(
        path = filePath,
        atomically = true,
        encoding = NSUTF8StringEncoding,
        error = null
    )

    return if (success) filePath else null
}

// MARK: - Native UI Presentation

/**
 * Triggers the native iOS Share Sheet (UIActivityViewController) to export files.
 *
 * @param context Platform-specific context.
 * @param filePath The absolute path of the file to be shared.
 */
actual fun platformShareFile(context: PlatformContext, filePath: String) {
    val fileUrl = NSURL.fileURLWithPath(filePath)
    val activityController = UIActivityViewController(
        activityItems = listOf(fileUrl),
        applicationActivities = null
    )

    dispatch_async(dispatch_get_main_queue()) {
        val allWindows = UIApplication.sharedApplication.connectedScenes
            .mapNotNull { it as? UIWindowScene }
            .flatMap { it.windows as List<*> }
            .mapNotNull { it as? UIWindow }

        val window: UIWindow? = allWindows.firstOrNull { it.isKeyWindow() }
            ?: allWindows.firstOrNull()
            ?: UIApplication.sharedApplication.keyWindow

        var topController: UIViewController? = window?.rootViewController

        // Verifies if the controller's view is physically attached to a window and is 
        // not currently dismissing, preventing orphaned modal references in the hierarchy.
        while (topController?.presentedViewController != null) {
            val presented = topController.presentedViewController

            val isAttachedToWindow = presented?.view?.window != null
            val isDismissing = presented?.isBeingDismissed() ?: false

            if (isAttachedToWindow && !isDismissing) {
                topController = presented
            } else {
                break
            }
        }

        activityController.modalPresentationStyle = UIModalPresentationPageSheet

        // Popover Layout Engine (Universal iPad/iPhone Support)
        activityController.popoverPresentationController?.let { popover ->
            val sourceView = topController?.view ?: return@let
            popover.sourceView = sourceView

            val bounds = sourceView.bounds
            val midX = bounds.useContents { size.width } / 2.0
            val midY = bounds.useContents { size.height } / 2.0

            popover.sourceRect = CGRectMake(x = midX, y = midY, width = 0.0, height = 0.0)
            popover.permittedArrowDirections = 0u
        }

        topController?.presentViewController(
            viewControllerToPresent = activityController,
            animated = true,
            completion = null
        )
    }
}

// MARK: - Time Synchronization

/**
 * Returns the current Unix epoch time in seconds.
 * Ensures safe Double-to-Long numeric conversion via explicit mathematical rounding.
 */
actual fun getCurrentEpochSeconds(): Long {
    return NSDate().timeIntervalSince1970.roundToLong()
}