/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "CIO backend for ktor http client"

plugins {
    id("ktorbuild.project.library")
    id("test-server")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":ktor-client-core"))
            api(project(":ktor-http-cio"))
            api(project(":ktor-websockets"))
            api(project(":ktor-network-tls"))
        }
        commonTest.dependencies {
            api(project(":ktor-client-tests"))
        }
        jvmTest.dependencies {
            api(project(":ktor-network-tls-certificates"))
            api(project(":ktor-test-base"))
            implementation(libs.mockk)
        }
    }
}
