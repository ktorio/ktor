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

    // Needed for patches/DokkaVersioningPluginParameters
    // TODO: Remove when the PR fixing this file will be merged and released. Probably in Dokka 2.2.0
    //   PR: https://github.com/Kotlin/dokka/pull/4301
    implementation(libs.kotlinx.serialization.json)

    // A hack to make version catalogs accessible from buildSrc sources
    // https://github.com/gradle/gradle/issues/15383#issuecomment-779893192
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}

// Should be synced with gradle/gradle-daemon-jvm.properties
kotlin {
    jvmToolchain(21)

    compilerOptions {
        allWarningsAsErrors = true
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}
