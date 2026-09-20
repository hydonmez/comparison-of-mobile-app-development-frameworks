
\# Comparison-of-mobile-app-development-frameworks 



![Native Main Screenshot](Screenshots/Native_Main.jpeg)


A comprehensive comparative analysis framework designed to evaluate and determine the most efficient development approach for mobile software projects across Native and Cross-Platform technologies.



This project explores the performance, development processes, and hardware utilization differences between Native (Kotlin, Swift) and Cross-Platform (Kotlin Multiplatform, Flutter, React Native) frameworks based on real-world scenarios.



\---



\## Features



\- Multi-Framework Implementation

&#x20; Identical test scenarios developed across five different mobile development architectures Native Android (Kotlin), Native iOS (Swift), Kotlin Multiplatform (KMP), Flutter, and React Native.

\- Real-World Test Scenarios

&#x20; Includes functionalities commonly used in modern mobile applications video and audio playback, map and navigation operations, file readwrite operations, JSON data parsing, list structures, and hardware sensor access.

\- Comprehensive Performance Evaluation

&#x20; Enables precise comparison based on critical metrics

&#x20; - Processor (CPU) utilization

&#x20; - Net and raw RAM consumption

&#x20; - Frames per second (FPS)

&#x20; - Time-based metrics (average completion times for file IO and JSON deserialization)

\- Research-Oriented Structure

&#x20; Provides raw telemetry logs and detailed statistical summary sheets (Excel) for academic and analytical reviews.



\---



\## Concept



In mobile software development processes, the accurate analysis of requirements and the selection of an appropriate architecture are of critical importance in terms of project duration, cost, performance, and maintainability. Today, the mobile application development ecosystem is primarily shaped around the iOS and Android platforms, and developers prefer either native or cross-platform approaches depending on project requirements. Each approach has different advantages and limitations in terms of performance, development processes, maintenance costs, and user experience. 



In this study, a comprehensive comparative analysis was conducted to determine the most efficient development approach for mobile software projects. Based on the findings, evaluations were made regarding which architectural approach is more suitable for different project requirements. The study aims to serve as a guiding reference for mobile software developers during the selection process.



\---



\## Technologies



The project is built using the leading mobile development frameworks and data analysis tools



\- Native Android Kotlin

\- Native iOS Swift, SwiftUI

\- Cross-Platform Kotlin Multiplatform (KMP), Flutter, React Native

\- Data Analysis Python, Pandas, Matplotlib, Microsoft Excel



\---



\## Project Structure



The repository is organized into four main directories to separate assets, raw dataresults, source codes, and visual documentation.



```text

NativeAndCrossPlatformPerformanceLab

│

├── Assets                 # Contains the RAR archive with JSONJPG files required for the tests

├── Data and Results       # Detailed and summarized Excel benchmark results for Android \& iOS

├── Projects               # Source codes for all 5 mobile frameworks

│   ├── FlutterPerfLab

│   ├── KotlinMultiPlatformPerfLab

│   ├── NativeKotlinPerfLab

│   ├── ReactNativePerfLab

│   └── SwiftPerfLab

└── Screenshots            # Application interface examples

Configuration \& Asset Setup

To run the benchmark scenarios properly, you must configure the media assets and the Google Maps API Key.



1\. Media Assets Setup

Navigate to the Assets folder and extract the compressed RAR file. This archive contains the required .jpg images and .json files.



Video \& Audio Files Due to file size limits, the video and audio files are not included in the repository. You must manually findprovide a video file and an audio file suitable for testing.



Rename your video file to test\_video\_1080p.mp4 and your audio file to test\_audio\_high.mp3.



Copy all these files (the extracted JPGsJSONs + your MP4 + your MP3) and paste them into the exact assetresource paths for each project



Native Kotlin ProjectsNativeKotlinPerfLabappsrcmainassets



Swift ProjectsSwiftPerfLabResources



KMP ProjectsKotlinMultiPlatformPerfLabcomposeAppsrcandroidMainassets \& iosMainresources



Flutter ProjectsFlutterPerfLabassets



React Native ProjectsReactNativePerfLabsrcassets



2\. Google Maps API Key Setup

For the map rendering benchmarks to work on Android platforms, a Google Maps API Key is required.



Obtain an API Key from the Google Developer 



In the root directory of the Android-based projects (Native Kotlin, KMP, Flutter's android folder, React Native's android folder), check for a local.properties file. If it does not exist, create it manually.



Add the following line inside the local.properties file



Properties

MAPS\_API\_KEY=YOUR\_API\_KEY\_HERE

Usage (Running in Release Mode)

To ensure accurate performance profiling and metrics, all applications must be built and executed in Release Mode on physical devices.



1\. Flutter

Navigate to the Flutter project directory and run



Bash

flutter run --release

2\. React Native

Navigate to the React Native directory. Start the app in release mode depending on the platform



Bash

npx react-native run-android --mode release

\# or for iOS

npx react-native run-ios --mode Release

3\. Native Kotlin (Android) \& Kotlin Multiplatform (KMP)

Using Android Studio



Open the project (NativeKotlinPerfLab or KotlinMultiPlatformPerfLab).



Open the Build Variants tool window (usually on the bottom left).



Change the Active Build Variant for the app (or composeApp) module from debug to release.



Press Run to deploy it to your physical device.



4\. Native Swift (iOS)

Using Xcode



Open SwiftPerfLab.xcodeproj or .xcworkspace.



Go to Product  Scheme  Edit Scheme (or press Cmd + ).



Select Run from the left sidebar and change the Build Configuration to Release.



Press Cmd + R to build and run on your physical iPhone.



License

This project is licensed under the MIT License.

