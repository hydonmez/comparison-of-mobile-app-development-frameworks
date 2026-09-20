import Foundation

/// The root Data Transfer Object (DTO) for GitHub telemetry parsing.
///
/// Conforms strictly to `Decodable` instead of `Codable` to prevent the compiler from 
/// generating unused serialization metadata, reducing binary size and compilation overhead. 
/// It is marked as `Sendable` to guarantee thread-safe transitions from background parsing tasks to the UI.
struct GitHubEvent: Decodable, Identifiable, Sendable {
    
    /// A unique identifier sourced directly from the raw JSON payload.
    /// Bypasses local `UUID()` generation to eliminate CPU overhead during benchmark loops.
    let id: String
    let type: String
    let actor: Actor
    let repo: Repo
    let payload: Payload
    let publicEvent: Bool
    
    /// Stored as a raw String rather than a Date object.
    /// ISO8601 date parsing introduces computational overhead. Using a String ensures the benchmark 
    /// measures pure JSON structural mapping throughput without date-formatting delays.
    let createdAt: String

    /// Defines the exact mapping between snake_case JSON keys and camelCase properties.
    /// Explicitly defining CodingKeys provides O(1) compile-time mapping, which is significantly 
    /// faster than using `.convertFromSnakeCase` at runtime.
    enum CodingKeys: String, CodingKey {
        case id, type, actor, repo, payload
        case publicEvent = "public"
        case createdAt = "created_at"
    }
}

/// Represents the entity (user or bot) that triggered the GitHub event.
struct Actor: Decodable, Sendable {
    let id: Int
    let login: String
    let url: String
    let avatarUrl: String

    enum CodingKeys: String, CodingKey {
        case id, login, url
        case avatarUrl = "avatar_url"
    }
}

/// Represents the target repository associated with the event.
struct Repo: Decodable, Sendable {
    let id: Int
    let name: String
    let url: String
}

/// Encapsulates event-specific metadata payloads.
/// Properties are optional to safely handle incomplete payload structures without throwing decoder errors.
struct Payload: Decodable, Sendable {
    let ref: String?
    let refType: String?
    let masterBranch: String?
    let description: String?

    enum CodingKeys: String, CodingKey {
        case ref
        case refType = "ref_type"
        case masterBranch = "master_branch"
        case description
    }
}