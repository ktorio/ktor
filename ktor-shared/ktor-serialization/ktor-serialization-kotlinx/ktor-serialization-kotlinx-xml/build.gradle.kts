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
            api(project(":ktor-serialization-kotlinx"))
            api(libs.xmlutil.serialization)
        }
        commonTest.dependencies {
            implementation(project(":ktor-serialization-kotlinx-tests"))
        }
        jvmTest.dependencies {
            implementation(project(":ktor-client-content-negotiation-tests"))
        }
    }
}
