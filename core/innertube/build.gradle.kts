// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin/JVM module: no Android SDK on the classpath (CLAUDE.md module rules).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Compiled by JDK 21 (the project toolchain) to Java 17 bytecode, the same level the
// Android modules use, so D8 never sees a newer class-file version than it expects.
kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}
tasks.withType<JavaCompile>().configureEach { options.release.set(17) }

dependencies {
    api(project(":core:model"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.kotlinx.coroutines.bom))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Android modules call their unit tests "testDebugUnitTest". This alias makes the documented
// `./gradlew testDebugUnitTest` (CLAUDE.md, CI) run this module's tests too.
tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Runs this JVM module's unit tests (alias of test)."
    dependsOn(tasks.named("test"))
}
