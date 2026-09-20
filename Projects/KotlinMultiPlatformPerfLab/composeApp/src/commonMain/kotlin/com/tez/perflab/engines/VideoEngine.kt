package com.tez.perflab.engines

import com.tez.perflab.managers.PlatformContext
import kotlinx.coroutines.flow.StateFlow

/**
 * Cross-platform type representing the native media player instance.
 * Maps to ExoPlayer on Android and AVPlayer on iOS for UI integration.
 */
expect class PlatformMediaPlayer

/**
 * Platform-agnostic video playback engine configured with custom buffer constraints.
 */
expect object VideoEngine {

    /**
     * Tracks whether video playback has reached the end of the file.
     */
    val hasEnded: StateFlow<Boolean>

    /**
     * Retrieves the active native player instance, or null if uninitialized.
     */
    fun getPlayer(): PlatformMediaPlayer?

    /**
     * Prepares the video asset with configured buffer constraints for playback.
     */
    fun prepareVideo(context: PlatformContext, fileName: String, extension: String)

    /**
     * Resets playback head to the beginning and starts playback.
     */
    fun playFromStart()

    /**
     * Pauses playback while retaining the current frame.
     */
    fun stop()

    /**
     * Releases the player instance and associated resources.
     */
    fun release()
}