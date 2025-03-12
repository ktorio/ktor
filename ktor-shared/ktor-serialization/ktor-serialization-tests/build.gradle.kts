/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.internal")
    id("kotlinx-serialization")
}

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                api(project(":ktor-server:ktor-server-test-host"))
                api(project(":ktor-client:ktor-client-plugins:ktor-client-content-negotiation:ktor-client-content-negotiation-tests")) // ktlint-disable max-line-length

                api(libs.logback.classic)
            }
        }
    }
}
