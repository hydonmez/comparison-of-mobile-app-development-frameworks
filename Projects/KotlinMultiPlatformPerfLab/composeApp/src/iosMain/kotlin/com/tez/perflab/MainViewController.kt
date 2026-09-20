@file:OptIn(ExperimentalForeignApi::class)

package com.tez.perflab

import androidx.compose.ui.window.ComposeUIViewController
import com.tez.perflab.ui.components.OrientationState
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.*

/**
 * Acts as the root native container mapping the Compose Multiplatform UI hierarchy
 * into the iOS ecosystem.
 */
fun MainViewController(): UIViewController = object : UIViewController(null, null) {

    private val composeController = ComposeUIViewController {
        App()
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        addChildViewController(composeController)
        view.addSubview(composeController.view)

        // 1. Initial (Portrait) sizing
        composeController.view.setFrame(view.bounds)
        composeController.didMoveToParentViewController(this)
    }

    /**
     * iOS triggers this method whenever the device rotates (Portrait <-> Landscape).
     * Here, we instruct the Compose canvas to completely fill the newly expanded screen bounds.
     * This eliminates letterboxing and allows the video to be truly full screen.
     */
    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        composeController.view.setFrame(view.bounds)
    }

    val supportedInterfaceOrientations: ULong
        get() = if (OrientationState.isLandscape) {
            UIInterfaceOrientationMaskLandscapeRight
        } else {
            UIInterfaceOrientationMaskPortrait
        }

    val shouldAutorotate: Boolean
        get() = true
}