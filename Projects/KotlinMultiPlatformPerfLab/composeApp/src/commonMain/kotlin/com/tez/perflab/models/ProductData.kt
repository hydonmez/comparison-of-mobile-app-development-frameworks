package com.tez.perflab.models

import kotlinx.serialization.Serializable

/**
 * A lightweight Data Transfer Object (DTO) representing a product entity.
 * Optimized for reflection-free JSON deserialization throughput.
 */
@Serializable
data class ProductData(
    val name: String,
    val price: String,
    val oldPrice: String,
    val discount: String,
    val category: String,
    val imageName: String
)

/**
 * A UI-bound wrapper encapsulating the raw product data for rendering pipelines.
 *
 * Uses a deterministic integer ID instead of a dynamic UUID. Generating UUIDs
 * in a tight loop during high-frequency scrolling benchmarks introduces CPU overhead
 * and frequent Garbage Collection (GC). Using a sequential primitive ID isolates
 * pure UI rendering performance.
 */
data class Product(
    val id: Int,
    val data: ProductData
)