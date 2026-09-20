import { useState, useRef, useCallback, useEffect } from 'react';
import Share from 'react-native-share';
import { MapTestDataGenerator } from '../../core/generator/MapTestDataGenerator';
import { usePerformanceStore, BenchmarkType } from '../../core/manager/PerformanceManager';
import { ExportManager } from '../../core/manager/ExportManager';
import { MapPoint } from '../../models/MapPoint';

/**
 * Deterministic Map Orchestrator
 * Automates geospatial rendering benchmarks and provides raw coordinates 
 * and zoom values to the presentation layer.
 */

// Mathematically identical zoom levels to the native baseline.
const ZOOM_OUT_LEVEL = 13.0;
const ZOOM_IN_LEVEL = 17.5;
const INITIAL_ZOOM = 11.12;
const FINAL_ZOOM = 11.2;
const DEFAULT_CENTER = { latitude: 41.0082, longitude: 28.9784 };

// Promise-based delay utility.
const delay = (ms: number) => new Promise<void>(resolve => setTimeout(resolve, ms));

export interface MapEngineController {
    // A generalized routing method to handle platform differences.
    // Routes to Google Maps animateCamera on Android and MapKit animateToRegion on iOS.
    animateToTarget: (
        target: { 
            center: { latitude: number; longitude: number }; 
            zoom: number; 
        }, 
        durationMs: number
    ) => void;
}

export const useMapTestViewModel = () => {
    const [points, setPoints] = useState<MapPoint[]>([]);
    const [isRunning, setIsRunning] = useState(false);
    const [status, setStatus] = useState("Ready");
    const [isReportReady, setIsReportReady] = useState(false);

    // Non-reactive execution flag to prevent state-diffing overhead during async cancellation
    const isCancelledRef = useRef(false);
    
    const { startMonitoring, stopMonitoring, currentLogs } = usePerformanceStore();

    const startPinTour = useCallback(async (mapController: MapEngineController, localPoints: MapPoint[]) => {
        isCancelledRef.current = false;
        
        // Warm-up Phase
        await delay(1000);

        for (let i = 0; i < localPoints.length; i++) {
            if (isCancelledRef.current) break;
            const point = localPoints[i];

            // STAGE 1: TRANSITION (Horizontal Panning)
            setStatus(`Target ${i + 1} / ${localPoints.length} -> Panning ✈️`);
            mapController.animateToTarget({ 
                center: point.coordinate, 
                zoom: ZOOM_OUT_LEVEL
            }, 1500);
            await delay(1800);

            if (isCancelledRef.current) break;

            // STAGE 2: INSPECTION (Vertical Zooming)
            setStatus(`Target ${i + 1} -> Inspecting Detail 🔍`);
            mapController.animateToTarget({ 
                center: point.coordinate, 
                zoom: ZOOM_IN_LEVEL
            }, 1500);
            await delay(1800);

            if (isCancelledRef.current) break;

            // STAGE 3: ASCENSION (Zoom Out)
            if (i < localPoints.length - 1) {
                setStatus(`Target ${i + 1} -> Ascending ⬆️`);
                mapController.animateToTarget({ 
                    center: point.coordinate, 
                    zoom: ZOOM_OUT_LEVEL
                }, 1000);
                await delay(1200);
            }
        }

        // FINAL STAGE: Global Context Review
        if (!isCancelledRef.current) {
            setStatus("Tour Finalized! Global Review 🌍");
            mapController.animateToTarget({ 
                center: DEFAULT_CENTER, 
                zoom: FINAL_ZOOM
            }, 2500);
            await delay(3000);

            if (!isCancelledRef.current) {
                stopTest(true);
            }
        }
    }, []);

    const startTest = useCallback((mapController: MapEngineController) => {
        stopTest(false);

        setIsRunning(true);
        setStatus("Initializing Map & Geospatial Data...");
        setIsReportReady(false);

        setTimeout(() => {
            const newPoints = MapTestDataGenerator.generatePoints(20);
            setPoints(newPoints);
            setStatus("Benchmarking in Progress...");

            startMonitoring(BenchmarkType.MACRO);
            startPinTour(mapController, newPoints);
        }, 0);
    }, [startPinTour, startMonitoring]);

    const stopTest = useCallback((isFinished: boolean = false) => {
        isCancelledRef.current = true;
        
        setIsRunning((prevIsRunning) => {
            if (!prevIsRunning) return false;
            
            stopMonitoring();
            setStatus(isFinished ? "Tour Completed ✅" : "Test Terminated 🛑");
            setIsReportReady(isFinished);
            return false;
        });
    }, [stopMonitoring]);

    const exportResults = useCallback(async () => {
        try {
            const summary = `Total_Points_Visited,${points.length},,,,`;
            const filePath = await ExportManager.generateCSV(
                currentLogs, 
                "Map_RN_Optimized_Tour", 
                summary
            );

            if (filePath) {
                await Share.open({
                    url: `file://${filePath}`,
                    type: 'text/csv',
                    failOnCancel: false
                });
            }
        } catch (error: any) {
            if (error.message !== 'User did not share') {
                setStatus(`Export Failed: ${error.message}`);
            }
        }
    }, [points.length, currentLogs]);

    useEffect(() => {
        return () => {
            isCancelledRef.current = true;
            stopMonitoring();
        };
    }, [stopMonitoring]);

    return {
        points,
        isRunning,
        status,
        isReportReady,
        // Provide the initial raw position to the UI layer
        initialTarget: { 
            center: DEFAULT_CENTER, 
            zoom: INITIAL_ZOOM
        }, 
        startTest,
        stopTest,
        exportResults
    };
};