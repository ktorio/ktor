/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Ktor JSON Content Negotiation via kotlinx.serialization support"

plugins {
    id("ktorbuild.project.library")
    id("kotlinx-serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.ktorSerializationKotlinx)
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.serialization.json.io)
        }
        jvmTest.dependencies {
            implementation(projects.ktorClientContentNegotiationTests)
        }
        commonTest.dependencies {
            implementation(projects.ktorSerializationKotlinxTests)
        }
    }
}
