/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Data classes for OpenAPI serialization"

plugins {
    id("ktorbuild.project.library")
    id("kotlinx-serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.ktorIo)
            implementation(projects.ktorHttp)
            implementation(libs.kotlin.serialization)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
