// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dk.backrow.core.player"
    compileSdk {
        version =
            release(libs.versions.android.compileSdk.get().toInt()) {
                minorApiLevel = libs.versions.android.compileSdkMinor.get().toInt()
            }
    }

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    api(project(":core:model"))
    api(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    testImplementation(libs.junit)
}
