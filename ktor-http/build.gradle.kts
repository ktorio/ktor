/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.library")
    id("kotlinx-serialization")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":ktor-utils"))
                api(libs.kotlinx.serialization.core)
            }
        }
        jvmTest {
            dependencies {
                implementation(project(":ktor-shared:ktor-test-base"))
                implementation(project(":ktor-shared:ktor-serialization:ktor-serialization-kotlinx"))
                implementation(project(":ktor-shared:ktor-serialization:ktor-serialization-kotlinx:ktor-serialization-kotlinx-json"))
            }
        }
    }
}
