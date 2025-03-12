/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Common code for Resources feature"

plugins {
    id("ktorbuild.project.library")
    id("kotlinx-serialization")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":ktor-http"))
                api(project(":ktor-utils"))
                api(libs.kotlinx.serialization.core)
            }
        }
    }
}
