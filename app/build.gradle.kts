// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

// Release versions come only from the tag, which release.yml passes as
// -Pbackrow.version=1.4.0 or 1.4.0-beta.2 (docs/RELEASE-PROCESS.md §2.1). Local and CI builds
// without it are development builds; a release build without it fails (see preReleaseBuild below).
val releaseVersion: String? = providers.gradleProperty("backrow.version").orNull

/**
 * major·1 000 000 + minor·10 000 + patch·100 + n, where n is the beta number (1–98) and 99 for
 * stable: codes only go up, and a stable release always updates the betas before it.
 */
fun versionCodeOf(version: String): Int {
    val match =
        Regex("""(\d+)\.(\d+)\.(\d+)(?:-beta\.(\d+))?""").matchEntire(version)
            ?: throw GradleException("backrow.version '$version' is not X.Y.Z or X.Y.Z-beta.N")
    val (major, minor, patch, beta) = match.destructured
    val n = if (beta.isEmpty()) 99 else beta.toInt()
    // 99 is reserved for stable, so beta numbers stop at 98.
    if (minor.toInt() > 99 || patch.toInt() > 99 || n !in 1..99 || (beta.isNotEmpty() && n > 98) || major.toInt() > 2000) {
        throw GradleException("backrow.version '$version' is out of range for the versionCode scheme")
    }
    return major.toInt() * 1_000_000 + minor.toInt() * 10_000 + patch.toInt() * 100 + n
}

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
        versionCode = releaseVersion?.let(::versionCodeOf) ?: 1
        versionName = releaseVersion ?: "0.1.0-dev"
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

    // AGP otherwise embeds a dependency-metadata block encrypted with a Google key. Nobody but
    // Google can read it, and F-Droid rejects APKs that carry it (reproducible builds, ADR-0007).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

// Release APKs are built only by release.yml from a tag (ADR-0007): refuse to build one without
// the tag's version, so nobody produces a release-looking APK on a laptop by accident.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    // A local, so the task action captures a Boolean, not the script (configuration cache).
    val missing = releaseVersion == null
    doFirst {
        if (missing) {
            throw GradleException("Release builds need -Pbackrow.version=X.Y.Z[-beta.N]; they are made by release.yml only.")
        }
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
    implementation(project(":core:jsengine"))
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
    implementation(platform(libs.kotlinx.coroutines.bom))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.media3.ui.compose)

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
