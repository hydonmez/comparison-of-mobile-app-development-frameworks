package com.tez.perflab.engines

import com.tez.perflab.managers.PlatformContext

/**
 * Platform-agnostic interface for disk I/O benchmarking.
 * Delegated to OS-specific implementations via native file I/O APIs.
 */
expect object StorageEngine {

    /**
     * Internal checksum used to prevent the compiler from optimizing away read/write loops
     * via Dead Code Elimination.
     */
    var securityChecksum: Int
        private set

    /**
     * Executes a sequential write benchmark on local storage.
     *
     * @param context Platform context for resolving internal app storage paths.
     * @param megabytes Total payload size to write sequentially in megabytes.
     * @param onProgress Progress callback reporting completion ratio (0.0 to 1.0).
     */
    suspend fun writeData(
        context: PlatformContext,
        megabytes: Int,
        onProgress: (Double) -> Unit
    )

    /**
     * Executes a sequential read benchmark on local storage.
     *
     * @param context Platform context for resolving internal app storage paths.
     * @param onProgress Progress callback reporting completion ratio (0.0 to 1.0).
     */
    suspend fun readData(
        context: PlatformContext,
        onProgress: (Double) -> Unit
    )
}