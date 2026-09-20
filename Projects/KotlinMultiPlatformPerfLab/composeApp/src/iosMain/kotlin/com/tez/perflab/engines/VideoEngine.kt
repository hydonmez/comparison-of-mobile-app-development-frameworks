@file:OptIn(ExperimentalForeignApi::class)

package com.tez.perflab.engines

import com.tez.perflab.managers.PlatformContext
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.*
import platform.CoreMedia.CMTimeMake
import platform.Foundation.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.darwin.NSObjectProtocol


// iOS native AVPlayer wrapper
@Suppress("unused")
actual class PlatformMediaPlayer(val avPlayer: AVPlayer)

/**
 * iOS-specific implementation of the hardware-accelerated VideoEngine.
 * Mirrors native environments to ensure consistent memory footprint management, 
 * read-ahead buffer constraints, and decoder initialization.
 */
actual object VideoEngine {
    private var _player: AVPlayer? = null
    private var _platformPlayer: PlatformMediaPlayer? = null

    private var notificationObserver: NSObjectProtocol? = null

    private val _hasEnded = MutableStateFlow(false)
    actual val hasEnded: StateFlow<Boolean> = _hasEnded.asStateFlow()

    actual fun getPlayer(): PlatformMediaPlayer? = _platformPlayer

    actual fun prepareVideo(context: PlatformContext, fileName: String, extension: String) {

        // Routes audio to the playback category to ensure video sound plays 
        // even if the physical silent switch is engaged on the device.
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, error = null)
        session.setActive(true, error = null)

        val bundle = NSBundle.mainBundle
        val path = bundle.pathForResource(fileName, ofType = extension) ?: return
        val url = NSURL.fileURLWithPath(path)

        val item = AVPlayerItem(url)

        // Configures the preferred forward buffer duration to 5.0 seconds.
        item.preferredForwardBufferDuration = 5.0

        val currentPlayer = _player
        if (currentPlayer != null) {
            currentPlayer.replaceCurrentItemWithPlayerItem(item)
        } else {
            val newPlayer = AVPlayer(item)

            newPlayer.automaticallyWaitsToMinimizeStalling = false

            _player = newPlayer
            _platformPlayer = PlatformMediaPlayer(newPlayer)
        }

        clearObserver()

        notificationObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = item,
            queue = NSOperationQueue.mainQueue
        ) { _ ->
            _hasEnded.value = true
        }
    }

    actual fun playFromStart() {
        _hasEnded.value = false
        _player?.seekToTime(CMTimeMake(0, 1))
        _player?.play()
    }

    actual fun stop() {
        _player?.pause()
    }

    /**
     * Cleans up player resources and observers.
     */
    actual fun release() {
        _player?.pause()
        _player?.replaceCurrentItemWithPlayerItem(null)
        clearObserver()
        _player = null
        _platformPlayer = null
        _hasEnded.value = false
    }

    private fun clearObserver() {
        notificationObserver?.let {
            NSNotificationCenter.defaultCenter.removeObserver(it)
            notificationObserver = null
        }
    }
}