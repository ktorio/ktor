/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

plugins {
    id("ktorbuild.project.library")
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            api(project(":ktor-shared:ktor-serialization"))
            api(libs.gson)
        }
        jvmTest.dependencies {
            api(project(":ktor-server:ktor-server-test-host"))
            api(project(":ktor-client:ktor-client-tests"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-content-negotiation:ktor-client-content-negotiation-tests"))
            api(project(":ktor-shared:ktor-serialization:ktor-serialization-tests"))

            api(libs.logback.classic)
        }
    }
}
