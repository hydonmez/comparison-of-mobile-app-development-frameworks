import Foundation

/// A high-throughput data provider engineered for virtualized UI scrolling benchmarks.
actor ListTestManager {

    static let shared = ListTestManager()

    private var cachedData: [ProductData] = ListTestManager.parseJSON()

    private var cursor = 0

    private init() {}

    private static func parseJSON() -> [ProductData] {
        guard let url = Bundle.main.url(forResource: "mock_products", withExtension: "json") else {
            fatalError("❌ CRITICAL: mock_products.json not found in the main bundle.")
        }

        do {
            let data = try Data(contentsOf: url)
            let parsed = try JSONDecoder().decode([ProductData].self, from: data)
            print("✅ ListTestManager: Dataset cached. Item count: \(parsed.count)")
            return parsed
        } catch {
            fatalError("❌ CRITICAL: JSON decoding failed: \(error)")
        }
    }

    /// Marked as `async` to satisfy Swift 6 strict concurrency rules for actor state mutation.
    /// This guarantees thread safety and prevents the UI thread from deadlocking 
    /// during high-frequency pagination requests.
    func fetchPage(pageSize: Int) async -> [Product] {
        guard !cachedData.isEmpty else { return [] }

        var page: [Product] = []
        page.reserveCapacity(pageSize)

        for _ in 0..<pageSize {
            let item = cachedData[cursor % cachedData.count]
            page.append(Product(id: cursor, data: item))
            cursor += 1
        }
        return page
    }

    /// Safely resets the pagination cursor across the actor boundary,
    /// ensuring consistent state behavior for consecutive benchmark runs.
    func resetCursor() async {
        cursor = 0
    }
}