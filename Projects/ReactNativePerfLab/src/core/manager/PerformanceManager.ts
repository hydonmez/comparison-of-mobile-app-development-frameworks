import { create } from 'zustand';
import { NativeModules } from 'react-native';
import { PerformanceLog } from '../../models/PerformanceLog';

// Bridge Safety Layer: Uses lazy access to the native module. 
// Returns null instead of crashing the JS thread if the module is missing.
const TelemetryBridge = NativeModules.HardwareTelemetryModule;

export enum BenchmarkType {
    MACRO = 'MACRO', 
    MICRO = 'MICRO'  
}

interface HardwareTelemetryModule {
    getOptimizedRAMUsage(): Promise<number>;
    getProcessCpuUsage(deltaSeconds: number): Promise<number>;
    getBatteryLevel(): Promise<number>;
    getThermalStateString(): Promise<string>;
    syncCpuBaseline(): Promise<void>;
}

export interface PerformanceState {
    isMeasuring: boolean;
    currentLogs: PerformanceLog[];
    currentFPS: number;
    thermalStateString: string;
    startMonitoring: (type?: BenchmarkType) => void;
    stopMonitoring: () => void;
}

// MARK: - Internal Engine State
let animationFrameId: number | null = null;
let windowStartTimeMs: number = 0;
let testStartTimeMs: number = 0;
let frameCount: number = 0;
let baselineRAM: number = 0;
let currentTestType: BenchmarkType = BenchmarkType.MACRO;
let logsBuffer: PerformanceLog[] = [];

/**
 * Ensures all calls to NativeModules are wrapped in a safety check 
 * to prevent runtime exceptions.
 */
const safeBridgeCall = async <T>(method: keyof HardwareTelemetryModule, args: any[] = [], fallback: T): Promise<T> => {
    if (!TelemetryBridge || typeof TelemetryBridge[method] !== 'function') {
        console.warn(`[PerformanceManager] Native Module method '${method}' not available.`);
        return fallback;
    }
    try {
        return await (TelemetryBridge[method] as Function)(...args);
    } catch (e) {
        console.error(`[PerformanceManager] Error invoking ${method}:`, e);
        return fallback;
    }
};

export const usePerformanceStore = create<PerformanceState>((set, get) => ({
    isMeasuring: false,
    currentLogs: [],
    currentFPS: 0,
    thermalStateString: 'Nominal',

    startMonitoring: async (type: BenchmarkType = BenchmarkType.MACRO) => {
        const state = get();
        if (state.isMeasuring) state.stopMonitoring();

        currentTestType = type;
        logsBuffer = [];
        
        set({
            isMeasuring: true,
            currentLogs: [],
            currentFPS: 0,
            thermalStateString: 'Nominal'
        });

        // Initialize Baseline RAM safely
        baselineRAM = await safeBridgeCall<number>('getOptimizedRAMUsage', [], 0);
        await safeBridgeCall<void>('syncCpuBaseline', [], undefined);

        windowStartTimeMs = 0;
        testStartTimeMs = 0;
        frameCount = 0;

        animationFrameId = requestAnimationFrame(onFrameTick);
    },

    stopMonitoring: () => {
        set({ isMeasuring: false });
        
        if (animationFrameId !== null) {
            cancelAnimationFrame(animationFrameId);
            animationFrameId = null;
        }

        set({ currentLogs: [...logsBuffer] });

        windowStartTimeMs = 0;
        testStartTimeMs = 0;
        frameCount = 0;
    }
}));

const onFrameTick = (timestampMs: number) => {
    const state = usePerformanceStore.getState();
    if (!state.isMeasuring) return;

    if (windowStartTimeMs === 0) {
        windowStartTimeMs = timestampMs;
        testStartTimeMs = timestampMs;
        animationFrameId = requestAnimationFrame(onFrameTick);
        return;
    }

    frameCount++;

    const deltaMs = timestampMs - windowStartTimeMs;
    const deltaSeconds = deltaMs / 1000.0;
    const totalElapsedMs = timestampMs - testStartTimeMs;
    const totalElapsedSeconds = totalElapsedMs / 1000.0;

    const targetInterval = currentTestType === BenchmarkType.MICRO ? 0.25 : 1.0;
    const warmupThreshold = currentTestType === BenchmarkType.MICRO ? 0.0 : 2.0;

    if (deltaSeconds >= targetInterval) {
        const snapshotFPS = Math.round(frameCount / deltaSeconds);
        usePerformanceStore.setState({ currentFPS: snapshotFPS });

        frameCount = 0;
        windowStartTimeMs = timestampMs;

        if (totalElapsedSeconds >= warmupThreshold) {
            captureMetricsInBackground(snapshotFPS, deltaSeconds).catch(console.error);
        } else {
            safeBridgeCall<void>('syncCpuBaseline', [], undefined);
        }
    }

    animationFrameId = requestAnimationFrame(onFrameTick);
};

/**
 * Fetches hardware metrics safely in the background.
 */
const captureMetricsInBackground = async (snapshotFPS: number, deltaSeconds: number) => {
    // Fetch metrics safely
    const rawRAM = await safeBridgeCall<number>('getOptimizedRAMUsage', [], 0);
    const cpuUsage = await safeBridgeCall<number>('getProcessCpuUsage', [deltaSeconds], 0);
    const battery = await safeBridgeCall<number>('getBatteryLevel', [], 0);
    const thermal = await safeBridgeCall<string>('getThermalStateString', [], 'Nominal');

    const netRAM = Math.max(0.0, rawRAM - baselineRAM);

    const log: PerformanceLog = {
        timestampMillis: Date.now(),
        id: Date.now(),
        cpuUsage,
        rawRAM,
        netRAM,
        batteryLevel: battery,
        fps: snapshotFPS,
        thermalState: thermal
    };

    logsBuffer.push(log);
    
    // Update reactive UI
    usePerformanceStore.setState({ thermalStateString: thermal });
};