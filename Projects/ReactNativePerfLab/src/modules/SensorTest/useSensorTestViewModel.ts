import { useState, useCallback, useRef, useEffect } from 'react';
import { Platform } from 'react-native';
import Share from 'react-native-share';
import { SensorTestManager } from '../../core/manager/SensorTestManager';
import { usePerformanceStore, BenchmarkType } from '../../core/manager/PerformanceManager';
import { ExportManager } from '../../core/manager/ExportManager';

// Expose high-resolution monotonic timer for precise telemetry accuracy.
declare const performance: { now: () => number };

/**
 * Sensor Orchestrator
 * Manages high-frequency telemetry state and React updates.
 * Uses a mutable ref (`isRunningRef`) to track execution status reliably 
 * inside interval closures without triggering continuous re-renders.
 */
export const useSensorTestViewModel = () => {
    
    // --- UI State Observables ---
    const [isRunning, setIsRunning] = useState<boolean>(false);
    const [status, setStatus] = useState<string>("Sensors Ready");
    const [isReportReady, setIsReportReady] = useState<boolean>(false);
    const [progress, setProgress] = useState<number>(0);

    // Mutable ref to track execution status reliably inside the interval closure.
    const isRunningRef = useRef<boolean>(false);

    // --- Hoisted Telemetry State (Triggering React Re-renders) ---
    const [accelData, setAccelData] = useState<number[]>([0, 0, 0]);
    const [gyroData, setGyroData] = useState<number[]>([0, 0, 0]);
    const [magnetData, setMagnetData] = useState<number[]>([0, 0, 0]);
    const [stepCount, setStepCount] = useState<number>(0);

    const { startMonitoring, stopMonitoring, currentLogs } = usePerformanceStore();

    const TEST_DURATION = 60.0;
    const timerIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

    /**
     * Terminates the active benchmark, flushes hardware streams, and stops performance logging.
     */
    const stopTest = useCallback((isFinished: boolean = false) => {
        // Use Ref instead of State to guarantee we are reading the latest value.
        if (!isRunningRef.current) return;

        isRunningRef.current = false;
        setIsRunning(false);

        // Hardware teardown
        if (timerIntervalRef.current) {
            clearInterval(timerIntervalRef.current);
            timerIntervalRef.current = null;
        }
        
        SensorTestManager.stopSensors();
        stopMonitoring();

        if (isFinished) {
            setStatus("✅ Test Finalized");
            setProgress(1.0);
            setIsReportReady(true);
        } else {
            setStatus("🛑 Terminated");
            setProgress(0.0);
            setIsReportReady(false);
        }

        // Clear telemetry state for subsequent benchmarks.
        setAccelData([0, 0, 0]);
        setGyroData([0, 0, 0]);
        setMagnetData([0, 0, 0]);
        setStepCount(0);
    }, [stopMonitoring]);

    const updateProgress = useCallback((absoluteStartTime: number) => {
        const now = performance.now();
        const timeElapsed = (now - absoluteStartTime) / 1000.0;
        
        if (timeElapsed >= TEST_DURATION) {
            stopTest(true);
        } else {
            setProgress(Math.min(timeElapsed / TEST_DURATION, 1.0));
        }
    }, [TEST_DURATION, stopTest]);

    const startTest = useCallback(() => {
        if (isRunningRef.current) return;
        
        isRunningRef.current = true;
        setIsRunning(true);
        
        setStatus("Acquiring Telemetry (100Hz Bridge Load)...");
        setIsReportReady(false);
        setProgress(0.0);
        
        startMonitoring(BenchmarkType.MACRO);
        
        SensorTestManager.startSensors(
            (data) => setAccelData(data),
            (data) => setGyroData(data),
            (data) => setMagnetData(data),
            (count) => setStepCount(count)
        );
        
        const absoluteStartTime = performance.now();
        if (timerIntervalRef.current) clearInterval(timerIntervalRef.current);
        
        timerIntervalRef.current = setInterval(() => { 
            updateProgress(absoluteStartTime); 
        }, 100);
        
    }, [startMonitoring, updateProgress]);

    const exportResults = useCallback(async () => {
        try {
            const filePath = await ExportManager.generateCSV(currentLogs, "Sensor_RN_Stress_100Hz");
            
            if (filePath) {
                await Share.open({
                    title: 'Sensor Benchmark Report',
                    url: `file://${filePath}`,
                    type: 'text/csv',
                    filename: 'Sensor_RN_Benchmark_Export.csv',
                    failOnCancel: false
                });
            } else {
                setStatus("❌ Export Failed: No data available");
            }
        } catch (error: any) {
            if (error.message !== 'User did not share') {
                setStatus(`❌ Export Error: ${error.message}`);
            }
        }
    }, [currentLogs]);

    useEffect(() => {
        return () => {
            if (timerIntervalRef.current) clearInterval(timerIntervalRef.current);
            SensorTestManager.stopSensors();
            
            if (isRunningRef.current) {
                isRunningRef.current = false;
                stopMonitoring();
            }
        };
    }, [stopMonitoring]);

    return {
        isRunning, status, isReportReady, progress,
        accelData, gyroData, magnetData, stepCount,
        startTest, stopTest, exportResults
    };
};