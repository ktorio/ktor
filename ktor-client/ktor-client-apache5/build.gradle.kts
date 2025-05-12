/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Apache backend for ktor http client"

plugins {
    id("ktorbuild.project.library")
    id("test-server")
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            api(projects.ktorClientCore)
            api(libs.apache.client5)
        }
        jvmTest.dependencies {
            api(projects.ktorClientTests)
        }
    }
}
