/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

plugins {
    id("ktorbuild.project.server-plugin")
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            implementation(libs.jansi)
            implementation(libs.kotlinx.coroutines.slf4j)
        }

        jvmTest.dependencies {
            implementation(projects.ktorServerCallId)
            implementation(projects.ktorServerStatusPages)
        }
    }
}
