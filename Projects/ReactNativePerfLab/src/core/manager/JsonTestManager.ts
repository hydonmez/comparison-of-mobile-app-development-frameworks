import { mapToGitHubEvent } from '../../models/GitHubEvent';
import RNFS from 'react-native-fs'; 
import { Platform } from 'react-native';

/**
 * High-Performance JSON Engine
 * A synchronous parsing engine for benchmarking within the React Native environment.
 * Executes synchronously on the JavaScript thread to isolate CPU deserialization 
 * and mapping throughput.
 */
class JsonTestManager {
  
  // Singleton instance
  private static instance: JsonTestManager;

  // Retains the raw payload in RAM to isolate CPU metrics from storage I/O latencies.
  private cachedRawJson: string | null = null;

  private constructor() {}

  public static getInstance(): JsonTestManager {
    if (!JsonTestManager.instance) {
      JsonTestManager.instance = new JsonTestManager();
    }
    return JsonTestManager.instance;
  }

  /**
   * Pre-loads data before starting the benchmark.
   * Offloads disk I/O to ensure the benchmark measures pure CPU performance.
   */
  public async preloadDataOnce(): Promise<void> {
    if (this.cachedRawJson !== null) return;

    try {
      let rawString = "";
      
      if (Platform.OS === 'android') {
         rawString = await RNFS.readFileAssets('benchmark_data.json', 'utf8');
      } else {
         rawString = await RNFS.readFile(`${RNFS.MainBundlePath}/benchmark_data.json`, 'utf8');
      }

      this.cachedRawJson = rawString;
    } catch (error) {
      console.error("CRITICAL EXCEPTION: Failed to preload JSON benchmark data.", error);
      throw error;
    }
  }

  /**
   * Executes a synchronous JSON parsing and mapping iteration to capture 
   * the exact CPU processing cost.
   * 
   * @returns The total number of decoded entities, retained to prevent compiler 
   * optimizations from discarding the result.
   */
  public runParseTest(): number {
    if (this.cachedRawJson === null) {
      throw new Error("CRITICAL EXCEPTION: preloadDataOnce() MUST be executed prior to benchmark initiation.");
    }

    try {
      // 1. Raw JSON Deserialization (Blocks JS execution)
      const parsedArray: any[] = JSON.parse(this.cachedRawJson);

      // 2. Synchronous Mapping (Measures pure JS execution engine performance)
      const events = parsedArray.map(mapToGitHubEvent);

      return events.length;
    } catch (error) {
      console.error("CRITICAL EXCEPTION: Benchmark Parsing failure:", error);
      return 0;
    }
  }

  /**
   * Clears the payload from memory to prevent artificial garbage collection 
   * pressure after the test.
   */
  public releaseMemory(): void {
    this.cachedRawJson = null;
  }
}

export default JsonTestManager.getInstance();