package com.tez.perflab.managers

import androidx.compose.runtime.Composable

/**
 * Represents a platform-agnostic execution context.
 *
 * This interface acts as a unified handle for OS-level dependencies.
 * - On Android: Resolves to `android.content.Context`.
 * - On iOS: Resolves to a generic reference (Any?) or a specific `UIViewController`
 *   to allow interaction with system services like `AVPlayer` or `CoreMotion`.
 */
interface PlatformContext

/**
 * A lifecycle-aware Composable provider for the active PlatformContext.
 *
 * Utilizes the 'expect' mechanism to extract the native environment reference
 * directly from the Composable tree, ensuring the context is always bound
 * to the current UI lifecycle and prevents memory leaks during platform transitions.
 */
@Composable
expect fun rememberPlatformContext(): PlatformContext