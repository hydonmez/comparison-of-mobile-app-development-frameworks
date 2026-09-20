import { NativeModules, NativeEventEmitter } from 'react-native';

const { NativeStorageBenchmark } = NativeModules;

// Prevents crashes if the Native Module is not linked.
const storageEventEmitter = NativeStorageBenchmark 
    ? new NativeEventEmitter(NativeStorageBenchmark) 
    : null;

/**
 * Native Storage Engine for benchmarking. 
 * Delegates heavy I/O operations directly to the native side (Swift/Kotlin) 
 * to avoid bridge serialization overhead. JS acts only as an orchestrator.
 */
export class StorageEngine {
    
    /**
     * Stores the checksum calculated natively to prevent 
     * compiler Dead Code Elimination (DCE).
     */
    static securityChecksum: number = 0;

    /**
     * Halts the active I/O benchmark natively.
     */
    static cancelTask(): void {
        if (!NativeStorageBenchmark) return;
        NativeStorageBenchmark.cancelTask();
    }

    /**
     * Triggers a sequential write benchmark on the native side.
     */
    static async writeData(
        megabytes: number, 
        onProgress: (progress: number) => void
    ): Promise<void> {
        return new Promise((resolve, reject) => {
            if (!NativeStorageBenchmark || !storageEventEmitter) {
                return reject(new Error("NativeStorageBenchmark module is not linked."));
            }

            // Subscribe to real-time progress emissions from Swift/Kotlin
            const subscription = storageEventEmitter.addListener(
                'onStorageProgress', 
                (progressValue: number) => onProgress(progressValue)
            );

            // Start the native benchmark
            NativeStorageBenchmark.writeData(megabytes)
                .then((checksum: number) => {
                    // Using class name explicitly instead of 'this' for strict static context safety
                    StorageEngine.securityChecksum = checksum;
                    subscription.remove();
                    resolve();
                })
                .catch((error: Error) => {
                    subscription.remove();
                    reject(error);
                });
        });
    }

    /**
     * Triggers a sequential read benchmark on the native side.
     */
    static async readData(onProgress: (progress: number) => void): Promise<void> {
        return new Promise((resolve, reject) => {
            if (!NativeStorageBenchmark || !storageEventEmitter) {
                return reject(new Error("NativeStorageBenchmark module is not linked."));
            }

            const subscription = storageEventEmitter.addListener(
                'onStorageProgress', 
                (progressValue: number) => onProgress(progressValue)
            );

            NativeStorageBenchmark.readData()
                .then((checksum: number) => {
                    StorageEngine.securityChecksum = checksum;
                    subscription.remove();
                    resolve();
                })
                .catch((error: Error) => {
                    subscription.remove();
                    reject(error);
                });
        });
    }
}