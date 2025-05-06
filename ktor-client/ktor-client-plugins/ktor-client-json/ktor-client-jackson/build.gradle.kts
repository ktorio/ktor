/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.client-plugin")
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            api(project(":ktor-client-json"))

            api(libs.jackson.databind)
            api(libs.jackson.module.kotlin)
        }
        jvmTest.dependencies {
            api(project(":ktor-client-cio"))
            api(project(":ktor-serialization-gson"))
        }
    }
}
