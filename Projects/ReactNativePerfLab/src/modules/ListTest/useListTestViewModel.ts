import { useState, useCallback, useRef, useEffect } from 'react';
import Share from 'react-native-share'; 
import { ListTestManager } from '../../core/manager/ListTestManager';
import { usePerformanceStore, BenchmarkType } from '../../core/manager/PerformanceManager';
import { ExportManager } from '../../core/manager/ExportManager';
import { Product } from '../../models/ProductData';

export interface ScrollCommand {
    targetIndex: number;
    durationMs: number;
    animated: boolean; 
}

export interface ListEngineController {
    scrollToIndex: (command: ScrollCommand) => void;
}

// Employs Promises to simulate macro-task delays.
// Note: JS timers are subject to Event Loop drift under high UI load.
const delay = (ms: number) => new Promise<void>(resolve => setTimeout(resolve, ms));

/**
 * Automated Scroll Orchestrator
 * A deterministic ViewModel for orchestrating automated UI scrolling benchmarks.
 */
export const useListTestViewModel = () => {

    const [products, setProducts] = useState<Product[]>([]);
    const [isRunning, setIsRunning] = useState<boolean>(false);
    const [isTestCompleted, setIsTestCompleted] = useState<boolean>(false);
    const [status, setStatus] = useState<string>("Ready");

    const { startMonitoring, stopMonitoring, currentLogs } = usePerformanceStore();

    const isCancelledRef = useRef<boolean>(false);
    const isLoadingMoreRef = useRef<boolean>(false);
    
    // Bypasses React's asynchronous state batching to provide a synchronous
    // source of truth for the automated hot-loop.
    const productsRef = useRef<Product[]>([]); 

    const PAGE_SIZE = 20;
    const TARGET_COUNT = 300;

    const appendProducts = useCallback((newItems: Product[]) => {
        // Memory Optimization
        // Utilizing Array.prototype.concat() over the spread operator 
        // yields slightly better garbage collection performance for large arrays.
        const updated = productsRef.current.concat(newItems);
        productsRef.current = updated;
        
        setProducts(updated);
    }, []);

    const loadMoreData = useCallback(async () => {
        if (isLoadingMoreRef.current) return;
        isLoadingMoreRef.current = true;

        // I/O Latency Simulation
        await delay(100);

        const newItems = ListTestManager.fetchPage(PAGE_SIZE);

        if (newItems.length === 0) {
            setStatus("ERROR: Payload Empty");
            setIsRunning(false);
            isLoadingMoreRef.current = false;
            return;
        }

        appendProducts(newItems);
        isLoadingMoreRef.current = false;
    }, [appendProducts]);

    const stopTest = useCallback((isFinished: boolean = false) => {
        isCancelledRef.current = true;
        
        setIsRunning(false);
        setIsTestCompleted(isFinished);
        stopMonitoring();
        setStatus(isFinished ? "Test Finalized" : "Terminated");
    }, [stopMonitoring]);

    const runAutoScrollScenario = useCallback(async (listController: ListEngineController) => {
        let index = 0;

        while (index < TARGET_COUNT - 5) {
            if (isCancelledRef.current) break;

            const currentCount = productsRef.current.length;
            const next = Math.min(index + 2, currentCount - 1);

            if (next > index) {
                index = next;
                setStatus(`Scrolling Down... (${index}/${currentCount})`);
                
                listController.scrollToIndex({
                    targetIndex: index,
                    durationMs: 800,
                    animated: true 
                });

                // Travel duration + 16ms buffer (1 frame at 60Hz)
                await delay(800 + 16);
            } else {
                await delay(500);
            }

            if (!isLoadingMoreRef.current && currentCount < TARGET_COUNT && index >= currentCount - 10) {
                // Concurrency Note
                // JS is single-threaded. This asynchronous call defers execution via the Event Loop
                // rather than executing on a background thread.
                loadMoreData().catch(console.error);
            }
        }

        if (!isCancelledRef.current && productsRef.current.length > 0) {
            setStatus("Target Achieved");
            listController.scrollToIndex({
                targetIndex: productsRef.current.length - 1,
                durationMs: 1500,
                animated: true
            });
            await delay(1500 + 16);
        }

        if (!isCancelledRef.current && productsRef.current.length > 0) {
            setStatus("Returning to Top...");
            listController.scrollToIndex({
                targetIndex: 0,
                durationMs: 2500,
                animated: true
            });
            await delay(2500 + 16);
        }

        if (!isCancelledRef.current) {
            stopTest(true);
        }
    }, [loadMoreData, stopTest]);

    const startTest = useCallback(async (listController: ListEngineController) => {
        stopTest(false);

        isCancelledRef.current = false;
        isLoadingMoreRef.current = false;
        
        productsRef.current = [];
        setProducts([]);
        
        setIsRunning(true);
        setIsTestCompleted(false);
        setStatus("Preparing Dataset...");

        startMonitoring(BenchmarkType.MACRO);

        try {
            await ListTestManager.init();
            ListTestManager.resetCursor();

            await loadMoreData();
            await delay(420); 
            
            await runAutoScrollScenario(listController);

        } catch (error: any) {
            setStatus(`Error: ${error.message}`);
            stopTest(false);
        }
    }, [loadMoreData, runAutoScrollScenario, startMonitoring, stopTest]);

    const exportResults = useCallback(async () => {
        try {
            const summary = `Total_Items_Rendered,${productsRef.current.length},,,,`;
            const filePath = await ExportManager.generateCSV(
                currentLogs, 
                "Ecommerce_RN_Test_FlashList",
                summary
            );

            if (filePath) {
                // Log the path for debugging
                console.log("ExportManager Path:", filePath); 

                // Security Check: Does the filePath already contain 'file://'?
                const validUrl = filePath.startsWith('file://') 
                    ? filePath 
                    : `file://${filePath}`;

                await Share.open({
                    url: validUrl,
                    type: 'text/csv',
                    failOnCancel: false
                });
            }
        } catch (error: any) {
             if (error.message !== 'User did not share') {
                console.error("Export Failed:", error);
                setStatus(`Export Failed: ${error.message}`);
             }
        }
    }, [currentLogs]);

    useEffect(() => {
        return () => {
            isCancelledRef.current = true;
            stopMonitoring();
        };
    }, [stopMonitoring]);

    return {
        products,
        isRunning,
        isTestCompleted,
        status,
        startTest,
        stopTest,
        exportResults
    };
};