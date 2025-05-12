/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.ktorHttp)
            api(projects.ktorNetwork)
            api(projects.ktorUtils)
        }
        jvmTest.dependencies {
            api(projects.ktorTestBase)
            api(projects.ktorNetworkTlsCertificates)
            api(libs.netty.handler)
            api(libs.mockk)
        }
    }
}
