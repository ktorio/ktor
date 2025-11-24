/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    `kotlin-dsl`
    // Serialization version should be aligned with the Kotlin version embedded in Gradle
    // https://docs.gradle.org/current/userguide/compatibility.html#kotlin
    kotlin("plugin.serialization") version embeddedKotlinVersion
}

dependencies {
    implementation(ktorLibs.server.defaultHeaders)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.cio)
    implementation(ktorLibs.server.jetty)
    implementation(ktorLibs.server.websockets)
    implementation(ktorLibs.server.sse)
    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.cachingHeaders)
    implementation(ktorLibs.server.conditionalHeaders)
    implementation(ktorLibs.server.compression)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.statusPages)
    implementation(ktorLibs.serialization.kotlinx)
    implementation(ktorLibs.network.tls.certificates)
    implementation(ktorLibs.utils)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logback.classic)
    implementation(libs.tomlj)
}

// Should be synced with gradle/gradle-daemon-jvm.properties
kotlin {
    jvmToolchain(21)

    compilerOptions {
        allWarningsAsErrors = true
        // A workaround for a compiler issue KT-74984
        // TODO: Remove after the issue is fixed
        freeCompilerArgs.add("-Xignore-const-optimization-errors")
    }
}
