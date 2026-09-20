import { useState, useEffect, useCallback, useRef } from 'react';
import Share from 'react-native-share';

import JsonTestManager from '../../core/manager/JsonTestManager';
import { usePerformanceStore, BenchmarkType } from '../../core/manager/PerformanceManager';
import { ExportManager } from '../../core/manager/ExportManager';

declare const performance: { now: () => number };

/**
 * Event-Loop Orchestrator for JSON deserialization benchmark. 
 * Accumulates execution time using performance.now() strictly around the synchronous 
 * parsing block to isolate pure computational latency. Employs setTimeout to manually 
 * yield the macro-task queue, preventing UI starvation.
 */
export const useJsonTestViewModel = () => {
    // --- Reactive UI State ---
    const [status, setStatus] = useState<string>("Ready");
    const [isRunning, setIsRunning] = useState<boolean>(false);
    const [isReportReady, setIsReportReady] = useState<boolean>(false);
    const [parsedCount, setParsedCount] = useState<number>(0);

    // --- Telemetry Store Actions ---
    const startMonitoring = usePerformanceStore(state => state.startMonitoring);
    const stopMonitoring = usePerformanceStore(state => state.stopMonitoring);
    const currentLogs = usePerformanceStore(state => state.currentLogs);

    const isRunningRef = useRef<boolean>(false);
    const ITERATIONS = 200;

    // --- Benchmark Pipeline ---

    const startBenchmark = useCallback(async () => {
        if (isRunningRef.current) return;

        isRunningRef.current = true;
        setIsRunning(true);
        setIsReportReady(false);
        setParsedCount(0);
        
        try {
            // Deferred I/O loading
            setStatus("Loading Payload into RAM...");
            await JsonTestManager.preloadDataOnce();

            setStatus("Initiating In-Memory JSON Benchmark...");
            
            // Macro telemetry profile
            startMonitoring(BenchmarkType.MACRO);

            // JIT Warm-up: Execute a synchronous dry-run to initialize caches and bridge bindings.
            JsonTestManager.runParseTest();

            setStatus("Parsing Data Structure...");
            
            // Yield to flush the initial state to the screen
            await new Promise<void>(resolve => setTimeout(resolve, 50));

            let localTotalItems = 0;
            let totalCpuTimeMs = 0; // Tracks pure CPU latency, excluding yield delays

            for (let i = 1; i <= ITERATIONS; i++) {
                // 1. Exact Metric Isolation
                const tickStart = performance.now();
                
                // Strictly synchronous parsing.
                const count = JsonTestManager.runParseTest();
                
                totalCpuTimeMs += (performance.now() - tickStart); 
                localTotalItems += count;

                // 2. JS Thread Yielding. Defers execution to allow the VM to process 
                // VSync signals and UI updates.
                await new Promise<void>(resolve => setTimeout(resolve, 0));

                // 3. Render Throttling
                if (i % 10 === 0) {
                    const progress = Math.floor((i / ITERATIONS) * 100);
                    setStatus(`Processing: ${progress}%`);
                }
            }

            // Calculate duration strictly based on CPU execution time
            const durationSecs = totalCpuTimeMs / 1000.0;
            setParsedCount(localTotalItems);
            
            const formattedTime = Math.round(durationSecs * 1000) / 1000.0;
            setStatus(`✅ Completed in ${formattedTime} sec`);
            
            finishTest(true);

        } catch (error) {
            const errorMessage = error instanceof Error ? error.message : "Execution failed";
            setStatus(`❌ Error: ${errorMessage}`);
            finishTest(false);
        }
    }, [startMonitoring]);

    const finishTest = useCallback((isSuccess: boolean) => {
        stopMonitoring();
        setIsRunning(false);
        isRunningRef.current = false;
        
        if (isSuccess) setIsReportReady(true);
    }, [stopMonitoring]);

    // --- Telemetry Export ---

    const exportResults = useCallback(async () => {
        try {
            const summary = `Total_Parsed_Items,${parsedCount},,,,`;
            const filePath = await ExportManager.generateCSV(
                currentLogs, 
                "JSON_Benchmark_ReactNative", 
                summary
            );

            if (filePath) {
                await Share.open({
                    url: `file://${filePath}`,
                    type: 'text/csv',
                    failOnCancel: false
                });
            }
        } catch (error) {
            const errorMessage = error instanceof Error ? error.message : "Unknown export error";
            
            if (errorMessage !== 'User did not share') {
                setStatus(`❌ Export Failed: ${errorMessage}`);
                console.error("Export Error:", error);
            }
        }
    }, [parsedCount, currentLogs]);

    // --- Lifecycle Management ---

    useEffect(() => {
        return () => {
            if (isRunningRef.current) {
                stopMonitoring();
            }
            // Memory Optimization: Explicitly release RAM payload to prevent 
            // garbage collection bloat when unmounting.
            JsonTestManager.releaseMemory();
        };
    }, [stopMonitoring]);

    return {
        status,
        isRunning,
        isReportReady,
        parsedCount,
        startBenchmark,
        exportResults
    };
};