pluginManagement {
    val flutterSdkPath =
        run {
            val properties = java.util.Properties()
            file("local.properties").inputStream().use { properties.load(it) }
            val flutterSdkPath = properties.getProperty("flutter.sdk")
            require(flutterSdkPath != null) { "flutter.sdk not set in local.properties" }
            flutterSdkPath
        }

    // Flutter toolchain injection
    includeBuild("$flutterSdkPath/packages/flutter_tools/gradle")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Links the Dart execution engine with the Android build lifecycle.
    id("dev.flutter.flutter-plugin-loader") version "1.0.0"

    // Strictly enforcing AGP 8.8.2 to guarantee consistent minification and packaging processes.
    id("com.android.application") version "8.8.2" apply false

    // Locked to Kotlin 2.1.20 to maintain consistent compiler environments.
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false

    // Ensures reproducible builds on Java 17 and eliminates JDK-induced anomalies.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

include(":app")