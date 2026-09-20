@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)
package com.tez.nativekotlinperflabapp.models

import kotlinx.serialization.Serializable

/**
 * A Data Transfer Object (DTO) representing a product entity.
 * Uses serialization to parse JSON efficiently.
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
 * A UI presentation wrapper that includes a unique identifier.
 * This is required by Jetpack Compose to optimize list rendering
 * and prevent unnecessary recompositions.
 */
data class Product(
    val id: Int,
    val data: ProductData
)