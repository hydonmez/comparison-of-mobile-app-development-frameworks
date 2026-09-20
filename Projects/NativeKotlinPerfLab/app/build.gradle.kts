import java.util.Properties
import java.io.FileInputStream
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlinx.atomicfu)
    id("com.google.devtools.ksp")
}

// [CREDENTIAL INJECTION]
// Securely loading the Maps API Key from local.properties.
// This ensures hardware-accelerated MapView rendering without compromising key security on public repositories.
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

// [MODERN COMPILER CONFIGURATION: PURE TELEMETRY BASELINE]
// Implementing Kotlin 2.1 architecture with the K2 compiler backend.
// Utilizing Java 17 Toolchain to guarantee zero desugaring overhead. This ensures that
// captured CPU and RAM metrics reflect raw framework throughput, not compiler compatibility layers.
kotlin {
    jvmToolchain(17)

    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

android {
    signingConfigs {
        create("release") {
            // [DEMONSTRATION KEYSTORE]
            // Hardcoded for academic/public repository demonstration purposes.
            // Ensures reproducible release builds for peer review without environment variable dependencies.
            storeFile = file("my-release-key.jks")
            storePassword = "123456"
            keyPassword = "123456"
            keyAlias = "key0"
        }
    }

    namespace = "com.tez.nativekotlinperflabapp"
    // Targeting the bleeding-edge SDK to utilize modern Choreographer and Thermal APIs for telemetry.
    compileSdk = 35

    defaultConfig {
        manifestPlaceholders += mapOf()
        applicationId = "com.tez.nativekotlinperflabapp"

        // [ARCHITECTURAL BASELINE: MODERN RUNTIME]
        // minSdk 33 ensures the benchmark exclusively utilizes modern ART garbage collection (GC)
        // and completely eliminates legacy support library performance baggage.
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        signingConfig = signingConfigs.getByName("release")

        manifestPlaceholders["mapsApiKey"] = localProperties.getProperty("MAPS_API_KEY") ?: ""
    }

    buildTypes {
        release {
            // [COMPILER OPTIMIZATION: R8 PRODUCTION GRADE]
            // Aggressive R8 minification and dead-code elimination are mandatory for hardware benchmarking.
            // This ensures memory allocation (RAM footprint) and CPU cycles are not wasted
            // on unused framework components, providing parity with C++/Swift optimizations.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        // Synchronized with Java 17 to leverage peak efficiency of the ART engine
        // ensuring absolute stability across the target API range (33-35).
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // [UI & ICONS FOUNDATION]
    // Extended icons for comprehensive UI rendering tests and stress loads.
    implementation(libs.androidx.compose.material.icons.extended)

    // [SERIALIZATION: AOT via KSP]
    // Utilizing KSP (Kotlin Symbol Processing) for JSON parsing.
    // Bypasses runtime reflection, ensuring CPU metrics reflect pure computational throughput.
    implementation(libs.moshi)
    ksp(libs.moshi.kotlin.codegen)

    // [IMAGE PROCESSING PIPELINE]
    // Utilizing Coil 3 to establish a standardized, hardware-accelerated image decoding
    // baseline for cross-platform parity tests.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // [LIFECYCLE & TELEMETRY INFRASTRUCTURE]
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.activity.compose)

    // [GEOSPATIAL RENDERING: GPU STRESS TEST]
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)

    // [MEDIA DECODING PIPELINE: HARDWARE ACCELERATION TEST]
    // Native Media3/ExoPlayer implementation to benchmark device-specific hardware decoders.
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    // [CORE FRAMEWORKS]
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // [AUTOMATED INSTRUMENTATION & BENCHMARKING]
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // [CONCURRENCY & STATE MANAGEMENT]
    // Atomic operations and Coroutines for thread-safe telemetry aggregation.
    implementation(libs.kotlinx.atomicfu)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
}