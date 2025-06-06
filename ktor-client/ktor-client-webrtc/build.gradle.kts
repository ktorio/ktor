/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Ktor WebRtc client"

plugins {
    id("kotlinx-serialization")
    id("ktorbuild.project.library")
}

kotlin {
    jvmToolchain(17)

    sourceSets {
        commonMain.dependencies {
            api(project(":ktor-io"))
            api(libs.kotlinx.serialization.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(project(":ktor-test-dispatcher"))
        }
    }
}
