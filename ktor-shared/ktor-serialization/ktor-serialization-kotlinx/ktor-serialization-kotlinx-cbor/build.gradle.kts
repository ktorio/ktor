/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Ktor CBOR Content Negotiation via kotlinx.serialization support"

plugins {
    id("ktorbuild.project.library")
    id("kotlinx-serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":ktor-shared:ktor-serialization:ktor-serialization-kotlinx"))
            api(libs.kotlinx.serialization.cbor)
        }
        commonTest.dependencies {
            api(libs.kotlinx.serialization.json)
            api(project(":ktor-shared:ktor-serialization:ktor-serialization-kotlinx:ktor-serialization-kotlinx-tests"))
        }
    }
}
