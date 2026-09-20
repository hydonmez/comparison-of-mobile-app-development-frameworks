// [ROOT BUILD PIPELINE]
// Centralized plugin registry for the entire performance lab workspace.
// 'apply false' ensures plugins are fetched to the classpath but only activated
// within the specific sub-modules (like the app module) where explicitly required.
plugins {
    alias(libs.plugins.android.application) apply false

    // Synchronized strictly with the underlying TOML manifest declarations for version parity.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false

    // [TELEMETRY & CONCURRENCY]
    // Registered here to allow thread-safe atomic operations in hardware profiler modules.
    alias(libs.plugins.kotlinx.atomicfu) apply false

    // [AOT COMPILATION]
    // KSP (Kotlin Symbol Processing) migrated to the Version Catalog (TOML).
    // This enforces the 'Single Source of Truth' pattern for compiler backend versions.
    alias(libs.plugins.devtools.ksp) apply false}