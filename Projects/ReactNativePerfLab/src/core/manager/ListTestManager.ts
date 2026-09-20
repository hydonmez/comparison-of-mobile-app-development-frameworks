import { ProductData, Product } from '../../models/ProductData';

// Statically importing JSON embeds it into the JS bundle.
// This increases the initial JS heap allocation but provides immediate memory access.
import bundledJson from '../../assets/mock_products.json';

/**
 * In-memory data provider for virtualized UI scrolling benchmarks.
 * Leverages JavaScript's single-threaded event loop, eliminating the need 
 * for concurrent access controls.
 */
export class ListTestManager {
    
    private static readonly cachedData: ProductData[] = bundledJson as ProductData[];
    private static cursor: number = 0;
    private static isInitialized: boolean = false;

    /**
     * Initializes the dataset.
     * Kept as an asynchronous operation to maintain interface consistency with native implementations.
     */
    static async init(): Promise<void> {
        if (this.isInitialized) return;
        
        if (!this.cachedData || this.cachedData.length === 0) {
            console.error("ListTestManager: Dataset failed to load or is empty.");
            return;
        }

        this.isInitialized = true;
        console.log(`ListTestManager: Dataset loaded. Item count: ${this.cachedData.length}`);
    }

    /**
     * Generates a deterministic page of mock products.
     * 
     * @param pageSize The exact number of items to generate per batch.
     * @returns A pre-allocated array of Product entities.
     */
    static fetchPage(pageSize: number): Product[] {
        const dataLength = this.cachedData.length;
        if (dataLength === 0) return [];

        // Pre-allocation prevents dynamic array resizing overhead
        const page: Product[] = new Array(pageSize);

        // Caching static references locally reduces property lookup overhead
        const cache = this.cachedData;
        let currentCursor = this.cursor;

        for (let i = 0; i < pageSize; i++) {
            const uniqueId = currentCursor++;
            
            page[i] = {
                id: uniqueId,
                data: cache[uniqueId % dataLength]
            };
        }

        // Commit cursor state post-loop
        this.cursor = currentCursor;
        return page;
    }

    /**
     * Resets the pagination cursor for reproducibility across multiple benchmark iterations.
     */
    static resetCursor(): void {
        this.cursor = 0;
    }
}