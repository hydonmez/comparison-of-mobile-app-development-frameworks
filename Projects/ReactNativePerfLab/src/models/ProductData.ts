/**
 * Product DTO & UI Wrapper
 * Lightweight TypeScript interfaces for zero-cost JSON deserialization 
 * and deterministic Virtual DOM diffing.
 */

/**
 * The raw data schema representing a product entity.
 * Decoupled from UI-specific identifiers to prevent structural bloat during parsing.
 */
export interface ProductData {
    readonly name: string;
    readonly price: string;
    readonly oldPrice: string;
    readonly discount: string;
    readonly category: string;
    readonly imageName: string;
}

/**
 * A UI-bound wrapper encapsulating the raw product data.
 * Uses a deterministic integer ID to ensure efficient O(1) row reconciliation 
 * in list components without the overhead of string UUIDs.
 */
export interface Product {
    readonly id: number;
    readonly data: ProductData;
}