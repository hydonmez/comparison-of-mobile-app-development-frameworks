@file:OptIn(ExperimentalForeignApi::class)

package com.tez.perflab.engines

import com.tez.perflab.managers.PlatformContext
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.*
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.NSBundle
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSURL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import platform.darwin.NSObjectProtocol

/**
 * iOS-specific AVFoundation implementation via Kotlin/Native C-Interop.
 * Designed for strict deterministic benchmarking to match ExoPlayer's configuration.
 */
actual object NativeAudioPlayer {

    private var player: AVPlayer? = null

    // Explicit reference retained to prevent Automatic Reference Counting (ARC)
    // from prematurely deallocating observers.
    private var notificationObserver: NSObjectProtocol? = null

    actual suspend fun loadAudio(context: PlatformContext, fileName: String): Boolean = withContext(Dispatchers.Main) {

        // Ensures a clean state before initiating I/O operations.
        release()

        val safeName = fileName.replace(".mp3", "")
        val path = NSBundle.mainBundle.pathForResource(safeName, ofType = "mp3")
            ?: return@withContext false

        val url = NSURL.fileURLWithPath(path)
        val item = AVPlayerItem(url)

        // Constrains OS-level read-ahead buffering to 5.0 seconds. 
        // This enforces a consistent memory footprint, matching Android's DefaultLoadControl.
        item.preferredForwardBufferDuration = 5.0

        player = AVPlayer(item)

        // Forces immediate decoder initialization to accurately measure latency.
        player?.automaticallyWaitsToMinimizeStalling = false

        notificationObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = item,
            queue = platform.Foundation.NSOperationQueue.mainQueue
        ) { _ ->
            AudioEngine.onPlaybackEnded()
        }

        // Apple's KVO is implemented as an Objective-C Category, which Kotlin/Native cannot override.
        // To achieve non-blocking behavior without a Swift wrapper, coroutine suspension (`delay`) is used.
        // This suspends execution gracefully rather than blocking the main thread.
        var attempts = 0
        while (item.status == AVPlayerItemStatusUnknown && attempts < 100) {
            // 50ms interval for initialization tracking.
            delay(50)
            attempts++
        }

        return@withContext item.status == AVPlayerItemStatusReadyToPlay
    }

    actual fun play() { player?.play() }

    actual fun pause() { player?.pause() }

    @OptIn(ExperimentalForeignApi::class)
    actual fun seek(toSeconds: Double) {
        val time = CMTimeMakeWithSeconds(toSeconds, 600)

        // Forces the AVPlayer decoder to resolve exact timestamps
        // rather than snapping to the nearest hardware Keyframe (I-Frame).
        val zeroTime = CMTimeMakeWithSeconds(0.0, 600)
        player?.seekToTime(time, zeroTime, zeroTime)
    }

    /**
     * Cleans up player resources and observers.
     */
    actual fun release() {
        player?.pause()

        notificationObserver?.let {
            NSNotificationCenter.defaultCenter.removeObserver(it)
            notificationObserver = null
        }

        player = null
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun getDurationSeconds(): Double {
        val item = player?.currentItem ?: return 0.0
        val secs = CMTimeGetSeconds(item.duration)
        // Resolves NaN issues during uninitialized buffer states.
        return if (secs.isNaN()) 0.0 else secs
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun getCurrentPositionSeconds(): Double {
        val playerInstance = player ?: return 0.0
        val secs = CMTimeGetSeconds(playerInstance.currentTime())
        return if (secs.isNaN()) 0.0 else secs
    }
}