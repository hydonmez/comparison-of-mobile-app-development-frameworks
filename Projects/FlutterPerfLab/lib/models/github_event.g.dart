// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'github_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

GitHubEvent _$GitHubEventFromJson(Map<String, dynamic> json) => GitHubEvent(
  id: json['id'] as String,
  type: json['type'] as String,
  actor: Actor.fromJson(json['actor'] as Map<String, dynamic>),
  repo: Repo.fromJson(json['repo'] as Map<String, dynamic>),
  payload: Payload.fromJson(json['payload'] as Map<String, dynamic>),
  publicEvent: json['public'] as bool,
  createdAt: json['created_at'] as String,
);

Actor _$ActorFromJson(Map<String, dynamic> json) => Actor(
  id: (json['id'] as num).toInt(),
  login: json['login'] as String,
  url: json['url'] as String,
  avatarUrl: json['avatar_url'] as String,
);

Repo _$RepoFromJson(Map<String, dynamic> json) => Repo(
  id: (json['id'] as num).toInt(),
  name: json['name'] as String,
  url: json['url'] as String,
);

Payload _$PayloadFromJson(Map<String, dynamic> json) => Payload(
  ref: json['ref'] as String?,
  refType: json['ref_type'] as String?,
  masterBranch: json['master_branch'] as String?,
  description: json['description'] as String?,
);
