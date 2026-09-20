pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // [AUTOMATED TOOLCHAIN PROVISIONING]
    // The Foojay resolver ensures that any machine cloning this repository (e.g., CI/CD pipelines
    // or peer reviewers) automatically fetches the exact Java 17 toolchain required for zero-desugaring
    // bytecode compilation, guaranteeing reproducible performance metrics. Version 0.8.0 is the stable standard for Gradle 8.8+.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    // [STRICT REPOSITORY ENFORCEMENT]
    // FAIL_ON_PROJECT_REPOS prevents rogue sub-modules from injecting unverified third-party repositories.
    // This is crucial for a controlled benchmarking environment to avoid supply-chain variations.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "NativeKotlinPerfLabApp"
include(":app")