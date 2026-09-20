import { useState, useCallback, useEffect, useRef } from 'react';
import Share from 'react-native-share';
import { useAudioEngine, AudioEngine } from '../../core/engine/AudioEngine';
import { usePerformanceStore, BenchmarkType } from '../../core/manager/PerformanceManager';
import { ExportManager } from '../../core/manager/ExportManager';

// Zero-allocation time formatter
export const formatTime = (time: number): string => {
    if (Number.isNaN(time) || !Number.isFinite(time)) return "00:00";
    const minutes = Math.floor(time / 60);
    const seconds = Math.floor(time % 60);
    const minStr = minutes < 10 ? `0${minutes}` : `${minutes}`;
    const secStr = seconds < 10 ? `0${seconds}` : `${seconds}`;
    return `${minStr}:${secStr}`;
};

export const useAudioTestViewModel = () => {
    
    // MARK: - Reactive UI State
    const [isTesting, setIsTesting] = useState(false);
    const [isAudioLoaded, setIsAudioLoaded] = useState(false);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);

    // Ref used strictly for safe asynchronous closures to avoid stale state in timeouts.
    const isTestingRef = useRef(false);

    // Select only 'isPlaying' to prevent the Zustand hook from triggering a 
    // full re-render during 4Hz polling updates.
    const isPlaying = useAudioEngine(state => state.isPlaying);

    const loadTestAudio = useCallback(async () => {
        try {
            const assetPayload = require('../../assets/test_audio_high.mp3');
            const success = await AudioEngine.loadAudio(assetPayload);
            setIsAudioLoaded(success);
            
            if (!success) {
                setErrorMessage("Resource Initialization Error: 'test_audio_high.mp3' missing.");
            }
        } catch (error) {
            console.error("[AudioTestViewModel] Asset Resolution Error:", error);
            setErrorMessage("I/O Error: Ensure 'test_audio_high.mp3' is bundled correctly.");
        }
    }, []);

    useEffect(() => {
        loadTestAudio();
        return () => {
            AudioEngine.release();
            usePerformanceStore.getState().stopMonitoring();
        };
    }, [loadTestAudio]);

    /**
     * State sync and export execution.
     */
    const stopTest = useCallback(async (isFinished: boolean = false) => {
        setIsTesting(false);
        isTestingRef.current = false;
        setErrorMessage(null);

        // Fire and forget, prevents Native UI deadlock.
        AudioEngine.pause().catch(err => console.warn(err));

        // 1. Stop telemetry (Freezes the buffer and updates the state)
        usePerformanceStore.getState().stopMonitoring();

        // Fetch fresh state. Zustand state is immutable, so we must retrieve logs 
        // directly from the latest state after stopping.
        const logs = usePerformanceStore.getState().currentLogs;

        if (!logs || logs.length === 0) {
            setErrorMessage("⚠️ Warning: Benchmark duration too short. (MACRO requires 2s warm-up).");
            return;
        }

        // Yield the JS thread to allow React to update the UI (isTesting = false).
        await new Promise<void>(resolve => setTimeout(resolve, 50));

        // 3. I/O Export Execution
        try {
            const engineState = useAudioEngine.getState();
            const safeDuration = Number.isFinite(engineState.totalDuration) ? engineState.totalDuration : 0;
            const summary = `Total_Audio_Duration_Tested,${safeDuration.toFixed(2)},,,,`;
            const testName = isFinished ? "Audio_RN_Complete" : "Audio_RN_Partial";

            const filePath = await ExportManager.generateCSV(
                logs,
                testName,
                summary
            );

            if (filePath) {
                await Share.open({
                    url: `file://${filePath}`,
                    type: 'text/csv',
                    failOnCancel: false
                });
            } else {
                setErrorMessage("I/O Failure: CSV generation returned null.");
            }
        } catch (error: any) {
            if (error.message !== 'User did not share') {
                setErrorMessage(`❌ Export Failed: ${error.message}`);
            }
        }
    }, []);

    const startTest = useCallback(async () => {
        const state = useAudioEngine.getState();
        
        if (!isAudioLoaded || isTestingRef.current) return;

        if (state.currentTime >= state.totalDuration - 0.1) {
            await AudioEngine.seek(0);
        }

        await AudioEngine.play();
        
        setIsTesting(true);
        isTestingRef.current = true;
        setErrorMessage(null);

        // Delay monitoring to prevent the initial decoder and bridge initialization 
        // spikes from skewing CPU metrics.
        setTimeout(() => {
            if (isTestingRef.current) {
                usePerformanceStore.getState().startMonitoring(BenchmarkType.MACRO);
            }
        }, 100);

    }, [isAudioLoaded]);

    const seekAudio = useCallback(async (to: number) => {
        if (!isAudioLoaded) return;
        await AudioEngine.seek(to);
        
        // Transient read bypassing React reactivity.
        const state = useAudioEngine.getState();
        if (isTestingRef.current && state.totalDuration > 0 && to >= (state.totalDuration - 0.2)) {
            stopTest(true);
        }
    }, [isAudioLoaded, stopTest]);

    const skip = useCallback(async (by: number) => {
        if (!isAudioLoaded) return;
        await AudioEngine.skip(by);
        
        const state = useAudioEngine.getState();
        
        // Predict the target timestamp instead of relying on the polled Zustand state 
        // to avoid missing the EOF boundary.
        const predictedTime = state.currentTime + by;
        
        if (isTestingRef.current && state.totalDuration > 0 && predictedTime >= (state.totalDuration - 0.2)) {
            stopTest(true);
        }
    }, [isAudioLoaded, stopTest]);

    /**
     * EOF Observer: Transient Subscription.
     * Subscribes directly to the Zustand store outside the React render cycle to 
     * maintain 0% UI thread overhead during benchmarking.
     */
    useEffect(() => {
        const unsubscribe = useAudioEngine.subscribe((state) => {
            if (!state.isPlaying && isTestingRef.current && state.totalDuration > 0) {
                if (state.currentTime >= state.totalDuration - 0.2) {
                    stopTest(true);
                }
            }
        });

        return () => {
            unsubscribe();
        };
    }, [stopTest]);

    return {
        // Essential UI States
        isTesting,
        isAudioLoaded,
        errorMessage,
        isPlaying,
        
        // Actions
        startTest,
        stopTest,
        seekAudio,
        skip
    };
};