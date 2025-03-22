/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

plugins {
    id("ktorbuild.project.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":ktor-http"))
            api(project(":ktor-io"))
        }

        jvmMain.dependencies {
            api(project(":ktor-network"))
        }

        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
