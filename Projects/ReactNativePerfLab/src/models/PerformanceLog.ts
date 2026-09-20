/**
 * Telemetry DTO
 * A flat, strictly typed, and immutable data structure representing a single 
 * snapshot of hardware telemetry to minimize garbage collection overhead.
 */
export interface PerformanceLog {
    /** 
     * The monotonic timestamp in milliseconds.
     * Serves as the temporal anchor for the telemetry snapshot.
     */
    readonly timestampMillis: number;

    /**
     * Derived directly from `timestampMillis`. 
     * Provides an efficient integer key for fast virtual DOM diffing in lists.
     */
    readonly id: number;

    readonly cpuUsage: number;
    
    /** Absolute physical memory consumption footprint (MB) */
    readonly rawRAM: number;
    
    /** Relative application memory overhead (Net Delta in MB) */
    readonly netRAM: number;
    
    readonly batteryLevel: number;
    readonly fps: number;
    readonly thermalState: string;
}

/**
 * A globally cached formatter.
 * Statically caching this prevents the CPU bottleneck of repeatedly instantiating 
 * Intl.DateTimeFormat during tight loops or CSV exports.
 */
const timeFormatter = new Intl.DateTimeFormat('en-US', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
});

/**
 * Formats the timestamp into a human-readable string.
 * Defers the cost of string formatting until explicitly requested.
 *
 * @param timestampMillis - The raw epoch timestamp
 * @returns A formatted string in "HH:mm:ss" format.
 */
export const formatTelemetryTime = (timestampMillis: number): string => {
    return timeFormatter.format(timestampMillis);
};