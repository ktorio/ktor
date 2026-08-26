/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.kotlin.serialization)
    implementation(libs.kotlinx.atomicfu.gradlePlugin)
    implementation(libs.dokka.gradlePlugin)
    implementation(libs.develocity)
    implementation(libs.gradleDoctor)
    implementation(libs.kotlinter)
    implementation(libs.mavenPublishing)
    implementation(libs.android.gradlePlugin)

    // A hack to make version catalogs accessible from buildSrc sources
    // https://github.com/gradle/gradle/issues/15383#issuecomment-779893192
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}

// The flaky-test selectors are driven by one property with one set of values, read here to
// configure the test tasks and in `ktor-test-base` to decide whether a `@Flaky` test runs. Compiling
// `ktor-test-constants` into build-logic keeps that a single declaration instead of two that have to
// be kept in sync by hand.
//
// The module is added as a source directory rather than as a project dependency because it is a
// Kotlin Multiplatform project built by the `ktorbuild.project.internal` convention plugin — which
// this build produces, so including it here would be circular.
sourceSets.main {
    kotlin.srcDir("../ktor-shared/ktor-test-constants/common/src")
}

// Should be synced with gradle/gradle-daemon-jvm.properties
kotlin {
    jvmToolchain(21)

    compilerOptions {
        allWarningsAsErrors = true
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

tasks.validatePlugins {
    enableStricterValidation = true
}
