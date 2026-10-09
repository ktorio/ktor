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

// Should be synced with gradle/gradle-daemon-jvm.properties
kotlin {
    jvmToolchain(21)

    compilerOptions {
        allWarningsAsErrors = true
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

// Sharing Public Suffix List parsing with ktor-client-core
val syncPublicSuffixSources = tasks.register<Sync>("syncPublicSuffixSources") {
    val cookiesLocation = "io/ktor/client/plugins/cookies"
    from(layout.projectDirectory.dir("../ktor-client/ktor-client-core/common/src/$cookiesLocation")) {
        include("Punycode.kt", "DomainCanonicalization.kt")
    }
    from(layout.projectDirectory.dir("../ktor-client/ktor-client-core/jvm/src/$cookiesLocation")) {
        include("PublicSuffixListParser.kt")
    }
    into(layout.buildDirectory.dir("generated/publicSuffix"))
}

sourceSets.main {
    kotlin.srcDir(syncPublicSuffixSources)
}

tasks.validatePlugins {
    enableStricterValidation = true
}
