import Foundation

/// A raw data schema for product entities, designed for efficient JSON deserialization.
/// Decoupling raw decoded properties from view-specific identifiers prevents the JSONDecoder 
/// from incurring unnecessary computational overhead during parsing.
struct ProductData: Decodable {
    let name: String
    let price: String
    let oldPrice: String
    let discount: String
    let category: String
    let imageName: String
}

/// A UI-bound wrapper encapsulating raw product data for rendering.
/// Replaces dynamic `UUID()` allocation with a deterministic integer ID to avoid CPU overhead 
/// during high-frequency scrolling, isolating pure UI rendering performance.
struct Product: Identifiable {
    let id: Int
    let data: ProductData
}