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

// Live tests against the real YouTube (ADR-0007 canary). A source set of their own, so the unit
// test run (`test`, `testDebugUnitTest`, `check`) never compiles or runs them; CLAUDE.md rule 6.
// They also skip themselves unless BACKROW_LIVE_TESTS=1. Run with:
//   BACKROW_LIVE_TESTS=1 ./gradlew :core:innertube:liveTest
// They solve challenges with `node` (on the PATH, or BACKROW_NODE) instead of QuickJS.
val liveTest: SourceSet = sourceSets.create("liveTest")
// Same-module access to internal classes (InnertubeHttp, ChallengeSolver, ...).
kotlin.target.compilations.getByName("liveTest").associateWith(kotlin.target.compilations.getByName("main"))
configurations.named("liveTestImplementation") { extendsFrom(configurations.implementation.get()) }

dependencies {
    "liveTestImplementation"(libs.junit)
}

tasks.register<Test>("liveTest") {
    group = "verification"
    description = "Runs the live YouTube canary tests (skipped unless BACKROW_LIVE_TESTS=1)."
    testClassesDirs = liveTest.output.classesDirs
    classpath = liveTest.runtimeClasspath
    useJUnit()
    // The result depends on YouTube today, not on the inputs: never up to date, never cached.
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT
        showStandardStreams = true
    }
}

// Android modules call their unit tests "testDebugUnitTest". This alias makes the documented
// `./gradlew testDebugUnitTest` (CLAUDE.md, CI) run this module's tests too.
tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Runs this JVM module's unit tests (alias of test)."
    dependsOn(tasks.named("test"))
}
