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
            implementation(projects.ktorTestBase)
            implementation(projects.ktorNetworkTlsCertificates)
            implementation(libs.netty.handler)
            implementation(libs.mockk)
        }
    }
}
