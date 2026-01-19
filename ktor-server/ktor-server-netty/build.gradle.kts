/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest

description = ""

plugins {
    id("ktorbuild.project.library")
}

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

kotlin {
    sourceSets {
        jvmMain.dependencies {
            api(projects.ktorServerCore)

            api(libs.netty.codec.http2)
            api(libs.jetty.alpn.api)

            api(libs.netty.transport.native.kqueue)
            api(libs.netty.transport.native.epoll)
            if (nativeClassifier != null) {
                api(libs.netty.tcnative.boringssl.static)
            }
        }
        jvmTest.dependencies {
            implementation(projects.ktorServerTestBase)
            implementation(projects.ktorServerTestSuites)
            implementation(projects.ktorServerCore)

            implementation(libs.netty.tcnative)
            implementation(libs.netty.tcnative.boringssl.static)
            implementation(libs.mockk)
        }
    }
}

tasks.named<KotlinJvmTest>("jvmTest") {
    systemProperty("enable.http2", "true")
}
