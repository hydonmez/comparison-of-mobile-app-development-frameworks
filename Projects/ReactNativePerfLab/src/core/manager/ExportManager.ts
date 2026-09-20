// src/managers/ExportManager.ts

import RNFS from 'react-native-fs';
import { PerformanceLog, formatTelemetryTime } from '../../models/PerformanceLog';

/**
 * Export Manager
 * A utility for aggregating and exporting raw telemetry data.
 * Designed with single-pass loops to optimize memory allocation and prevent call stack issues.
 */
export class ExportManager {
    
    /**
     * Generates a structured CSV file from a collection of performance logs.
     * 
     * @param logs The raw performance logs captured during the benchmark suite.
     * @param testName A descriptive identifier for the benchmark.
     * @param customSummary Optional appended text for additional context.
     * @returns A Promise resolving to the absolute file path of the CSV, or null on failure.
     */
    static async generateCSV(
        logs: PerformanceLog[],
        testName: string,
        customSummary?: string
    ): Promise<string | null> {
        if (!logs || logs.length === 0) return null;

        // Generate filename with Unix timestamp
        const timestamp = Math.floor(Date.now() / 1000);
        const fileName = `${testName}_${timestamp}.csv`;
        
        // Define file path in the caches directory
        const filePath = `${RNFS.CachesDirectoryPath}/${fileName}`;

        try {
            // Pre-allocate array length to prevent dynamic resizing overhead
            const totalRows = customSummary ? logs.length + 4 : logs.length + 3;
            const rows: string[] = new Array(totalRows); 
            let rowIndex = 0;

            // ===== HEADER =====
            rows[rowIndex++] = "Timestamp,CPU(%),Raw_RAM(MB),Net_RAM(MB),Battery(%),FPS,Thermal_State";

            // ===== RAW LOGS =====
            for (let i = 0; i < logs.length; i++) {
                const log = logs[i];
                
                // Use template literals for optimized string interpolation
                rows[rowIndex++] = `${formatTelemetryTime(log.timestampMillis)},${log.cpuUsage.toFixed(2)},${log.rawRAM.toFixed(2)},${log.netRAM.toFixed(2)},${log.batteryLevel.toFixed(1)},${log.fps},${log.thermalState}`;
            }

            // ===== SUMMARY =====
            rows[rowIndex++] = "\n--- SUMMARY ---";
            rows[rowIndex++] = ExportManager.calculateSummary(logs);

            if (customSummary) {
                rows[rowIndex++] = `\n${customSummary}`;
            }

            // Join the payload and write to disk asynchronously
            const csvContent = rows.join('\n');
            await RNFS.writeFile(filePath, csvContent, 'utf8');

            return filePath;
        } catch (error) {
            console.error("[ExportManager] CSV Export Error: ", error);
            return null;
        }
    }

    /**
     * Calculates core statistical metrics (Average, Standard Deviation, Min, Max) for the dataset.
     */
    private static calculateSummary(logs: PerformanceLog[]): string {
        const cpu = logs.map(l => l.cpuUsage);
        const raw = logs.map(l => l.rawRAM);
        const net = logs.map(l => l.netRAM);
        const fps = logs.map(l => l.fps);

        const avg = (v: number[]): number => v.length === 0 ? 0 : v.reduce((a, b) => a + b, 0) / v.length;

        /**
         * Calculates the Sample Standard Deviation using Bessel's correction (N-1).
         * Prevents division-by-zero when only a single frame is logged.
         */
        const std = (v: number[]): number => {
            if (v.length <= 1) return 0.0;
            const mean = avg(v);
            const sumOfSquaredDifferences = v.reduce((sum, val) => sum + Math.pow(val - mean, 2), 0);
            return Math.sqrt(sumOfSquaredDifferences / (v.length - 1));
        };

        /**
         * Calculates min and max in a single O(N) pass.
         * Avoids Math.min/max with spread operators to prevent Call Stack Overflows with large arrays.
         */
        const minMax = (v: number[]): { min: number, max: number } => {
            if (v.length === 0) return { min: 0, max: 0 };
            
            let currentMin = v[0];
            let currentMax = v[0];
            
            for (let i = 1; i < v.length; i++) {
                if (v[i] < currentMin) currentMin = v[i];
                if (v[i] > currentMax) currentMax = v[i];
            }
            
            return { min: currentMin, max: currentMax };
        };

        const formatStat = (name: string, v: number[]): string => {
            const mean = avg(v).toFixed(2);
            const s = std(v).toFixed(2);
            const mm = minMax(v);
            return `${name},${mean},${s},${mm.min.toFixed(2)},${mm.max.toFixed(2)}`;
        };

        let summary = "Metric,Average,StdDev,Min,Max\n";
        summary += `${formatStat("CPU(%)", cpu)}\n`;
        summary += `${formatStat("Raw_RAM(MB)", raw)}\n`;
        summary += `${formatStat("Net_RAM(MB)", net)}\n`;
        summary += formatStat("FPS", fps);

        return summary;
    }
}