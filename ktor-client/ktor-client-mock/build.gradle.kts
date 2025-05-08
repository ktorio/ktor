/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.library")
    id("kotlinx-serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":ktor-http"))
            api(project(":ktor-client-core"))
        }

        commonTest.dependencies {
            api(project(":ktor-test-dispatcher"))
        }

        jvmTest.dependencies {
            api(libs.kotlinx.serialization.core)
            api(project(":ktor-client-content-negotiation"))
            api(project(":ktor-serialization-kotlinx"))
            api(project(":ktor-serialization-kotlinx-json"))
        }
    }
}
