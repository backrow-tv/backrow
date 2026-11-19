// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin/JVM module: no Android SDK on the classpath (CLAUDE.md module rules).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Compiled by JDK 21 (the project toolchain) to Java 17 bytecode, the same level the
// Android modules use, so D8 never sees a newer class-file version than it expects.
kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}
tasks.withType<JavaCompile>().configureEach { options.release.set(17) }

dependencies {
    implementation(project(":core:model"))
    testImplementation(libs.junit)
}

// The fixture sanitisation test (CLAUDE.md rule 6) lives here and scans every module's
// src/test/resources. It gets the repository root as a system property, and the fixture
// files are declared as inputs so the test re-runs whenever any fixture changes.
val repositoryRoot: File = rootDir
tasks.withType<Test>().configureEach {
    systemProperty("backrow.rootDir", repositoryRoot.absolutePath)
    inputs.files(
        fileTree(repositoryRoot) {
            include("**/src/test/resources/**")
            exclude("**/build/**", "reference/**", ".gradle/**")
        },
    ).withPropertyName("fixtures").withPathSensitivity(PathSensitivity.RELATIVE)
}

// Android modules call their unit tests "testDebugUnitTest". This alias makes the documented
// `./gradlew testDebugUnitTest` (CLAUDE.md, CI) run this module's tests too.
tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Runs this JVM module's unit tests (alias of test)."
    dependsOn(tasks.named("test"))
}
