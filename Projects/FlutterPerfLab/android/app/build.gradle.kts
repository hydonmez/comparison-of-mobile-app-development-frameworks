import java.util.Properties

// Securely resolves the Maps API Key from local.properties to prevent key exposure.
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.reader(Charsets.UTF_8).use { reader ->
        localProperties.load(reader)
    }
}

plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    // Application namespace.
    namespace = "com.tez.flutterperflab"
    
    // Locked to SDK 35 for modern hardware telemetry APIs.
    compileSdk = 36
    
    ndkVersion = flutter.ndkVersion

    compileOptions {
        // Enforces Java 17 bytecode generation.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    signingConfigs {
        create("release") {
            // Standardized key for reproducible release builds.
            storeFile = file("my-release-key.jks")
            storePassword = "123456"
            keyAlias = "key0"
            keyPassword = "123456"
        }
    }

    defaultConfig {
        applicationId = "com.tez.flutterperflab"
        
        // Setting minSdk 33 ensures reliance on the modern ART garbage collector (GC).
        minSdk = 33
        targetSdk = 35
        
        versionCode = flutter.versionCode
        versionName = flutter.versionName
        
        // Google Maps API Key Injection
        manifestPlaceholders += mapOf(
            "mapsApiKey" to localProperties.getProperty("MAPS_API_KEY", "")
        )
    }

    buildTypes {
        getByName("release") {
            // Enforces aggressive R8 minification for optimal memory footprint.
            isMinifyEnabled = true
            isShrinkResources = true
            
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Enforces the standard release signing config.
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

flutter {
    source = "../.."
}