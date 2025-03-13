/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.library")
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            compileOnly(libs.slf4j.simple)
            api(libs.kotlinx.coroutines.slf4j)
        }
        commonTest.dependencies {
            api(project(":ktor-client:ktor-client-mock"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-content-negotiation"))
        }
        jvmTest.dependencies {
            api(project(":ktor-shared:ktor-serialization:ktor-serialization-jackson"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-encoding"))
        }
    }
}
