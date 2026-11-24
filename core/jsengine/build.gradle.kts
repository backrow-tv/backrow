// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

// JavaScript engine for :core:innertube's challenge solver (ADR-0009). Android library because
// QuickJS is native code. Knows nothing about YouTube: it runs the scripts it is handed.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dk.backrow.core.jsengine"
    compileSdk {
        version =
            release(libs.versions.android.compileSdk.get().toInt()) {
                minorApiLevel = libs.versions.android.compileSdkMinor.get().toInt()
            }
    }

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    api(project(":core:innertube"))
    implementation(project(":core:model"))
    implementation(libs.quickjs.kt.android)
    implementation(platform(libs.kotlinx.coroutines.bom))
    implementation(libs.kotlinx.coroutines.core)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.kotlinx.coroutines.bom))
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
