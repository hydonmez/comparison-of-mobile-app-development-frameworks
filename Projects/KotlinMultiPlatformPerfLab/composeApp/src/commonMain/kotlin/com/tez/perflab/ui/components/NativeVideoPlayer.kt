package com.tez.perflab.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tez.perflab.engines.PlatformMediaPlayer

/**
 * Contract for a platform-specific hardware-accelerated video rendering surface.
 *
 * Ensures the common UI layer can request a high-performance video surface without
 * intrinsic knowledge of the underlying OS implementation (ExoPlayer via `AndroidView`
 * vs AVPlayerLayer via `UIKitView`).
 *
 * By delegating the `Modifier` to the actual implementation, Compose maintains total
 * control over the layout math (Measurement/Placement), while delegating the pixel-pushing
 * directly to the hardware decoders (MediaCodec / VideoToolbox). This prevents expensive
 * memory copying across the native boundaries.
 */
@Composable
expect fun NativeVideoPlayer(
    player: PlatformMediaPlayer?,
    modifier: Modifier = Modifier
)