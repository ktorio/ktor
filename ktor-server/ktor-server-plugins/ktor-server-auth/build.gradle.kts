/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

plugins {
    id("ktorbuild.project.server-plugin")
    id("kotlinx-serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":ktor-client:ktor-client-core"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-sessions"))
            api(libs.kotlinx.serialization.json)
        }
        jvmTest.dependencies {
            api(project(":ktor-server:ktor-server-plugins:ktor-server-content-negotiation"))
            api(project(":ktor-shared:ktor-serialization:ktor-serialization-jackson"))
        }
    }
}
