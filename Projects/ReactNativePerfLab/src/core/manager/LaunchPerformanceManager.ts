import { create } from 'zustand';
import { NativeModules, NativeEventEmitter } from 'react-native';

/**
 * Launch Telemetry Engine
 * Engineered to measure application launch latency.
 */
declare const performance: { now: () => number };
const { LaunchPerformanceNative } = NativeModules;
const NATIVE_OS_START_EPOCH_MS: number = LaunchPerformanceNative?.osStartEpochMs || Date.now();

/**
 * Generates high-resolution timestamps. 
 * Anchors performance.now() to Date.now() at boot to decode timestamps from iOS and Android.
 */
const jsBootEpochMs = Date.now();
const jsBootPerf = performance.now();

function getHighResEpochMs(): number {
    return jsBootEpochMs + (performance.now() - jsBootPerf);
}

export interface LaunchPerformanceState {
    totalColdStartMs: number;
    osDurationMs: number;
    softwareDurationMs: number;
    hotStartMs: number;

    appStarted: () => void;
    osReady: () => void;
    reportBenchmark: () => void;
    hotStartDetected: () => void;
}

// MARK: - Internal Timestamps (Non-Reactive)
let startTimeMs: number | null = null;
let osReadyTimeMs: number | null = null;
let hotStartWakeTimeMs: number | null = null;
let isColdStartReported: boolean = false;

// MARK: - Native Telemetry Listener
const eventEmitter = LaunchPerformanceNative 
    ? new NativeEventEmitter(LaunchPerformanceNative) 
    : null;

// Add listener only if the module is loaded successfully
if (eventEmitter) {
    eventEmitter.addListener('onHotStartInitiated', (nativeHotStartEpochMs: number) => {
        /**
         * Ignores OS resume events until the initial cold start phase has concluded.
         */
        if (isColdStartReported) {
            hotStartWakeTimeMs = nativeHotStartEpochMs;
        }
    });
}

export const useLaunchPerformanceStore = create<LaunchPerformanceState>((set) => ({
    
    totalColdStartMs: 0,
    osDurationMs: 0,
    softwareDurationMs: 0,
    hotStartMs: 0,

    // MARK: - Cold Start Tracking

    appStarted: () => {
        startTimeMs = NATIVE_OS_START_EPOCH_MS;
    },

    osReady: () => {
        osReadyTimeMs = getHighResEpochMs();
    },

    reportBenchmark: () => {
        if (isColdStartReported || startTimeMs === null || osReadyTimeMs === null) return;
        
        const now = getHighResEpochMs();

        set({
            osDurationMs: osReadyTimeMs - startTimeMs,
            softwareDurationMs: now - osReadyTimeMs,
            totalColdStartMs: now - startTimeMs,
        });

        isColdStartReported = true;
        startTimeMs = null;
        osReadyTimeMs = null;
    },

    // MARK: - Hot Start Tracking

    hotStartDetected: () => {
        if (!isColdStartReported || hotStartWakeTimeMs === null) return;

        const now = getHighResEpochMs();
        
        // Prevents negative telemetry values due to sub-millisecond bridge execution
        const calculatedHotStart = Math.max(0.1, now - hotStartWakeTimeMs);

        set({ hotStartMs: calculatedHotStart });
        hotStartWakeTimeMs = null;
    }
}));

// MARK: - View Rendering Optimizations
export const getFormattedLaunchMetrics = (state: LaunchPerformanceState) => ({
    formattedColdStart: state.totalColdStartMs.toFixed(1),
    formattedOSDuration: state.osDurationMs.toFixed(1),
    formattedUIDuration: state.softwareDurationMs.toFixed(1),
    formattedHotStart: state.hotStartMs > 0 ? state.hotStartMs.toFixed(1) : "--",
});