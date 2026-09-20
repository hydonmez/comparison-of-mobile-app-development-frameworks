package com.tez.nativekotlinperflabapp.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * A Data Transfer Object (DTO) for the GitHub Events API.
 * Uses compile-time adapter generation for optimized JSON parsing.
 */
@JsonClass(generateAdapter = true)
data class GitHubEvent(
    val id: String,
    val type: String,
    val actor: Actor,
    val repo: Repo,
    val payload: Payload,

    @field:Json(name = "public")
    val publicEvent: Boolean,

    @field:Json(name = "created_at")
    val createdAt: String
)

@JsonClass(generateAdapter = true)
data class Actor(
    val id: Int,
    val login: String,
    val url: String,

    @field:Json(name = "avatar_url")
    val avatarUrl: String
)

@JsonClass(generateAdapter = true)
data class Repo(
    val id: Int,
    val name: String,
    val url: String
)

/**
 * Handles the polymorphic nature of GitHub payloads with null-safety
 * to prevent parsing exceptions.
 */
@JsonClass(generateAdapter = true)
data class Payload(
    val ref: String? = null,

    @field:Json(name = "ref_type")
    val refType: String? = null,

    @field:Json(name = "master_branch")
    val masterBranch: String? = null,

    val description: String? = null
)