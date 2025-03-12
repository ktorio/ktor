/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.library")
}

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                api(project(":ktor-client:ktor-client-plugins:ktor-client-json"))
                api(libs.gson)
            }
        }
        jvmTest {
            dependencies {
                api(project(":ktor-client:ktor-client-cio"))
                api(project(":ktor-shared:ktor-serialization:ktor-serialization-gson"))
            }
        }
    }
}
