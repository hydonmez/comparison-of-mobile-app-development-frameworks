import 'package:json_annotation/json_annotation.dart';

// Required directive for build_runner to generate compile-time parsing logic.
part 'github_event.g.dart';

/// An immutable Data Transfer Object (DTO) optimized for AOT compilation and strict JSON deserialization.
///
/// Setting `createToJson: false` prevents the compiler from generating unused serialization metadata,
/// minimizing the binary size and reducing memory allocation pressure.
@JsonSerializable(createToJson: false)
class GitHubEvent {
  /// Relies strictly on the payload's unique identifier to avoid local generation overhead.
  final String id;
  final String type;
  final Actor actor;
  final Repo repo;
  final Payload payload;

  /// Explicitly maps the raw JSON key to the Dart property.
  /// Bypasses the need for runtime field renaming strategies, ensuring O(1) mapping complexity.
  @JsonKey(name: 'public')
  final bool publicEvent;

  /// Maintained as a raw String to prevent DateTime parsing overhead during deserialization.
  @JsonKey(name: 'created_at')
  final String createdAt;

  const GitHubEvent({
    required this.id,
    required this.type,
    required this.actor,
    required this.repo,
    required this.payload,
    required this.publicEvent,
    required this.createdAt,
  });

  /// Factory constructor that delegates instantiation to the compile-time generated code.
  factory GitHubEvent.fromJson(Map<String, dynamic> json) =>
      _$GitHubEventFromJson(json);
}

@JsonSerializable(createToJson: false)
class Actor {
  final int id;
  final String login;
  final String url;

  @JsonKey(name: 'avatar_url')
  final String avatarUrl;

  const Actor({
    required this.id,
    required this.login,
    required this.url,
    required this.avatarUrl,
  });

  factory Actor.fromJson(Map<String, dynamic> json) => _$ActorFromJson(json);
}

@JsonSerializable(createToJson: false)
class Repo {
  final int id;
  final String name;
  final String url;

  const Repo({required this.id, required this.name, required this.url});

  factory Repo.fromJson(Map<String, dynamic> json) => _$RepoFromJson(json);
}

/// A nested DTO encapsulating event-specific metadata payloads.
///
/// Properties are deliberately declared as nullable to safely accommodate polymorphic
/// or incomplete payloads, preventing runtime exceptions during high-frequency parsing.
@JsonSerializable(createToJson: false)
class Payload {
  final String? ref;

  @JsonKey(name: 'ref_type')
  final String? refType;

  @JsonKey(name: 'master_branch')
  final String? masterBranch;

  final String? description;

  const Payload({this.ref, this.refType, this.masterBranch, this.description});

  factory Payload.fromJson(Map<String, dynamic> json) =>
      _$PayloadFromJson(json);
}
