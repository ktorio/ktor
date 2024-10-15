/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.gradle.kotlin.dsl.module
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest
import kotlin

plugins {
    kotlin("plugin.serialization")
}

description = ""

val jetty_alpn_api_version: String by extra

val enableAlpnProp = project.hasProperty("enableAlpn")
val osName = System.getProperty("os.name").lowercase()
val nativeClassifier: String? = if (enableAlpnProp) {
    when {
        osName.contains("win") -> "windows-x86_64"
        osName.contains("linux") -> "linux-x86_64"
        osName.contains("mac") -> "osx-x86_64"
        else -> throw InvalidUserDataException("Unsupported os family $osName")
    }
} else {
    null
}

kotlin.sourceSets {
    jvmMain {
        dependencies {
            api(project(":ktor-server:ktor-server-core"))

            api(libs.netty.codec.http2)
            api(libs.jetty.alpn.api)

            api(libs.netty.transport.native.kqueue)
            api(libs.netty.transport.native.epoll)
            if (nativeClassifier != null) {
                api(libs.netty.tcnative.boringssl.static)
            }
        }
    }
    jvmTest {
        dependencies {
            api(project(":ktor-server:ktor-server-test-base"))
            api(project(":ktor-server:ktor-server-test-suites"))
            api(project(":ktor-server:ktor-server-core"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-default-headers"))

            api(libs.netty.tcnative)
            api(libs.netty.tcnative.boringssl.static)
            api(libs.mockk)
            api(libs.logback.classic)

            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            api(libs.jackson.databind)
            api(libs.jackson.module.kotlin)
            api(libs.netty.transport.native.epoll)
            api(libs.netty.tcnative)
            api(libs.netty.tcnative.boringssl.static)
            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
        }
    }
}

tasks.named<KotlinJvmTest>("jvmTest") {
    systemProperty("enable.http2", "true")
}
