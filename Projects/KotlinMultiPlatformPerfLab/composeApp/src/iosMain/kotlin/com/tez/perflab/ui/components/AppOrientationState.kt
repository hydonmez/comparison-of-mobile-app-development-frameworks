package com.tez.perflab.ui.components

/**
 * A global configuration state read by the iOS native layer (AppDelegate) 
 * to enforce hardware orientation policies.
 */
object OrientationState {
    var isLandscape: Boolean = false
}