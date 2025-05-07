import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

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
    implementation(libs.ktor.server.default.headers)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.jetty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.sse)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.caching.headers)
    implementation(libs.ktor.server.conditional.headers)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.kotlinx)
    implementation(libs.ktor.network.tls.certificates)
    implementation(libs.ktor.utils)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logback.classic)
    implementation(libs.tomlj)
}

// Should be synced with gradle/gradle-daemon-jvm.properties
kotlin {
    jvmToolchain(21)

    compilerOptions {
        languageVersion = rootProject.findProperty("kotlin_language_version")
            ?.let { KotlinVersion.fromVersion(it.toString()) }
            ?: KotlinVersion.KOTLIN_2_1

        println("Using languageVersion :ktor-test-server: ${languageVersion.get()}")

        apiVersion = rootProject.findProperty("kotlin_api_version")
            ?.let { KotlinVersion.fromVersion(it.toString()) }
            ?: KotlinVersion.KOTLIN_2_1

        println("Using apiVersion :ktor-test-server: ${apiVersion.get()}")

        allWarningsAsErrors = true
        // A workaround for a compiler issue KT-74984
        // TODO: Remove after the issue is fixed
        freeCompilerArgs.add("-Xignore-const-optimization-errors")
    }
}
