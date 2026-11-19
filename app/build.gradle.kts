// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.licensee)
}

android {
    namespace = "dk.backrow"
    compileSdk {
        version =
            release(libs.versions.android.compileSdk.get().toInt()) {
                minorApiLevel = libs.versions.android.compileSdkMinor.get().toInt()
            }
    }

    defaultConfig {
        // Fixed forever (ADR-0001).
        applicationId = "dk.backrow"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Debug and release can be installed side by side on one TV.
            applicationIdSuffix = ".debug"
        }
        release {
            // R8 full mode is the AGP default; shrinking matters on 1 GB devices (ADR-0004).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(21)
}

// Dependency license gate (ADR-0002, CLAUDE.md rule 1). Applied here because this module
// produces the APK; it walks the full transitive graph, including every :core module.
licensee {
    allow("Apache-2.0")
    allow("MIT")
    allow("BSD-2-Clause")
    allow("BSD-3-Clause")
    allow("ISC")
    allow("Unlicense")

    // GPL-2.0 with Classpath Exception. The only exception to the list above; pinned to one
    // version so every bump is reviewed. See docs/adr/0011-desugar-jdk-libs-license-exception.md.
    allowDependency("com.android.tools", "desugar_jdk_libs", libs.versions.desugar.jdk.libs.get()) {
        because("GPL-2.0 with Classpath Exception, allowed for this artifact only by ADR-0011")
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:innertube"))
    implementation(project(":core:player"))
    implementation(project(":core:sponsorblock"))
    implementation(project(":core:data"))

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.core)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
