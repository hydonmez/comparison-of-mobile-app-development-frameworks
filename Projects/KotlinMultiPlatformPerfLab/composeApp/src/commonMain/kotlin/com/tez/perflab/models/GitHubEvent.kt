package com.tez.perflab.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A cross-platform Data Transfer Object (DTO) modeling the GitHub Events API.
 *
 * Utilizing the `@Serializable` annotation triggers compile-time code generation.
 * This avoids the CPU overhead introduced by JVM reflection, improving parsing throughput.
 */
@Serializable
data class GitHubEvent(
    val id: String,
    val type: String,
    val actor: Actor,
    val repo: Repo,
    val payload: Payload,

    // Maps the JSON key "public" to a Kotlin-safe property name to avoid keyword collisions.
    @SerialName("public")
    val publicEvent: Boolean,

    @SerialName("created_at")
    val createdAt: String
)

@Serializable
data class Actor(
    val id: Int,
    val login: String,
    val url: String,

    @SerialName("avatar_url")
    val avatarUrl: String
)

@Serializable
data class Repo(
    val id: Int,
    val name: String,
    val url: String
)

/**
 * Models the polymorphic 'payload' block of a GitHub event structure.
 *
 * Optional parameters are used to prevent mapping crashes. Since payload schemas
 * vary based on the event type, this flat optional structure avoids the CPU penalties
 * of custom polymorphic deserializers during high-frequency benchmarking.
 */
@Serializable
data class Payload(
    val ref: String? = null,

    @SerialName("ref_type")
    val refType: String? = null,

    @SerialName("master_branch")
    val masterBranch: String? = null,

    val description: String? = null
)