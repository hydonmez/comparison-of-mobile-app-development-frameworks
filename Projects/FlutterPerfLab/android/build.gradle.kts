// Root build configuration for dependency resolution and build directory management.

allprojects {
    repositories {
        google()
        mavenCentral()
        
        // Adds JetBrains repositories to resolve dependencies for native plugins seamlessly.
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }

    // Forcefully downgrade specific core dependencies to maintain compatibility 
    // with AGP 8.8.2 and prevent compilation failures.
    // Also enforces a global version override for Kotlin modules to prevent 'Incompatible classes' errors.
    configurations.all {
        resolutionStrategy {
            // Library-specific version overrides
            force("com.google.maps.android:android-maps-utils:4.0.0")
            force("androidx.core:core:1.16.0")
            force("androidx.core:core-ktx:1.16.0")
            
            // Global Kotlin version override
            eachDependency {
                if (requested.group == "org.jetbrains.kotlin") {
                    useVersion("2.1.20")
                }
            }
        }
    }
}

// Isolates build outputs to the root Flutter workspace to prevent artifact pollution.
val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}

subprojects {
    project.evaluationDependsOn(":app")
}

// Clean build workspace
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}