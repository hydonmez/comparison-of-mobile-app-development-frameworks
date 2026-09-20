rootProject.name = "KotlinMultiplatformPerfLab"

// [COMPILER FEATURE ENHANCEMENT]
// Enables type-safe accessors for project dependencies, reducing build script errors.
// enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()

        // [INFRASTRUCTURE SETUP]
        // Required for resolving early-access or patched JetBrains compiler plugins.
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

plugins {
    // [AUTOMATED TOOLCHAIN PROVISIONING]
    // Synchronized strictly with the Native Android lab to guarantee reproducible builds on Java 17.
    // This ensures identical JVM environments across all benchmark iterations, eliminating
    // JDK-induced compilation and execution anomalies.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    // [DEPENDENCY RESOLUTION STRICTNESS]
    // Centralizes all repository declarations. Prevents individual sub-modules
    // from silently adding insecure or conflicting repositories, maintaining the
    // absolute integrity of the performance lab environment.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()

        
        // While Google hosts native 'androidx' libraries, JetBrains hosts the
        // 'org.jetbrains.androidx' Multiplatform ports here. This resolves
        // architectural dependencies required for symmetric lifecycle and navigation metrics.
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://maven.pkg.jetbrains.space/public/p/compose/patch")
    }
}

include(":composeApp")