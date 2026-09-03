/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest

description = ""

plugins {
    id("ktorbuild.project.internal")
}

kotlin {
    sourceSets {
        jvmTest.dependencies {
            implementation(projects.ktorServerTestBase)
            implementation(projects.ktorServerTestSuites)
            implementation(projects.ktorServerCore)
            implementation(projects.ktorServerNetty)

            implementation(libs.netty.tcnative)
            implementation(libs.netty.tcnative.boringssl.static)
        }
    }
}

// Runs the shared engine suites over HTTP/3 only: the HTTP/1.1 and HTTP/2 legs of the same suites
// already run in :ktor-server-netty, and re-running them here would only duplicate coverage.
tasks.named<KotlinJvmTest>("jvmTest") {
    systemProperty("enable.http3", "true")
    systemProperty("enable.http3.only", "true")
}
