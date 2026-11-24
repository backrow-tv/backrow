// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

// Plugins are declared here once, with versions from the catalog, and applied per module.
// Declaring kotlin-jvm here also puts our Kotlin Gradle plugin version on the build
// classpath, which AGP's built-in Kotlin support then uses for Android modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.licensee) apply false
}

// Reproducible builds (ADR-0007, docs/RELEASE-PROCESS.md §3.3): archives get fixed timestamps
// and a stable file order, so the same tag builds the same bytes on any machine.
subprojects {
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}
