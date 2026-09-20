@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.tez.perflab.managers

import kotlinx.cinterop.*
import platform.darwin.*
import platform.Foundation.*
import platform.QuartzCore.*
import platform.UIKit.UIDevice
import platform.posix.*
import kotlin.math.round

/**
 * iOS actual implementation of the hardware profiler for Kotlin Multiplatform.
 * Uses Mach Thread Summation for CPU tracking and strict vm_deallocate mapping
 * to prevent memory leaks.
 */
actual object HardwareProfiler {

    private var displayLink: CADisplayLink? = null
    private var displayLinkProxy: DisplayLinkProxy? = null

    // MARK: - FPS TRACKER & SCHEDULER (CADisplayLink)

    actual fun startFpsTracker(
        context: PlatformContext,
        type: BenchmarkType,
        onTick: (fps: Int, deltaSeconds: Double, totalElapsedSeconds: Double) -> Unit
    ) {
        if (displayLink != null) stopFpsTracker()

        // Enable battery monitoring at initialization
        UIDevice.currentDevice.batteryMonitoringEnabled = true

        val targetInterval = if (type == BenchmarkType.MICRO) 0.25 else 1.0
        displayLinkProxy = DisplayLinkProxy(targetInterval, onTick)

        displayLink = CADisplayLink.displayLinkWithTarget(
            target = displayLinkProxy!!,
            selector = NSSelectorFromString("onFrameTick:")
        )

        displayLink?.addToRunLoop(NSRunLoop.mainRunLoop, NSRunLoopCommonModes)
    }

    actual fun stopFpsTracker() {
        UIDevice.currentDevice.batteryMonitoringEnabled = false
        displayLinkProxy?.invalidate()
        displayLink?.invalidate()
        displayLink = null
        displayLinkProxy = null
    }

    // MARK: - CPU UTILIZATION (Stateless Mach Thread Summation)

    /**
     * Calculates absolute CPU percentage by iterating over all active Mach threads.
     */
    actual fun getCpuUsage(deltaSeconds: Double): Double = memScoped {
        val threadList = alloc<thread_act_array_tVar>()
        val threadCount = alloc<mach_msg_type_number_tVar>()

        val kr = task_threads(mach_task_self_, threadList.ptr, threadCount.ptr)
        if (kr != KERN_SUCCESS) return 0.0

        var totalCpuUsage = 0.0
        val threads = threadList.value

        if (threads != null) {
            val count = threadCount.value.toInt()

            // Allocates thread information structures once outside the loop 
            // to reuse stack memory and prevent allocation overhead.
            val threadInfo = alloc<thread_basic_info>()
            val threadInfoCount = alloc<mach_msg_type_number_tVar>()

            for (i in 0 until count) {
                val threadId = threads[i]

                // Resets the struct size for each thread before querying the kernel.
                threadInfoCount.value = (sizeOf<thread_basic_info>() / sizeOf<integer_tVar>()).toUInt()

                val infoResult = thread_info(
                    threadId,
                    THREAD_BASIC_INFO.toUInt(),
                    threadInfo.ptr.reinterpret(),
                    threadInfoCount.ptr
                )

                if (infoResult == KERN_SUCCESS) {
                    // Exclude idle threads to get active usage only
                    val isIdle = (threadInfo.flags.toInt() and TH_FLAGS_IDLE) != 0
                    if (!isIdle) {
                        totalCpuUsage += (threadInfo.cpu_usage.toDouble() / TH_USAGE_SCALE.toDouble()) * 100.0
                    }
                }
            }

            // Prevent memory leaks by deallocating the thread list C-Pointer
            val size = (count * sizeOf<thread_tVar>()).toULong()
            vm_deallocate(
                mach_task_self_,
                threads.rawValue.toLong().toULong(),
                size
            )
        }

        return round(totalCpuUsage * 10.0) / 10.0
    }

    /**
     * Priming is not necessary for Mach Thread Summation.
     * Kept empty to satisfy existing 'actual' declarations.
     */
    actual fun primeCpu() {
        // No-op
    }

    // MARK: - MEMORY UTILIZATION (Physical Footprint)

    actual fun getRamUsage(): Double = memScoped {
        val info = alloc<task_vm_info_data_t>()
        val count = alloc<mach_msg_type_number_tVar>()
        count.value = (sizeOf<task_vm_info_data_t>() / sizeOf<integer_tVar>()).toUInt()

        val result = task_info(
            mach_task_self_,
            TASK_VM_INFO.toUInt(),
            info.ptr.reinterpret(),
            count.ptr
        )

        return if (result == KERN_SUCCESS) {
            val footprintMb = info.phys_footprint.toDouble() / (1024.0 * 1024.0)
            round(footprintMb * 10.0) / 10.0
        } else {
            0.0
        }
    }

    // MARK: - BATTERY & THERMAL

    actual fun getBatteryLevel(): Double {
        val level = UIDevice.currentDevice.batteryLevel
        return if (level >= 0.0f) round(level.toDouble() * 100.0 * 10.0) / 10.0 else 0.0
    }

    actual fun getThermalState(): String {
        return when (NSProcessInfo.processInfo.thermalState) {
            NSProcessInfoThermalState.NSProcessInfoThermalStateNominal -> "Nominal"
            NSProcessInfoThermalState.NSProcessInfoThermalStateFair -> "Fair"
            NSProcessInfoThermalState.NSProcessInfoThermalStateSerious -> "Serious"
            NSProcessInfoThermalState.NSProcessInfoThermalStateCritical -> "Critical"
            else -> "Nominal"
        }
    }
}

/**
 * Proxy class to bridge CADisplayLink Objective-C selector to Kotlin callbacks.
 * Encapsulates the dynamic interval logic and prevents memory leaks.
 */
private class DisplayLinkProxy(
    private val targetInterval: Double,
    private var tickCallback: ((Int, Double, Double) -> Unit)?
) : NSObject() {

    private var lastMetricCaptureTime: Double = 0.0
    private var testStartTime: Double = 0.0
    private var frameCount: Int = 0
    private var isInvalidated = false

    fun invalidate() {
        isInvalidated = true
        tickCallback = null
    }

    @Suppress("unused")
    @ObjCAction
    fun onFrameTick(displayLink: CADisplayLink) {
        if (isInvalidated || tickCallback == null) return

        val currentTimestamp = displayLink.timestamp

        if (lastMetricCaptureTime == 0.0) {
            lastMetricCaptureTime = currentTimestamp
            testStartTime = currentTimestamp
            return
        }

        frameCount++
        val deltaFromLastCapture = currentTimestamp - lastMetricCaptureTime
        val totalElapsedSeconds = currentTimestamp - testStartTime

        if (deltaFromLastCapture >= targetInterval) {
            val snapshotFPS = (frameCount.toDouble() / deltaFromLastCapture).toInt()

            tickCallback?.invoke(snapshotFPS, deltaFromLastCapture, totalElapsedSeconds)

            frameCount = 0
            lastMetricCaptureTime = currentTimestamp
        }
    }
}