# Dual-Platform Portability Guide: Running on iOS with Kotlin & Compose Multiplatform (KMP)

By refactoring our UI layers to use the **Material 3 Snackbar Host** and pure Kotlin state callbacks, we have compiled a 100% portable Jetpack Compose view structure. The exact same composables (`TrackerDashboard`, `ProfileSettingsScreen`, and diagnostic charts) can be run on **iOS** with zero code duplication using **Compose Multiplatform**.

Below is the definitive multiplatform roadmap, file configurations, and bridging templates to build your app on iOS.

---

## 📂 1. Multiplatform Project Structure

In a unified multiplatform project, your files are organized into a core shared module and platform-specific entry points:

```
├── clean-water-app/
│   ├── shared/                        # Core Common Module (Shared code)
│   │   ├── build.gradle.kts           # Multiplatform dependencies (Android + iOS targets)
│   │   └── src/
│   │       ├── commonMain/            # Both iOS and Android compile from here
│   │       │   └── kotlin/
│   │       │       ├── MainActivity.kt # All beautiful composables go here!
│   │       │       └── ViewModel.kt   # Core state machine logic
│   │       ├── androidMain/           # Android-specific extensions (Haptic feedback, notifications)
│   │       └── iosMain/               # iOS-specific integrations
│   │
│   ├── iosApp/                        # Native iOS Project (Xcode project)
│   │   ├── iosApp/
│   │       ├── App.swift              # Swift Entry Point
│   │       └── ContentView.swift      # Renders Compose UI via UIViewControllerRepresentable
│   │
│   └── androidApp/                    # Native Android Project launcher
│       └── src/main/AndroidManifest.xml
```

---

## ⚙️ 2. Dual-Platform Gradle Configuration (`shared/build.gradle.kts`)

Your shared module needs both the Android target and iOS targets. Paste this configuration into your shared build setup:

```kotlin
plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    
    // Configures the iOS compilation target for both ARM64 devices and simulators
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SharedFramework"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                
                // Kotlin Multiplatform Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
                // Core serialization for structured offline profiles
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
            }
        }
        val androidMain by getting {
            dependencies {
                api("androidx.activity:activity-compose:1.9.0")
            }
        }
        val iosMain by getting {
            dependencies {
                // iOS-specific implementations
            }
        }
    }
}

android {
    namespace = "com.example.shared"
    compileSdk = 34
    defaultConfig {
        minSdk = 24
    }
}
```

---

## 🍎 3. Swift iOS Bootstrapping Launcher

Swift-exclusive views can present our Compose-based UI without configuration friction. Simply create these files inside your native Xcode directory (`/iosApp`):

### A. The UIKit Bridge (`shared/src/iosMain/.../MainViewController.kt`)
This Kotlin file converts your Compose Multiplatform window into an iOS `UIViewController`:

```kotlin
package com.example.shared

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    // Launches your common WaterTrackerUI screen directly into iOS!
    WaterTrackerApp() 
}
```

### B. The SwiftUI Wrapper View (`ContentView.swift` in iOSApp)
Use SwiftUI’s `UIViewControllerRepresentable` block to load the bridge controller:

```swift
import SwiftUI
import SharedFramework // Built by your common multiplatform target

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        return MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard, edges: .bottom) // Prevents keyboard layout overlap
    }
}
```

### C. The Swift Main Launcher App (`iosApp.swift` in iOSApp)
```swift
import SwiftUI

@main
struct iosApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

---

## ⚡ 4. Key Cross-Platform Portation Wins

1. **Pure Compose UI**: Our custom animations, dynamic fluid Wave canvas, physical metrics sliders, and custom presets work out of the box on both platforms, maintaining identical layout physics and color schemas.
2. **Snackbar Replacement**: By substituting standard Android Toasts with `SnackbarHostState.showSnackbar`, the floating action status alerts run seamlessly on Apple's graphic system.
3. **Common State Management**: The data structures `WaterLog` and `WaterProfile` are now structured to be fully JSON serializable, allowing painless common logic sync to both iOS CoreData / local defaults and Android Room.
