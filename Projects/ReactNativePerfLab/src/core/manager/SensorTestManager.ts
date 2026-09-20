import {
    accelerometer,
    gyroscope,
    magnetometer,
    setUpdateIntervalForType,
    SensorTypes,
} from 'react-native-sensors';
import { startStepCounterUpdate, stopStepCounterUpdate } from '@dongminyu/react-native-step-counter';
import { Subscription } from 'rxjs';

/**
 * High-frequency hardware telemetry manager for benchmarking.
 * Requests maximum hardware polling (10ms/100Hz) to stress the JS bridge, 
 * and throttles updates to 16Hz (60ms) for the UI.
 */
export class SensorTestManager {

    private static isRegistered: boolean = false;

    private static accelSub: Subscription | null = null;
    private static gyroSub: Subscription | null = null;
    private static magnetSub: Subscription | null = null;

    // --- CALLBACKS ---
    private static onAccelUpdate: ((data: number[]) => void) | null = null;
    private static onGyroUpdate: ((data: number[]) => void) | null = null;
    private static onMagnetUpdate: ((data: number[]) => void) | null = null;
    private static onStepUpdate: ((count: number) => void) | null = null;

    // --- TEMPORAL THROTTLING REGISTERS ---
    private static lastAccelUpdate: number = 0;
    private static lastGyroUpdate: number = 0;
    private static lastMagnetUpdate: number = 0;

    private static initialStepCount: number | null = null;

    /**
     * Initializes hardware sensors and binds the execution context.
     */
    static startSensors(
        accelCallback: (data: number[]) => void,
        gyroCallback: (data: number[]) => void,
        magnetCallback: (data: number[]) => void,
        stepCallback: (count: number) => void
    ): void {
        if (this.isRegistered) this.stopSensors();

        this.onAccelUpdate = accelCallback;
        this.onGyroUpdate = gyroCallback;
        this.onMagnetUpdate = magnetCallback;
        this.onStepUpdate = stepCallback;

        this.lastAccelUpdate = 0;
        this.lastGyroUpdate = 0;
        this.lastMagnetUpdate = 0;
        this.initialStepCount = null;

        // Aggressive sampling rate (10ms / 100Hz) to test bridge payload limits.
        setUpdateIntervalForType(SensorTypes.accelerometer, 10);
        setUpdateIntervalForType(SensorTypes.gyroscope, 10);
        setUpdateIntervalForType(SensorTypes.magnetometer, 10);

        try {
            // 1. ACCELEROMETER
            this.accelSub = accelerometer.subscribe(({ x, y, z }) => {
                const now = Date.now();
                
                // Software throttling: Filters the 100Hz bridge stream down to ~16Hz (60ms).
                if (now - this.lastAccelUpdate > 60) {
                    this.lastAccelUpdate = now;
                    this.onAccelUpdate?.([x, y, z]);
                }
            });

            // 2. GYROSCOPE
            this.gyroSub = gyroscope.subscribe(({ x, y, z }) => {
                const now = Date.now();
                
                if (now - this.lastGyroUpdate > 60) {
                    this.lastGyroUpdate = now;
                    this.onGyroUpdate?.([x, y, z]);
                }
            });

            // 3. MAGNETOMETER
            this.magnetSub = magnetometer.subscribe(({ x, y, z }) => {
                const now = Date.now();
                
                if (now - this.lastMagnetUpdate > 60) {
                    this.lastMagnetUpdate = now;
                    this.onMagnetUpdate?.([x, y, z]);
                }
            });

            // 4. HARDWARE PEDOMETER
            startStepCounterUpdate(new Date(), (data) => {
                const totalSteps = data.steps;

                if (this.initialStepCount === null) {
                    this.initialStepCount = totalSteps;
                }

                const baseSteps = this.initialStepCount;
                this.onStepUpdate?.(totalSteps - baseSteps);
            });

        } catch (error) {
            console.error("Sensor initialization failed:", error);
        }

        this.isRegistered = true;
    }

    /**
     * Terminates hardware listeners and clears closures to prevent memory leaks.
     */
    static stopSensors(): void {
        if (!this.isRegistered) return;

        this.accelSub?.unsubscribe();
        this.gyroSub?.unsubscribe();
        this.magnetSub?.unsubscribe();

        stopStepCounterUpdate();

        this.accelSub = null;
        this.gyroSub = null;
        this.magnetSub = null;

        this.onAccelUpdate = null;
        this.onGyroUpdate = null;
        this.onMagnetUpdate = null;
        this.onStepUpdate = null;

        this.isRegistered = false;
    }
}