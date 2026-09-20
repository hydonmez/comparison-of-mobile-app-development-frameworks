// [ROOT BUILD CONFIGURATION: 2026 BASELINE]
// This file orchestrates the plugin classpath for the entire multi-project build.

plugins {
    // Standard Android & Kotlin Multiplatform Plugins
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlinx.atomicfu) apply false
    alias(libs.plugins.kotlinCompose) apply false

    // [KSP ARCHITECTURE]
    // Migrated to Version Catalog (TOML) to enforce the 'Single Source of Truth' pattern.
    // Synchronized strictly with Kotlin 2.1.20 for AOT Moshi Code Generation.
    alias(libs.plugins.ksp) apply false
}