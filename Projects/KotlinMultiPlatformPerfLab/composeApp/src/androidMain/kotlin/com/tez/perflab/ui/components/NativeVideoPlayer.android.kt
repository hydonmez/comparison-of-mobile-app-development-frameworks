package com.tez.perflab.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.tez.perflab.engines.PlatformMediaPlayer

/**
 * Integrates the native Android Media3 PlayerView into the Compose Multiplatform tree
 * using the AndroidView interoperability API for hardware-accelerated video rendering.
 */
actual @Composable fun NativeVideoPlayer(
    player: PlatformMediaPlayer?,
    modifier: Modifier
) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                this.player = player?.exoPlayer
                useController = true
            }
        },
        update = { view ->
            view.player = player?.exoPlayer
        },
        onRelease = { view ->
            // Detaches the player instance to prevent memory leaks when the view is disposed.
            view.player = null
        },
        modifier = modifier
    )
}