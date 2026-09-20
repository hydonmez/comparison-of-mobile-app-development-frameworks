import java.util.Properties
import java.io.FileInputStream
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinCompose)

    // [CONCURRENCY & TELEMETRY PARITY]
    // Activated explicitly to enforce thread-safe atomic operations during high-frequency hardware profiling.
    alias(libs.plugins.kotlinx.atomicfu)
}

// [CREDENTIAL INJECTION & SECURITY]
// Securely resolves the Maps API Key from local.properties.
// Prevents key exposure in public repositories while ensuring hardware-accelerated rendering is properly authenticated.
val localProperties = Properties()
val localPropertiesFile = project.rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

kotlin {
    // [COMPILER ARCHITECTURE & JVM TOOLCHAIN]
    // Synchronized strictly with the Native Android baseline targeting Java 17.
    // This guarantees zero-desugaring overhead, ensuring that captured CPU/RAM metrics reflect pure computational execution rather than compatibility layers.
    jvmToolchain(17)

    val iosTargets = listOf(iosX64(), iosArm64(), iosSimulatorArm64())
    iosTargets.forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            freeCompilerArgs += listOf("-Xbinary=bundleId=com.tez.perflab")
        }
    }

    androidTarget {
        compilerOptions {
            // Configured JVM target exclusively via JvmTarget.JVM_17.
            // Explicit arguments like '-Xjdk-release' are omitted to prevent compiler resolution conflicts and maintain cross-platform build stability.
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.process)
            implementation(libs.play.services.maps)
            implementation(libs.maps.compose)
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.ui)
        }

        commonMain.dependencies {
            implementation(libs.coil.compose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.navigation.compose)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.kotlinx.atomicfu)
        }
    }
}

android {
    namespace = "com.tez.perflab"

    // [API SURFACE PARITY]
    // Locked to SDK 35 to ensure access to modern hardware telemetry APIs (e.g., Choreographer, Thermal API).
    compileSdk = 35

    signingConfigs {
        create("release") {
            // [DEMONSTRATION KEYSTORE]
            storeFile = file("my-release-key.jks")
            storePassword = "123456"
            keyAlias = "key0"
            keyPassword = "123456"
        }
    }

    defaultConfig {
        applicationId = "com.tez.perflab"

        // [ARCHITECTURAL BASELINE]
        // Establishing minSdk 33 ensures the execution environment exclusively relies on the modern ART garbage collector (GC),
        // eliminating legacy compatibility layers that could introduce metric anomalies during benchmarking.
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        manifestPlaceholders["mapsApiKey"] = localProperties.getProperty("MAPS_API_KEY") ?: ""
    }

    buildTypes {
        getByName("release") {
            resValue("string", "app_name", "KMP Perflab (Release)")

            // [AOT OPTIMIZATION & MINIFICATION]
            // Enforcing aggressive R8 minification ensures parity with Native Android's memory footprint and execution paths.
            isMinifyEnabled = true
            isShrinkResources = true

            signingConfig = signingConfigs.getByName("release")

            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        // [BYTECODE SYNCHRONIZATION]
        // Enforces Java 17 bytecode generation to eliminate asymmetric execution overhead between Native Android and Kotlin Multiplatform targets.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Explicit fragment support required for integrating platform-specific components (e.g., Google Maps fragment host).
    implementation("androidx.fragment:fragment-ktx:1.8.0")
    debugImplementation(libs.compose.uiTooling)
}