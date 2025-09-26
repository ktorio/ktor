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
            api(projects.ktorClientCore)
            api(projects.ktorHttpCio)
            api(projects.ktorWebsockets)
            api(projects.ktorNetworkTls)
        }
        commonTest.dependencies {
            api(projects.ktorClientTests)
        }
        jvmTest.dependencies {
            api(projects.ktorNetworkTlsCertificates)
            api(projects.ktorTestBase)
            implementation(libs.mockk)
        }
    }
}
