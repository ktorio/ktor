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
            api(project(":ktor-serialization"))
            api(libs.jackson.databind)
            api(libs.jackson.module.kotlin)
        }
        jvmTest.dependencies {
            api(project(":ktor-server-test-host"))
            api(project(":ktor-client-tests"))
            api(project(":ktor-client-content-negotiation-tests"))
            api(project(":ktor-serialization-tests"))

            api(libs.logback.classic)
            api(libs.jackson.dataformat.smile)
        }
    }
}
