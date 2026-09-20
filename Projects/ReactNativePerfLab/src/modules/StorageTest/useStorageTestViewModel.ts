import { useState, useCallback, useRef, useEffect } from 'react';
import Share from 'react-native-share';
import { StorageEngine } from '../../core/engine/StorageEngine';
import { usePerformanceStore, BenchmarkType } from '../../core/manager/PerformanceManager';
import { ExportManager } from '../../core/manager/ExportManager';

export const useStorageTestViewModel = () => {
    const [progress, setProgress] = useState<number>(0);
    const [isRunning, setIsRunning] = useState<boolean>(false);
    const [status, setStatus] = useState<string>("Ready");
    const [isWriteCompleted, setIsWriteCompleted] = useState<boolean>(false);
    const [isReportReady, setIsReportReady] = useState<boolean>(false);

    const { startMonitoring, stopMonitoring, currentLogs } = usePerformanceStore();
    const lastTestNameRef = useRef<string>("");

    const throttleProgressUpdate = useCallback((p: number) => {
        // Throttle updates to minimize bridge crossing frequency
        setProgress(p);
    }, []);

    const finishTest = useCallback((isWrite: boolean) => {
        stopMonitoring();
        lastTestNameRef.current = isWrite ? "Storage_RN_Write_2GB" : "Storage_RN_Read_2GB";
        setIsRunning(false);
        setStatus(isWrite ? "Write Completed ✅" : "Read Completed ✅");
        if (isWrite) setIsWriteCompleted(true);
        setIsReportReady(true);
    }, [stopMonitoring]);

    const runBenchmark = useCallback(async (isWrite: boolean) => {
        if (isRunning) return;

        setIsRunning(true);
        setIsReportReady(false);
        setProgress(0);
        setStatus(isWrite ? "Writing 2GB Fixed Payload..." : "Reading 2GB Fixed Payload...");

        startMonitoring(BenchmarkType.MICRO);

        try {
            if (isWrite) {
                await StorageEngine.writeData(2048, throttleProgressUpdate);
            } else {
                await StorageEngine.readData(throttleProgressUpdate);
            }
            
            finishTest(isWrite);
        } catch (error: unknown) {
            const err = error as Error;
            setStatus(`Error: ${err.message}`);
            setIsRunning(false);
            stopMonitoring();
        }
    }, [isRunning, startMonitoring, throttleProgressUpdate, finishTest, stopMonitoring]);

    const exportResults = useCallback(async () => {
        try {
            const filePath = await ExportManager.generateCSV(currentLogs, lastTestNameRef.current);
            if (filePath) {
                await Share.open({
                    title: 'Storage Benchmark Report',
                    url: `file://${filePath}`,
                    type: 'text/csv',
                    filename: `${lastTestNameRef.current}.csv`,
                    failOnCancel: false
                });
            }
        } catch (error: unknown) {
            const err = error as Error;
            if (err.message !== 'User did not share') {
                setStatus(`Export Error: ${err.message}`);
            }
        }
    }, [currentLogs]);

    useEffect(() => {
        return () => {
            StorageEngine.cancelTask();
            stopMonitoring();
        };
    }, [stopMonitoring]);

    return { progress, isRunning, status, isWriteCompleted, isReportReady, runBenchmark, exportResults };
};